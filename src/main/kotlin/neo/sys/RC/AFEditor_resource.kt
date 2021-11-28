package neo.sys.RC

neo.Renderer.Material.idMaterial
import neo.idlib.math.Vector.idVec3
import neo.CM.CollisionModel.contactType_t
import neo.CM.CollisionModel.contactInfo_t
import neo.TempDump.SERiAL
import neo.idlib.math.Matrix.idMat3
import neo.CM.CollisionModel.trace_s
import java.lang.UnsupportedOperationException
import neo.idlib.MapFile.idMapFile
import neo.idlib.Text.Str.idStr
import neo.idlib.geometry.TraceModel.idTraceModel
import neo.idlib.BV.Bounds.idBounds
import neo.idlib.containers.CInt
import neo.idlib.geometry.Winding.idFixedWinding
import neo.idlib.math.Rotation.idRotation
import neo.idlib.math.Vector.idVec6
import neo.idlib.MapFile.idMapEntity
import neo.CM.AbstractCollisionModel_local.cm_node_s
import neo.idlib.containers.CFloat
import neo.CM.AbstractCollisionModel_local.cm_brushRef_s
import neo.CM.AbstractCollisionModel_local.cm_polygonRef_s
import neo.CM.AbstractCollisionModel_local
import neo.CM.CollisionModel_load
import neo.idlib.MapFile.idMapPrimitive
import neo.idlib.MapFile.idMapPatch
import neo.idlib.MapFile.idMapBrush
import neo.framework.CVarSystem.idCVar
import neo.framework.CVarSystem
import neo.Renderer.Material
import neo.CM.CollisionModel_debug
import neo.framework.CmdSystem.idCmdSystem.ArgCompletion_String
import neo.idlib.math.Vector.idVec4
import neo.idlib.containers.HashIndex.idHashIndex
import neo.CM.AbstractCollisionModel_local.cm_windingList_s
import neo.CM.CollisionModel_local.idCollisionModelManagerLocal
import neo.CM.CollisionModel.idCollisionModelManager
import neo.CM.CollisionModel_local
import neo.CM.AbstractCollisionModel_local.cm_model_s
import neo.CM.AbstractCollisionModel_local.cm_procNode_s
import neo.framework.Common
import neo.CM.AbstractCollisionModel_local.cm_vertex_s
import neo.CM.AbstractCollisionModel_local.cm_edge_s
import neo.CM.AbstractCollisionModel_local.cm_polygon_s
import neo.idlib.geometry.TraceModel.traceModelVert_t
import neo.idlib.geometry.TraceModel.traceModelEdge_t
import neo.idlib.geometry.TraceModel.traceModelPoly_t
import neo.idlib.geometry.TraceModel.traceModel_t
import neo.idlib.math.Math_h
import neo.CM.AbstractCollisionModel_local.cm_trmPolygon_s
import neo.CM.AbstractCollisionModel_local.cm_trmEdge_s
import neo.CM.AbstractCollisionModel_local.cm_trmVertex_s
import neo.CM.CollisionModel
import neo.idlib.Lib
import neo.idlib.math.Angles.idAngles
import neo.idlib.Text.Str
import neo.idlib.math.Random.idRandom
import neo.idlib.Timer.idTimer
import java.util.Locale
import neo.framework.File_h.idFile
import neo.CM.CollisionModel_files
import neo.framework.FileSystem_h
import neo.idlib.math.Pluecker.idPluecker
import neo.CM.AbstractCollisionModel_local.cm_traceWork_s
import neo.CM.CollisionModel_translate
import java.util.Arrays
import neo.idlib.math.Plane
import neo.CM.CollisionModel_rotate
import neo.idlib.math.Plane.idPlane
import neo.idlib.math.Math_h.idMath
import neo.CM.AbstractCollisionModel_local.cm_brush_s
import neo.idlib.geometry.TraceModel
import neo.CM.CollisionModel_contents
import java.util.function.Supplier
import java.util.function.IntFunction
import neo.CM.AbstractCollisionModel_local.cm_polygonRefBlock_s
import neo.CM.AbstractCollisionModel_local.cm_brushRefBlock_s
import neo.CM.AbstractCollisionModel_local.cm_nodeBlock_s
import neo.Renderer.Material.cullType_t
import neo.idlib.Text.Lexer.idLexer
import neo.idlib.Text.Token.idToken
import neo.Renderer.RenderWorld
import neo.idlib.geometry.Winding
import neo.framework.DeclManager
import neo.idlib.geometry.Surface_Patch.idSurface_Patch
import neo.idlib.MapFile
import neo.idlib.MapFile.idMapBrushSide
import neo.Renderer.Model.idRenderModel
import neo.Renderer.Model.modelSurface_s
import neo.idlib.containers.StrPool.idPoolStr
import neo.Renderer.ModelManager
import neo.CM.AbstractCollisionModel_local.cm_polygonBlock_s
import neo.CM.AbstractCollisionModel_local.cm_brushBlock_s
import neo.TempDump
import neo.ui.RegExp.idRegister.REGTYPE
import neo.ui.RegExp.idRegister
import neo.ui.Winvar.idWinVar
import neo.idlib.math.Vector.idVec2
import neo.ui.Rectangle.idRectangle
import neo.ui.Winvar.idWinVec4
import neo.ui.Winvar.idWinRectangle
import neo.ui.Winvar.idWinVec2
import neo.ui.Winvar.idWinVec3
import neo.ui.Winvar.idWinFloat
import neo.ui.Winvar.idWinInt
import neo.ui.Winvar.idWinBool
import neo.framework.DemoFile.idDemoFile
import neo.idlib.containers.List.idList
import neo.idlib.Text.Parser.idParser
import neo.ui.Window.idWindow
import neo.ui.Window.wexpOpType_t
import neo.ui.GuiScript.idGuiScriptList
import neo.idlib.math.Interpolate.idInterpolateAccelDecelLinear
import neo.ui.Winvar.idWinBackground
import neo.ui.DeviceContext.idDeviceContext
import neo.ui.SimpleWindow.drawWin_t
import neo.ui.UserInterfaceLocal.idUserInterfaceLocal
import neo.ui.Window.rvNamedEvent
import neo.ui.Window.wexpOp_t
import neo.ui.RegExp.idRegisterList
import neo.ui.Window.idWindow.ON
import neo.ui.Winvar.idWinStr
import neo.ui.Window.idTimeLineEvent
import neo.ui.Window.idTransitionData
import kotlin.jvm.JvmOverloads
import neo.ui.DeviceContext.idDeviceContext.CURSOR
import neo.ui.SimpleWindow.idSimpleWindow
import neo.ui.Winvar
import neo.ui.EditWindow.idEditWindow
import neo.ui.ChoiceWindow.idChoiceWindow
import neo.ui.SliderWindow.idSliderWindow
import neo.ui.MarkerWindow.idMarkerWindow
import neo.ui.BindWindow.idBindWindow
import neo.ui.ListWindow.idListWindow
import neo.ui.FieldWindow.idFieldWindow
import neo.ui.RenderWindow.idRenderWindow
import neo.ui.GameSSDWindow.idGameSSDWindow
import neo.ui.GameBearShootWindow.idGameBearShootWindow
import neo.ui.GameBustOutWindow.idGameBustOutWindow
import neo.sys.sys_public.sysEvent_s
import neo.sys.sys_public.sysEventType_t
import neo.framework.KeyInput
import neo.framework.KeyInput.idKeyInput
import neo.Renderer.RenderSystem_init
import neo.idlib.Dict_h.idDict
import neo.idlib.precompiled
import neo.ui.GuiScript.idGuiScript
import neo.ui.Window.wexpRegister_t
import neo.idlib.Dict_h.idKeyValue
import neo.framework.UsercmdGen
import neo.framework.DeclTable.idDeclTable
import neo.framework.DeclManager.declType_t
import neo.ui.Window.idRegEntry
import java.util.Objects
import java.lang.NumberFormatException
import neo.ui.UserInterface.idUserInterface
import neo.ui.GuiScript.guiCommandDef_t
import neo.ui.GuiScript.Script_Set
import neo.ui.GuiScript.Script_SetFocus
import neo.ui.GuiScript.Script_EndGame
import neo.ui.GuiScript.Script_ResetTime
import neo.ui.GuiScript.Script_ShowCursor
import neo.ui.GuiScript.Script_ResetCinematics
import neo.ui.GuiScript.Script_Transition
import neo.ui.GuiScript.Script_LocalSound
import neo.ui.GuiScript.Script_RunScript
import neo.ui.GuiScript.Script_EvalRegs
import neo.ui.GuiScript
import neo.ui.GuiScript.idGSWinVar
import neo.idlib.Lib.idLib
import neo.framework.CmdSystem
import neo.framework.CmdSystem.cmdExecution_t
import java.lang.StringBuffer
import neo.sys.win_input
import neo.ui.EditWindow
import java.util.HashMap
import neo.idlib.containers.idStrList
import neo.ui.ListWindow.idTabRect
import neo.ui.ListWindow
import neo.ui.DeviceContext.idDeviceContext.ALIGN
import neo.ui.Winvar.idMultiWinVar
import neo.ui.ListGUI.idListGUI
import neo.framework.Session.logStats_t
import neo.ui.MarkerWindow.markerData_t
import neo.framework.FileSystem_h.idFileList
import neo.Renderer.Material.shaderStage_t
import neo.Renderer.RenderWorld.renderLight_s
import neo.Game.Animation.Anim.idMD5Anim
import neo.Renderer.RenderWorld.renderView_s
import neo.Renderer.RenderWorld.idRenderWorld
import neo.Renderer.RenderWorld.renderEntity_s
import neo.Renderer.RenderSystem
import neo.Game.GameEdit
import neo.idlib.geometry.JointTransform.idJointMat
import neo.ui.DeviceContext.idDeviceContext.SCROLLBAR
import neo.Renderer.RenderSystem.fontInfoEx_t
import neo.Renderer.RenderSystem.fontInfo_t
import neo.ui.DeviceContext
import neo.idlib.geometry.DrawVert.idDrawVert
import neo.idlib.math.Matrix.idMat4
import neo.Renderer.RenderSystem.glyphInfo_t
import neo.ui.Rectangle.idRegion
import neo.ui.GameSSDWindow.SSDCrossHair
import neo.ui.GameSSDWindow
import neo.ui.GameSSDWindow.SSD
import neo.ui.GameSSDWindow.SSDEntity
import neo.ui.GameSSDWindow.SSDMover
import neo.ui.GameSSDWindow.SSDAsteroid
import neo.ui.GameSSDWindow.SSDAstronaut
import neo.ui.GameSSDWindow.SSDExplosion
import neo.ui.GameSSDWindow.SSDPoints
import neo.ui.GameSSDWindow.SSDProjectile
import neo.ui.GameSSDWindow.SSDPowerup
import neo.ui.GameSSDWindow.SSDLevelStats_t
import neo.ui.GameSSDWindow.SSDAsteroidData_t
import neo.ui.GameSSDWindow.SSDAstronautData_t
import neo.ui.GameSSDWindow.SSDLevelData_t
import neo.ui.GameSSDWindow.SSDPowerupData_t
import neo.ui.GameSSDWindow.SSDWeaponData_t
import neo.ui.GameSSDWindow.SSDGameStats_t
import neo.ui.UserInterfaceLocal.idUserInterfaceManagerLocal
import neo.ui.UserInterface.idUserInterface.idUserInterfaceManager
import neo.ui.UserInterface
import neo.ui.GameBustOutWindow.powerupType_t
import neo.ui.GameBustOutWindow.BOEntity
import neo.ui.GameBustOutWindow.collideDir_t
import neo.ui.GameBustOutWindow
import neo.ui.GameBustOutWindow.BOBrick
import neo.Renderer.Image_files
import neo.ui.ListGUILocal.idListGUILocal
import neo.ui.GameBearShootWindow.BSEntity
import neo.ui.GameBearShootWindow
import neo.sys.RC.doom_resource
import javax.imageio.ImageIO
import java.io.IOException
import neo.framework.CmdSystem.cmdFunction_t
import kotlin.Throws
import neo.idlib.Lib.idException
import neo.TempDump.TODO_Exception
import neo.sys.RC.CreateResourceIDs_f
import neo.sys.win_cpu.bitFlag_s
import neo.framework.BuildDefines
import neo.sys.win_cpu
import neo.sys.sys_public
import java.lang.Process
import java.io.BufferedReader
import neo.sys.win_net.net_interface
import neo.sys.win_net
import neo.sys.sys_public.netadr_t
import java.net.SocketAddress
import java.util.Enumeration
import java.net.NetworkInterface
import java.net.InetAddress
import java.net.Inet6Address
import java.net.SocketException
import neo.sys.win_net.udpMsg_s
import org.lwjgl.openal.ALC
import java.lang.UnsatisfiedLinkError
import neo.Sound.snd_system.idSoundSystemLocal
import java.lang.IllegalStateException
import neo.Sound.snd_local.idAudioHardware
import neo.idlib.math.Simd
import javax.sound.sampled.SourceDataLine
import neo.Sound.snd_local
import neo.sys.win_main
import java.lang.StringBuilder
import java.util.concurrent.ScheduledExecutorService
import neo.sys.sys_public.xthreadInfo
import neo.sys.sys_public.sysMemoryStats_s
import neo.sys.sys_public.xthread_t
import neo.sys.sys_public.xthreadPriority
import neo.sys.win_local
import java.util.concurrent.locks.ReentrantLock
import neo.sys.win_syscon
import neo.sys.win_glimp
import neo.sys.win_local.Win32Vars_t
import java.util.concurrent.locks.LockSupport
import java.util.concurrent.TimeUnit
import java.nio.file.Paths
import java.io.FilenameFilter
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.datatransfer.StringSelection
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.lang.Runnable
import neo.sys.win_main.Sys_In_Restart_f
import neo.sys.win_shared
import neo.framework.Async.AsyncNetwork.idAsyncNetwork
import java.nio.channels.FileChannel
import kotlin.jvm.JvmStatic
import neo.Tools.edit_public
import neo.sys.sys_local
import neo.sys.sys_local.idSysLocal
import java.text.SimpleDateFormat
import neo.sys.sys_public.idSys
import org.lwjgl.glfw.GLFWErrorCallback
import neo.sys.win_glimp.glimpParms_t
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFWVidMode
import org.lwjgl.opengl.GL
import neo.Renderer.tr_local
import java.awt.event.InputEvent
import neo.idlib.containers.CBool
import java.awt.event.KeyListener
import java.awt.event.MouseListener
import neo.sys.win_net.idUDPLag
import java.nio.ByteOrder
import neo.sys.sys_public.netadrtype_t
import java.util.TimerTask
import neo.sys.win_syscon.WinConData
import javax.swing.JFrame
import java.awt.Dimension
import javax.swing.UIManager
import java.lang.ClassNotFoundException
import java.lang.InstantiationException
import java.lang.IllegalAccessException
import javax.swing.UnsupportedLookAndFeelException
import neo.framework.Licensee
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.JTextField
import javax.swing.JButton
import neo.sys.win_syscon.Click
import javax.swing.JTextArea
import java.awt.Color
import javax.swing.JScrollPane
import neo.framework.EditField.idEditField
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import neo.Game.GameSys.Event.idEventDef
import neo.Tools.Compilers.AAS.AASFile.idAASSettings
import neo.Game.Game_local.idGameLocal
import neo.Game.Entity.idEntity
import neo.framework.DeclParticle.idDeclParticle
import neo.Game.Game_local.idEntityPtr
import neo.Game.AI.AI.moveCommand_t
import neo.Game.AI.AI.moveStatus_t
import neo.Game.AI.AI.moveType_t
import neo.Game.GameSys.SaveGame.idSaveGame
import neo.Game.GameSys.SaveGame.idRestoreGame
import neo.Game.AI.AAS.idAASCallback
import neo.Game.Pvs.pvsHandle_t
import neo.Game.AI.AAS.idAAS
import neo.Game.Game_local
import neo.Game.AI.AI.idAI
import neo.Game.Actor.idActor
import neo.Game.GameSys.Class.eventCallback_t
import neo.Game.Physics.Physics.idPhysics
import neo.Game.AI.AI.obstaclePath_s
import neo.Game.AI.AI_pathing.obstacle_s
import neo.Game.AI.AI_pathing
import neo.Game.AI.AI_pathing.pathNode_s
import neo.Tools.Compilers.AAS.AASFile
import neo.Game.GameSys.SysCvar
import neo.Game.AI.AI.predictedPath_s
import neo.Game.AI.AI_pathing.pathTrace_s
import neo.Game.AI.AI
import neo.Game.Physics.Clip.idClipModel
import neo.Game.AI.AI_pathing.ballistics_s
import neo.Game.GameSys.Class.eventCallback_t1
import neo.Game.GameSys.Class.idEventArg
import neo.Game.GameSys.Class.eventCallback_t2
import neo.Game.AI.AI_Events
import neo.Game.GameSys.Class.eventCallback_t0
import neo.Game.Moveable
import neo.Game.GameSys.Class.eventCallback_t3
import neo.Game.Script.Script_Program.idScriptBool
import neo.Game.Script.Script_Program.idScriptFloat
import neo.Game.Projectile.idProjectile
import neo.Sound.snd_shader.idSoundShader
import neo.Game.AI.AI.idMoveState
import neo.Game.AI.AI.particleEmitter_s
import neo.Game.Physics.Physics_Monster.idPhysics_Monster
import neo.Game.AI.AI.talkState_t
import neo.idlib.math.Angles
import neo.Game.Animation.Anim_Blend.idAnimator
import neo.Game.GameSys.Class.idClass
import neo.Game.Game_local.gameSoundChannel_t
import neo.Game.Player.idPlayer
import neo.Tools.Compilers.AAS.AASFile.idReachability
import neo.Game.Animation.Anim_Blend.idDeclModelDef
import neo.Game.Animation.Anim.frameCommand_t
import neo.Game.Animation.Anim_Blend.idAnim
import neo.Game.Animation.Anim.frameCommandType_t
import neo.Game.Animation.Anim
import neo.Game.Actor
import neo.Game.Physics.Physics_Monster.monsterMoveResult_t
import neo.Game.Moveable.idMoveable
import neo.Game.Animation.Anim.jointModTransform_t
import neo.Game.Projectile.idSoulCubeMissile
import neo.Game.AI.AAS.aasPath_s
import neo.Game.AI.AAS.aasObstacle_s
import neo.Game.AI.AAS.aasGoal_s
import neo.Game.AI.AI.idAASFindAreaOutOfRange
import neo.Game.AI.AI.idAASFindAttackPosition
import neo.Game.AI.AI.idAASFindCover
import java.lang.Math
import neo.Game.Animation.Anim.animFlags_t
import neo.Game.Player
import neo.Game.AF.afTouch_s
import neo.idlib.math.Quat.idQuat
import neo.Game.AFEntity.idAFAttachment
import neo.Game.Script.Script_Thread.idThread
import neo.Game.AI.AI.idCombatNode
import neo.Game.Misc.idPathCorner
import neo.Game.AFEntity.idAFEntity_Base
import neo.Tools.Compilers.AAS.AASFile.aasTrace_s
import neo.Game.AI.AAS_local.idAASLocal
import neo.Game.AI.AAS_routing.idRoutingObstacle
import neo.Game.AI.AAS_routing.idRoutingCache
import neo.Game.AI.AAS_routing.idRoutingUpdate
import neo.Tools.Compilers.AAS.AASFile.idAASFile
import neo.Tools.Compilers.AAS.AASFileManager
import neo.Tools.Compilers.AAS.AASFile.aasArea_s
import neo.Tools.Compilers.AAS.AASFile.aasFace_s
import neo.Tools.Compilers.AAS.AASFile.aasPortal_s
import neo.Tools.Compilers.AAS.AASFile.aasCluster_s
import neo.Game.AI.AAS_routing
import neo.Game.AI.AAS
import neo.Game.AI.AAS_pathing
import java.nio.IntBuffer
import neo.Tools.Compilers.AAS.AASFile.aasNode_s
import neo.Tools.Compilers.AAS.AASFile.aasEdge_s
import neo.Tools.Compilers.AAS.AASFile.idReachability_Walk
import neo.Game.AI.AI_Vagary.idAI_Vagary
import neo.Game.AI.AI_Vagary
import neo.Game.GameSys.Class.eventCallback_t5
import neo.idlib.geometry.Winding2D.idWinding2D
import neo.idlib.BV.Box.idBox
import neo.idlib.containers.Queue.idQueueTemplate
import neo.Game.Script.Script_Program.idVarDef
import neo.idlib.containers.StaticList.idStaticList
import neo.Game.Script.Script_Program.function_t
import neo.Game.Script.Script_Program
import neo.Game.Script.Script_Program.statement_s
import neo.Game.Script.Script_Program.idTypeDef
import neo.Game.Script.Script_Program.idVarDefName
import neo.Game.Script.idProgram
import neo.Game.Script.Script_Compiler.idCompiler
import neo.Game.Script.Script_Program.idCompileError
import neo.Game.Script.Script_Compiler
import neo.Game.Script.Script_Compiler.opcode_s
import neo.framework.FileSystem_h.fsMode_t
import neo.Game.Script.Script_Program.varEval_s
import neo.Game.Script.Script_Program.idVarDef.initialized_t
import neo.Game.Script.Script_Thread
import neo.Game.Entity.signalNum_t
import neo.Game.Camera.idCamera
import neo.Game.GameSys.Class.eventCallback_t6
import neo.Game.GameSys.Class.eventCallback_t4
import neo.Game.Script.Script_Interpreter.idInterpreter
import neo.Game.Physics.Physics_AF.idAFBody
import neo.Game.Script.Script_Thread.idThread.ListThreads_f
import java.nio.BufferUnderflowException
import neo.Game.Script.Script_Program.idScriptObject
import neo.Game.Script.Script_Program.idScriptVariable
import neo.Game.Script.Script_Program.eval_s
import neo.Game.Game
import neo.Game.Script.Script_Interpreter.prstack_s
import neo.Game.Script.Script_Interpreter
import neo.Game.Animation.Anim.AFJointModType_t
import neo.Game.AF.jointConversion_s
import neo.Game.Physics.Physics_AF.idPhysics_AF
import neo.framework.DeclAF.idDeclAF
import neo.framework.DeclManager.declState_t
import neo.Game.AF
import neo.Game.Physics.Physics_AF.idAFConstraint
import neo.Game.Physics.Physics_AF.constraintType_t
import neo.Game.Physics.Physics_AF.idAFConstraint_BallAndSocketJoint
import neo.Game.Physics.Physics_AF.idAFConstraint_UniversalJoint
import neo.Game.Physics.Physics_AF.idAFConstraint_Hinge
import neo.Game.Physics.Physics.impactInfo_s
import neo.Game.Physics.Physics_AF.idAFConstraint_Fixed
import neo.framework.DeclAF.idDeclAF_Body
import neo.Game.AF.idAF
import neo.framework.DeclAF.declAFJointMod_t
import neo.framework.DeclAF.idDeclAF_Constraint
import neo.framework.DeclAF.declAFConstraintType_t
import neo.Game.Physics.Physics_AF.idAFConstraint_Slider
import neo.Game.Physics.Physics_AF.idAFConstraint_Spring
import neo.framework.DeclAF.getJointTransform_t
import neo.Game.FX.idEntityFx
import neo.Game.FX
import neo.Game.FX.idFXLocalAction
import neo.framework.DeclFX.idDeclFX
import neo.framework.DeclFX.idFXSingleAction
import neo.framework.DeclFX.fx_enum
import neo.idlib.BitMsg.idBitMsgDelta
import neo.Game.FX.idTeleporter
import neo.idlib.containers.Hierarchy.idHierarchy
import neo.Game.GameSys.Class.idTypeInfo
import java.lang.FunctionalInterface
import neo.Game.WorldSpawn.idWorldspawn
import neo.Game.Misc.idStaticEntity
import neo.Game.Trigger.idTrigger_Multi
import neo.Game.Target.idTarget_Tip
import neo.Game.Target.idTarget_Remove
import neo.Game.Mover.idMover
import neo.Game.Light.idLight
import neo.Game.Camera.idCameraAnim
import neo.Game.Misc.idFuncEmitter
import neo.Game.Misc.idAnimated
import neo.Game.Projectile.idBFGProjectile
import neo.Game.Trigger.idTrigger_Hurt
import neo.Game.Item.idMoveablePDAItem
import neo.Game.Misc.idLocationEntity
import neo.Game.Misc.idPlayerStart
import neo.Game.Sound.idSound
import neo.Game.Target.idTarget_GiveEmail
import neo.Game.Target.idTarget_SetPrimaryObjective
import neo.Game.Item.idObjectiveComplete
import neo.Game.Target.idTarget
import neo.Game.Camera.idCameraView
import neo.Game.Item.idObjective
import neo.Game.Target.idTarget_SetShaderParm
import neo.Game.Target.idTarget_FadeEntity
import neo.Game.Item.idItem
import neo.Game.Mover.idSplinePath
import neo.Game.AFEntity.idAFEntity_Generic
import neo.Game.Mover.idDoor
import neo.Game.Trigger.idTrigger_Count
import neo.Game.Target.idTarget_EndLevel
import neo.Game.Target.idTarget_CallObjectFunction
import neo.Game.Trigger.idTrigger_Fade
import neo.Game.Item.idPDAItem
import neo.Game.Item.idVideoCDItem
import neo.Game.Misc.idLocationSeparatorEntity
import neo.Game.Projectile.idDebris
import neo.Game.Misc.idSpawnableEntity
import neo.Game.Target.idTarget_LightFadeIn
import neo.Game.Target.idTarget_LightFadeOut
import neo.Game.Item.idItemPowerup
import neo.Game.Misc.idForceField
import neo.Game.Target.idTarget_LockDoor
import neo.Game.Target.idTarget_SetInfluence
import neo.Game.Moveable.idExplodingBarrel
import neo.Game.Target.idTarget_EnableLevelWeapons
import neo.Game.AFEntity.idAFEntity_WithAttachedHead
import neo.Game.Misc.idFuncAASObstacle
import neo.Game.Misc.idVacuumEntity
import neo.Game.Mover.idRotater
import neo.Game.Mover.idElevator
import neo.Game.Misc.idShaking
import neo.Game.Misc.idFuncRadioChatter
import neo.Game.Misc.idFuncPortal
import neo.Game.Item.idMoveableItem
import neo.Game.Misc.idFuncSmoke
import neo.Game.Misc.idPhantomObjects
import neo.Game.Misc.idBeam
import neo.Game.Misc.idExplodable
import neo.Game.Misc.idEarthQuake
import neo.Game.Projectile.idGuidedProjectile
import neo.Game.Target.idTarget_Show
import neo.Game.BrittleFracture.idBrittleFracture
import neo.Game.Trigger.idTrigger_Timer
import neo.Game.Mover.idPendulum
import neo.Game.Item.idItemRemover
import neo.Game.Target.idTarget_GiveSecurity
import neo.Game.Trigger.idTrigger_EntityName
import neo.Game.Moveable.idBarrel
import neo.Game.Misc.idActivator
import neo.Game.Misc.idFuncSplat
import neo.Game.Target.idTarget_Damage
import neo.Game.Target.idTarget_SetKeyVal
import neo.Game.Target.idTarget_EnableStamina
import neo.Game.Misc.idVacuumSeparatorEntity
import neo.Game.Misc.idDamagable
import neo.Game.SecurityCamera.idSecurityCamera
import neo.Game.Trigger.idTrigger_Touch
import neo.Game.AFEntity.idAFEntity_ClawFourFingers
import neo.Game.Mover.idBobber
import neo.Game.Target.idTarget_LevelTrigger
import neo.Game.Target.idTarget_RemoveWeapons
import neo.Game.Mover.idPlat
import neo.Game.Entity.idAnimatedEntity
import neo.Game.GameSys.Event.idEvent
import neo.Game.GameSys.Class.classSpawnFunc_t
import neo.Game.GameSys.Class.idClass.DisplayInfo_f
import neo.Game.GameSys.Class.idClass.ListClasses_f
import neo.Game.GameSys.Class.idEventFunc
import neo.Game.GameSys.Class.idClass_Save
import neo.Game.GameSys.Class.idClass_Restore
import neo.idlib.containers.LinkList.idLinkList
import neo.Game.GameSys.SysCmds.gameDebugLine_t
import neo.Game.GameSys.SysCmds
import neo.idlib.BitMsg.idBitMsg
import neo.framework.Async.NetworkSystem
import neo.Game.GameSys.SysCmds.Cmd_EntityList_f
import neo.Game.GameSys.SysCmds.Cmd_ActiveEntityList_f
import neo.Game.GameSys.SysCmds.Cmd_ListSpawnArgs_f
import neo.Game.GameSys.SysCmds.Cmd_ReloadScript_f
import neo.Game.GameSys.SysCmds.Cmd_Script_f
import neo.Game.GameSys.SysCmds.Cmd_KillMonsters_f
import neo.Game.GameSys.SysCmds.Cmd_KillMovables_f
import neo.Game.GameSys.SysCmds.Cmd_KillRagdolls_f
import neo.Game.Weapon
import neo.Game.Weapon.idWeapon
import neo.Game.GameSys.SysCmds.Cmd_Give_f
import neo.Game.GameSys.SysCmds.Cmd_CenterView_f
import neo.Game.GameSys.SysCmds.Cmd_God_f
import neo.Game.GameSys.SysCmds.Cmd_Notarget_f
import neo.Game.GameSys.SysCmds.Cmd_Noclip_f
import neo.Game.GameSys.SysCmds.Cmd_Kill_f
import neo.Game.GameSys.SysCmds.Cmd_PlayerModel_f
import neo.Game.GameSys.SysCmds.Cmd_Say_f
import neo.Game.GameSys.SysCmds.Cmd_SayTeam_f
import neo.Game.GameSys.SysCmds.Cmd_AddChatLine_f
import neo.Game.GameSys.SysCmds.Cmd_Kick_f
import neo.Game.GameSys.SysCmds.Cmd_GetViewpos_f
import neo.Game.GameSys.SysCmds.Cmd_SetViewpos_f
import neo.Game.GameSys.SysCmds.Cmd_Teleport_f
import neo.Game.GameSys.SysCmds.Cmd_Trigger_f
import neo.Game.GameSys.SysCmds.Cmd_Spawn_f
import neo.Game.GameSys.SysCmds.Cmd_Damage_f
import neo.Game.GameSys.SysCmds.Cmd_Remove_f
import neo.Game.GameSys.SysCmds.Cmd_TestLight_f
import neo.Game.GameSys.SysCmds.Cmd_TestPointLight_f
import neo.Game.GameSys.SysCmds.Cmd_PopLight_f
import neo.Game.GameSys.SysCmds.Cmd_ClearLights_f
import neo.Game.GameSys.SysCmds.Cmd_TestFx_f
import neo.Game.GameSys.SysCmds.Cmd_AddDebugLine_f
import neo.Game.GameSys.SysCmds.Cmd_RemoveDebugLine_f
import neo.Game.GameSys.SysCmds.Cmd_BlinkDebugLine_f
import neo.Game.GameSys.SysCmds.Cmd_ListDebugLines_f
import neo.Game.GameSys.SysCmds.Cmd_ListCollisionModels_f
import neo.Game.GameSys.SysCmds.Cmd_CollisionModelInfo_f
import neo.Game.Animation.Anim_Import.idModelExport
import neo.Game.GameSys.SysCmds.Cmd_ExportModels_f
import neo.Game.Animation.Anim.idAnimManager
import neo.Game.GameSys.SysCmds.Cmd_ReexportModels_f
import neo.Game.GameSys.SysCmds.Cmd_ReloadAnims_f
import neo.Game.GameSys.SysCmds.Cmd_ListAnims_f
import neo.Game.GameSys.SysCmds.Cmd_AASStats_f
import neo.Game.GameSys.SysCmds.Cmd_TestDamage_f
import neo.Game.GameSys.SysCmds.Cmd_TestBoneFx_f
import neo.Game.GameSys.SysCmds.Cmd_TestDeath_f
import neo.Game.GameSys.SysCmds.Cmd_WeaponSplat_f
import neo.Game.GameSys.SysCmds.Cmd_SaveSelected_f
import neo.Game.GameSys.SysCmds.Cmd_DeleteSelected_f
import neo.Game.GameSys.SysCmds.Cmd_SaveMoveables_f
import neo.Game.GameSys.SysCmds.Cmd_SaveRagdolls_f
import neo.Game.GameSys.SysCmds.Cmd_BindRagdoll_f
import neo.Game.GameSys.SysCmds.Cmd_UnbindRagdoll_f
import neo.Game.GameSys.SysCmds.Cmd_GameError_f
import neo.Game.GameSys.SysCmds.Cmd_SaveLights_f
import neo.Game.GameSys.SysCmds.Cmd_SaveParticles_f
import neo.Game.GameSys.SysCmds.Cmd_DisasmScript_f
import neo.Game.GameSys.SysCmds.Cmd_TestSave_f
import neo.Game.GameSys.SysCmds.Cmd_RecordViewNotes_f
import neo.Game.GameSys.SysCmds.Cmd_CloseViewNotes_f
import neo.Game.GameSys.SysCmds.Cmd_ShowViewNotes_f
import neo.Renderer.Model.srfTriangles_s
import neo.Game.GameSys.SysCmds.Cmd_NextGUI_f
import neo.TempDump.void_callback
import neo.Game.GameSys.SysCmds.ArgCompletion_DefFile
import neo.Game.GameSys.SysCmds.Cmd_TestId_f
import neo.framework.CmdSystem.idCmdSystem.ArgCompletion_Integer
import neo.Game.MultiplayerGame
import neo.framework.CmdSystem.idCmdSystem.ArgCompletion_MapName
import neo.Game.GameSys.SysCvar.gameVersion_s
import neo.framework.BuildVersion
import neo.idlib.geometry.Winding.idWinding
import neo.idlib.math.Vector.idVec5
import neo.framework.DeclSkin.idDeclSkin
import neo.Game.Game.refSound_t
import neo.framework.UsercmdGen.usercmd_t
import neo.Game.GameSys.TypeInfo.WriteVariableType_t
import neo.Game.GameSys.NoGameTypeInfo.classTypeInfo_t
import neo.Game.GameSys.NoGameTypeInfo
import neo.Game.GameSys.NoGameTypeInfo.enumTypeInfo_t
import neo.Game.GameSys.TypeInfo.idTypeInfoTools
import neo.Game.GameSys.TypeInfo.idTypeInfoTools.PrintVariable
import neo.Game.GameSys.TypeInfo.idTypeInfoTools.WriteVariable
import neo.Game.GameSys.NoGameTypeInfo.classVariableInfo_t
import neo.Game.GameSys.TypeInfo.idTypeInfoTools.WriteGameStateVariable
import neo.Game.GameSys.TypeInfo.idTypeInfoTools.InitVariable
import neo.Game.GameSys.TypeInfo.idTypeInfoTools.VerifyVariable
import neo.Game.GameSys.TypeInfo.WriteGameState_f
import neo.Game.GameSys.TypeInfo.CompareGameState_f
import neo.Game.GameSys.TypeInfo.TestSaveGame_f
import neo.Game.GameSys.TypeInfo.ListTypeInfo_f.SortTypeInfoBySize
import neo.Game.GameSys.TypeInfo.ListTypeInfo_f.SortTypeInfoByName
import neo.idlib.containers.List.cmp_t
import neo.Game.GameSys.TypeInfo.ListTypeInfo_f
import neo.Game.GameSys.NoGameTypeInfo.constantInfo_t
import neo.Game.GameSys.NoGameTypeInfo.enumValueInfo_t
import neo.Game.IK.idIK
import neo.Game.IK.idIK_Walk
import neo.Game.IK.idIK_Reach
import neo.Game.Physics.Clip.trmCache_s
import neo.Game.Physics.Clip.clipSector_s
import neo.Game.Physics.Clip.clipLink_s
import neo.Game.Physics.Clip.idClip
import neo.Game.Physics.Clip.idClip.listParms_s
import neo.Renderer.RenderWorld.modelTrace_s
import neo.Game.Physics.Push.idPush.pushed_s
import neo.Game.Physics.Push.idPush.pushedGroup_s
import neo.Game.Physics.Push
import neo.Game.Projectile
import neo.Game.AFEntity
import neo.Game.Physics.Physics_Actor.idPhysics_Actor
import neo.Game.Physics.Force.idForce
import neo.Game.Physics.Physics_AF
import neo.Game.Physics.Physics_AF.AFPState_s
import neo.idlib.math.Matrix.idMatX
import neo.idlib.math.Vector.idVecX
import neo.Game.Physics.Physics_AF.idAFConstraint.constraintFlags_s
import neo.Game.Physics.Physics_AF.idAFConstraint_ConeLimit
import neo.Game.Physics.Physics_AF.idAFConstraint_BallAndSocketJointFriction
import neo.Game.Physics.Physics_AF.idAFConstraint_PyramidLimit
import neo.Game.Physics.Physics_AF.idAFConstraint_UniversalJointFriction
import neo.Game.Physics.Physics_AF.idAFConstraint_HingeFriction
import neo.Game.Physics.Physics_AF.idAFConstraint_HingeSteering
import neo.Game.Physics.Physics_AF.idAFConstraint_ContactFriction
import neo.Game.Physics.Physics_AF.idAFConstraint_Contact
import neo.Game.Physics.Physics_AF.AFBodyPState_s
import neo.Game.Physics.Physics_AF.idAFBody.bodyFlags_s
import neo.Game.Physics.Physics_AF.idAFTree
import neo.Game.Physics.Physics_Base.idPhysics_Base
import neo.Game.Physics.Physics_AF.AFCollision_s
import neo.idlib.math.Lcp.idLCP
import neo.idlib.math.Quat.idCQuat
import neo.Game.Physics.Force_Field.forceFieldApplyType
import neo.Game.Physics.Force_Field.forceFieldType
import neo.Game.Physics.Physics_Player.idPhysics_Player
import neo.Game.Physics.Physics_Base.contactEntity_t
import neo.idlib.BV.Bounds
import neo.Game.Physics.Physics
import neo.Game.Physics.Physics_Player
import neo.Game.Physics.Physics_Player.playerPState_s
import neo.Game.Physics.Physics_Player.waterLevel_t
import neo.Game.Physics.Physics_Player.pmtype_t
import neo.Game.Physics.Physics_Static.staticPState_s
import neo.Game.Physics.Physics_Static.idPhysics_Static
import neo.Game.Physics.Physics_Monster
import neo.Game.Physics.Physics_Monster.monsterPState_s
import neo.Game.Physics.Physics_RigidBody
import neo.Game.Physics.Physics_RigidBody.rigidBodyPState_s
import neo.Game.Physics.Physics_RigidBody.rigidBodyIState_s
import java.nio.FloatBuffer
import neo.idlib.math.Ode.idODE
import neo.Game.Physics.Physics_RigidBody.idPhysics_RigidBody
import neo.Game.Physics.Physics_RigidBody.idPhysics_RigidBody.rigidBodyDerivatives_s
import neo.idlib.math.Ode.deriveFunction_t
import neo.Game.Physics.Physics_RigidBody.idPhysics_RigidBody.RigidBodyDerivatives
import neo.idlib.math.Ode.idODE_Euler
import neo.Game.Physics.Physics_Parametric.parametricPState_s
import neo.idlib.math.Extrapolate.idExtrapolate
import neo.idlib.math.Curve.idCurve_Spline
import neo.Game.Physics.Physics_Parametric
import neo.idlib.math.Extrapolate
import neo.Game.Physics.Physics_Parametric.idPhysics_Parametric
import neo.Game.Physics.Physics_StaticMulti
import neo.Game.Physics.Physics_StaticMulti.idPhysics_StaticMulti
import neo.Game.Pvs.pvsPassage_t
import neo.Game.Pvs.pvsPortal_t
import neo.Game.Pvs.pvsStack_t
import neo.Game.Pvs.pvsCurrent_t
import neo.Game.Pvs
import neo.Game.Pvs.pvsArea_t
import neo.Game.Pvs.pvsType_t
import neo.Renderer.RenderWorld.exitPortal_t
import neo.Game.Pvs.idPVS
import neo.Renderer.RenderWorld.portalConnection_t
import neo.idlib.geometry.JointTransform.idJointQuat
import neo.Game.Animation.Anim.jointAnimInfo_t
import neo.Renderer.Model.idMD5Joint
import neo.Game.Animation.Anim.frameBlend_t
import neo.Game.Animation.Anim.frameLookup_t
import neo.Game.Animation.Anim.jointInfo_t
import neo.Game.Sound
import neo.framework.DeclManager.idDecl
import java.util.Collections
import neo.Game.Animation.Anim_Blend
import java.lang.Character
import neo.Game.Animation.Anim_Blend.idAnimBlend
import neo.Game.Animation.Anim.idAFPoseJointMod
import neo.Game.Animation.Anim.jointMod_t
import neo.idlib.containers.BinSearch
import neo.Game.Animation.Anim_Import
import neo.Game.Animation.Anim_Testmodel.idTestModel
import neo.Game.Actor.copyJoints_t
import neo.Game.Animation.Anim_Testmodel.idTestModel.KeepTestModel_f
import neo.Game.Animation.Anim_Testmodel.idTestModel.TestSkin_f
import neo.Game.Animation.Anim_Testmodel.idTestModel.TestShaderParm_f
import neo.Game.Animation.Anim_Testmodel.idTestModel.TestModel_f
import neo.Game.Animation.Anim_Testmodel.idTestModel.ArgCompletion_TestModel
import neo.Game.Animation.Anim_Testmodel.idTestModel.TestParticleStopTime_f
import neo.Game.Animation.Anim_Testmodel.idTestModel.TestAnim_f
import neo.Game.Animation.Anim_Testmodel.idTestModel.ArgCompletion_TestAnim
import neo.Game.Animation.Anim_Testmodel.idTestModel.TestBlend_f
import neo.Game.Animation.Anim_Testmodel.idTestModel.TestModelNextAnim_f
import neo.Game.Animation.Anim_Testmodel.idTestModel.TestModelPrevAnim_f
import neo.Game.Animation.Anim_Testmodel.idTestModel.TestModelNextFrame_f
import neo.Game.Animation.Anim_Testmodel.idTestModel.TestModelPrevFrame_f
import neo.Sound.sound.idSoundWorld
import neo.Game.Game.gameReturn_t
import neo.Game.Game.escReply_t
import neo.Game.Game.allowReply_t
import neo.Sound.snd_shader
import neo.Sound.sound.idSoundEmitter
import neo.Game.AFEntity.jointTransformData_t
import neo.Game.Game.idGameEdit
import neo.Tools.Compilers.AAS.AASFileManager.idAASFileManager
import neo.framework.CmdSystem.idCmdSystem
import neo.framework.Common.idCommon
import neo.framework.CVarSystem.idCVarSystem
import neo.framework.DeclManager.idDeclManager
import neo.framework.FileSystem_h.idFileSystem
import neo.framework.Async.NetworkSystem.idNetworkSystem
import neo.Renderer.ModelManager.idRenderModelManager
import neo.Renderer.RenderSystem.idRenderSystem
import neo.Sound.sound.idSoundSystem
import neo.Game.Game.idGame
import neo.Renderer.RenderWorld.deferredEntityCallback_t
import neo.Game.Misc
import neo.Game.Misc.idSpring
import neo.Game.Physics.Force_Spring.idForce_Spring
import neo.Game.Physics.Force_Field.idForce_Field
import neo.Game.AFEntity.idAFEntity_Gibbable
import neo.Game.Misc.idLiquid
import neo.Renderer.Model_liquid.idRenderModelLiquid
import neo.Game.Misc.idFuncAASPortal
import neo.Game.GameSys.SaveGame
import neo.Game.Actor.idAttachInfo
import neo.Game.Actor.idAnimState
import neo.Game.IK
import neo.Renderer.Material.surfTypes_t
import neo.Game.Light
import neo.Game.Mover
import neo.Game.Mover.idMover.moveState_t
import neo.Game.Mover.idMover.moverCommand_t
import neo.Game.Mover.idMover.rotationState_t
import neo.Game.Mover.idMover.moveStage_t
import neo.Game.Mover.moverState_t
import neo.Game.Mover.floorInfo_s
import neo.Game.Mover.idElevator.elevatorState_t
import neo.Game.Mover.idMover_Binary
import neo.Game.Mover.idMover_Periodic
import neo.Game.Mover.idRiser
import neo.Game.Camera
import neo.Game.Camera.cameraFrame_t
import neo.Game.Entity.signal_t
import neo.TempDump.NiLLABLE
import neo.Game.Entity.idEntity.entityFlags_s
import neo.Game.Entity.signalList_t
import neo.framework.DeclEntityDef.idDeclEntityDef
import neo.Renderer.Model.dynamicModel_t
import neo.idlib.math.Curve.idCurve_CatmullRomSpline
import neo.idlib.math.Curve.idCurve_NonUniformBSpline
import neo.idlib.math.Curve.idCurve_NURBS
import neo.idlib.math.Curve.idCurve_BSpline
import neo.Game.Entity.damageEffect_s
import neo.Game.Player.idLevelTriggerInfo
import neo.Game.Player.idObjectiveInfo
import neo.Game.Player.idItemInfo
import neo.Game.Player.aasLocation_t
import neo.idlib.math.Interpolate.idInterpolate
import neo.Game.Player.loggedAccel_t
import neo.Game.GameEdit.idDragEntity
import neo.Game.Player.idInventory
import neo.Game.PlayerView.idPlayerView
import neo.Game.AFEntity.idAFEntity_Vehicle
import neo.Game.PlayerIcon.idPlayerIcon
import neo.Game.MultiplayerGame.gameType_t
import neo.framework.DeclPDA.idDeclPDA
import neo.framework.DeclPDA.idDeclVideo
import neo.Game.Game_network
import neo.Renderer.RenderWorld.guiPoint_t
import neo.framework.DeclPDA.idDeclAudio
import neo.framework.DeclPDA.idDeclEmail
import neo.Game.Target.idTarget_SessionCommand
import neo.Game.Target.idTarget_WaitForButton
import neo.Game.Target.idTarget_SetGlobalShaderTime
import neo.Game.Target.idTarget_SetShaderTime
import neo.Game.Target.idTarget_Give
import neo.Game.Target.idTarget_SetModel
import neo.Game.Target.idTarget_SetFov
import neo.Game.Target.idTarget_FadeSoundClass
import neo.Game.Weapon.weaponStatus_t
import neo.Game.Trigger.idTrigger
import neo.Game.AFEntity.idMultiModelAF
import neo.Game.Physics.Physics_AF.idAFConstraint_Suspension
import neo.Game.AFEntity.idAFEntity_VehicleSimple
import neo.Game.AFEntity.idAFEntity_VehicleFourWheels
import neo.Game.AFEntity.idAFEntity_VehicleSixWheels
import neo.Game.Physics.Force_Constant.idForce_Constant
import neo.Game.Physics.Force_Drag.idForce_Drag
import neo.Game.GameEdit.idCursor3D
import neo.Game.GameEdit.selectedTypeInfo_s
import neo.Game.Moveable.idExplodingBarrel.explode_state_t
import neo.Game.Game.gameExport_t
import neo.Game.Game.gameImport_t
import neo.Sound.snd_system
import neo.Game.Game_local.entityState_s
import neo.Game.Game_local.snapshot_s
import neo.Game.Game_local.entityNetEvent_s
import neo.Game.Game_network.idEventQueue
import neo.Game.Game_local.spawnSpot_t
import neo.Game.GameEdit.idEditEntities
import neo.Game.MultiplayerGame.idMultiplayerGame
import neo.Game.Physics.Push.idPush
import neo.Game.SmokeParticles.idSmokeParticles
import neo.idlib.math.Simd.idSIMD
import neo.framework.DeclManager.idListDecls_f
import neo.framework.DeclManager.idPrintDecls_f
import neo.framework.CmdSystem.idCmdSystem.ArgCompletion_Decl
import neo.Game.Game_network.idEventQueue.outOfOrderBehaviour_t
import neo.Game.MultiplayerGame.snd_evt_t
import neo.Game.MultiplayerGame.idMultiplayerGame.msg_evt_t
import neo.Game.GameSys.Class.idAllocError
import neo.Game.Game_local.idGameLocal.sortSpawnPoints
import neo.Game.Game_local.idGameLocal.ArgCompletion_EntityName
import neo.Game.MultiplayerGame.idMultiplayerGame.DropWeapon_f
import neo.Game.MultiplayerGame.idMultiplayerGame.MessageMode_f
import neo.Game.MultiplayerGame.idMultiplayerGame.VoiceChat_f
import neo.Game.MultiplayerGame.idMultiplayerGame.VoiceChatTeam_f
import neo.Game.Game_local.idGameLocal.MapRestart_f
import neo.Game.MultiplayerGame.idMultiplayerGame.ForceReady_f
import neo.Game.PlayerIcon.playerIconType_t
import neo.Game.PlayerIcon
import neo.Game.PlayerView.screenBlob_t
import neo.Game.PlayerView
import neo.Game.Projectile.idProjectile.projectileFlags_s
import neo.Game.Projectile.idProjectile.projectileState_t
import neo.Game.Projectile.beamTarget_t
import neo.Game.SecurityCamera
import neo.Game.SmokeParticles.singleSmoke_t
import neo.framework.DeclParticle.idParticleStage
import neo.Game.SmokeParticles.activeSmokeStage_t
import neo.Game.SmokeParticles
import neo.framework.DeclParticle.particleGen_t
import neo.Game.BrittleFracture.shard_s
import neo.Game.BrittleFracture
import neo.Game.MultiplayerGame.playerVote_t
import neo.Game.MultiplayerGame.mpChatLine_s
import neo.Game.MultiplayerGame.mpPlayerState_s
import neo.Game.MultiplayerGame.idMultiplayerGame.vote_flags_t
import neo.Game.MultiplayerGame.idMultiplayerGame.vote_result_t
import neo.idlib.BV.Sphere.idSphere
import neo.idlib.BV.Frustum.idFrustum
import neo.idlib.BV.Frustum
import neo.idlib.math.Matrix.idMat2
import neo.idlib.math.Matrix.idMat0
import neo.idlib.math.Matrix.idMat5
import neo.idlib.math.Matrix.idMat6
import neo.idlib.math.Lcp
import neo.idlib.math.Lcp.idLCP_Square
import neo.idlib.math.Lcp.idLCP_Symmetric
import neo.idlib.math.Simd.idSIMDProcessor
import neo.idlib.math.Simd_Generic.idSIMD_Generic
import neo.idlib.math.Simd.idSIMD.Test_f
import neo.Renderer.Model.shadowCache_s
import neo.Renderer.Model.dominantTri_s
import neo.idlib.math.Vector.idVec
import neo.TempDump.TypeErasure_Expection
import neo.idlib.math.Curve.idCurve
import neo.idlib.math.Math_h.idMath._flint
import neo.idlib.math.Random.idRandom2
import neo.idlib.math.Vector.idPolar3
import org.lwjgl.BufferUtils
import neo.idlib.math.Complex.idComplex
import neo.idlib.math.Polynomial.idPolynomial
import neo.idlib.math.Polynomial
import neo.TempDump.Deprecation_Exception
import neo.idlib.math.Simd_Generic
import neo.idlib.Text.Str.Measure_t
import neo.TempDump.CPP_class.Char
import neo.idlib.Text.Str.idStr.formatList_t
import neo.idlib.Text.Lexer.punctuation_t
import java.nio.CharBuffer
import java.lang.IndexOutOfBoundsException
import neo.idlib.Text.Base64.idBase64
import neo.idlib.Text.Parser.define_s
import neo.idlib.Text.Parser.indent_s
import neo.idlib.Text.Parser.idParser.value_s
import neo.idlib.Text.Parser.idParser.operator_s
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import neo.idlib.geometry.Surface.surfaceEdge_t
import neo.idlib.geometry.Surface.idSurface
import neo.idlib.geometry.Winding2D
import neo.idlib.geometry.TraceModel.idTraceModel.volumeIntegrals_t
import neo.idlib.geometry.TraceModel.idTraceModel.projectionIntegrals_t
import neo.idlib.geometry.TraceModel.idTraceModel.polygonIntegrals_t
import neo.idlib.geometry.Surface_Polytope
import neo.idlib.geometry.Surface_Polytope.idSurface_Polytope
import java.lang.RuntimeException
import java.math.BigInteger
import neo.framework.CVarSystem.idInternalCVar
import neo.framework.CmdSystem.commandDef_s
import neo.TempDump.reflects
import java.util.LinkedList
import neo.idlib.containers.Stack.idStackTemplate
import neo.idlib.containers.StrPool.idStrPool
import neo.idlib.containers.HashIndex
import java.util.function.ToIntFunction
import java.util.stream.Collectors
import neo.idlib.containers.StaticList
import neo.idlib.BitMsg
import java.lang.CloneNotSupportedException
import neo.idlib.Dict_h.KeyCompare
import neo.idlib.Dict_h.idDict.ListKeys_f
import neo.idlib.Dict_h.idDict.ListValues_f
import neo.idlib.LangDict.idLangKeyValue
import neo.Renderer.Cinematic.cinData_t
import neo.Sound.sound.soundDecoderInfo_t
import neo.framework.Common.MemInfo_t
import neo.Sound.snd_local.waveformatex_s
import java.nio.ShortBuffer
import org.lwjgl.openal.AL10
import neo.Sound.snd_wavefile.idWaveFile
import neo.Sound.snd_local.idSampleDecoder
import neo.Sound.snd_cache
import neo.Sound.snd_cache.idSoundSample
import neo.Sound.snd_local.waveformat_s
import neo.Sound.snd_local.pcmwaveformat_s
import neo.Sound.snd_local.waveformatextensible_s
import neo.Sound.snd_local.mminfo_s
import neo.Sound.snd_decoder.idSampleDecoderLocal
import neo.sys.win_snd.idAudioHardwareWIN32
import neo.Sound.snd_world.soundPortalTrace_s
import neo.Sound.snd_emitter.idSoundEmitterLocal
import neo.Sound.snd_emitter.idSoundFade
import neo.Sound.sound
import neo.framework.DemoFile.demoSystem_t
import neo.Sound.snd_local.soundDemoCommand_t
import neo.Sound.snd_emitter
import neo.Sound.snd_world.idSoundWorldLocal
import neo.Sound.snd_wavefile
import neo.Sound.snd_emitter.idSoundChannel
import neo.Renderer.Cinematic.idSndWindow
import neo.Sound.snd_emitter.idSlowChannel
import neo.idlib.math.Simd.speakerLabel
import neo.Sound.snd_efxfile.idEFXFile
import neo.Sound.snd_emitter.SoundFX
import neo.Sound.snd_system.openalSource_t
import neo.Sound.snd_cache.idSoundCache
import neo.Sound.snd_world.s_stats
import neo.sys.win_snd
import org.lwjgl.openal.ALC10
import org.lwjgl.openal.ALCCapabilities
import org.lwjgl.openal.ALCapabilities
import org.lwjgl.openal.AL
import neo.Sound.snd_system.ListSounds_f
import neo.Sound.snd_system.ListSoundDecoders_f
import neo.Sound.snd_system.SoundReloadSounds_f
import neo.Sound.snd_system.TestSound_f
import neo.framework.CmdSystem.idCmdSystem.ArgCompletion_SoundName
import neo.Sound.snd_system.SoundSystemRestart_f
import neo.Sound.snd_emitter.SoundFX_Lowpass
import neo.Sound.snd_emitter.SoundFX_Comb
import neo.framework.File_h.idFile_Memory
import org.lwjgl.stb.STBVorbis
import neo.Sound.snd_decoder
import org.lwjgl.PointerBuffer
import neo.Sound.snd_efxfile.idSoundEffect
import neo.Sound.snd_emitter.FracTime
import neo.Sound.snd_emitter.SoundFX_LowpassFast
import neo.framework.File_h.fsOrigin_t
import org.lwjgl.stb.STBVorbisInfo
import neo.Tools.Compilers.AAS.Brush
import neo.Tools.Compilers.AAS.Brush.idBrushSide
import neo.Tools.Compilers.AAS.Brush.idBrush
import neo.idlib.containers.PlaneSet.idPlaneSet
import neo.Tools.Compilers.AAS.Brush.idBrushList
import neo.Tools.Compilers.AAS.Brush.idBrushMap
import neo.Tools.Compilers.AAS.AASBuild.Allowance
import neo.Tools.Compilers.AAS.AASFile.idReachability_Special
import neo.Tools.Compilers.AAS.AASFile_local.idAASFileLocal
import neo.Tools.Compilers.AAS.AASBuild_ledge.idLedge
import neo.Tools.Compilers.AAS.AASBuild_local.aasProcNode_s
import neo.Tools.Compilers.AAS.BrushBSP.idBrushBSP
import neo.Tools.Compilers.AAS.AASReach.idAASReach
import neo.Tools.Compilers.AAS.AASCluster.idAASCluster
import neo.Tools.Compilers.AAS.AASBuild.MergeAllowed
import neo.Tools.Compilers.AAS.AASBuild.ExpandedChopAllowed
import neo.Tools.Compilers.AAS.AASBuild.ExpandedMergeAllowed
import neo.Tools.Compilers.AAS.AASBuild
import neo.Tools.Compilers.AAS.BrushBSP.idBrushBSPNode
import neo.Tools.Compilers.AAS.BrushBSP.idBrushBSPPortal
import neo.Tools.Compilers.AAS.AASBuild.idAASBuild
import neo.Tools.Compilers.AAS.BrushBSP
import neo.Tools.Compilers.AAS.AASBuild_ledge
import neo.Tools.Compilers.AAS.AASBuild_File
import neo.Tools.Compilers.AAS.AASBuild_File.sizeEstimate_s
import neo.Tools.Compilers.AAS.AASBuild.RunAAS_f
import neo.Tools.Compilers.AAS.AASBuild.RunAASDir_f
import neo.Tools.Compilers.AAS.AASBuild.RunReach_f
import neo.Tools.Compilers.AAS.AASFile.idReachability_Fly
import neo.Tools.Compilers.AAS.AASReach
import neo.Tools.Compilers.AAS.AASFile.idReachability_Swim
import neo.Tools.Compilers.AAS.AASFile.idReachability_BarrierJump
import neo.Tools.Compilers.AAS.AASFile.idReachability_WaterJump
import neo.Tools.Compilers.AAS.AASFile.idReachability_WalkOffLedge
import neo.idlib.containers.VectorSet.idVectorSet
import neo.Tools.Compilers.AAS.BrushBSP.idBrushBSP.splitterStats_s
import neo.Tools.Compilers.AAS.AASFile_local.aasTraceStack_s
import neo.Tools.Compilers.AAS.AASFile_local
import neo.Tools.Compilers.AAS.AASFileManager.idAASFileManagerLocal
import neo.Tools.Compilers.DMap.dmap.uBrush_t
import neo.Tools.Compilers.DMap.dmap.uEntity_t
import neo.Tools.Compilers.DMap.map
import neo.Tools.Compilers.DMap.dmap
import neo.Tools.Compilers.DMap.dmap.side_s
import neo.Renderer.Material.materialCoverage_t
import neo.Tools.Compilers.DMap.dmap.primitive_s
import neo.Tools.Compilers.DMap.ubrush
import neo.Tools.Compilers.DMap.dmap.mapTri_s
import neo.Tools.Compilers.DMap.tritools
import neo.Tools.Compilers.DMap.dmap.mapLight_t
import neo.Renderer.tr_lightrun
import neo.Tools.Compilers.DMap.dmap.optimizeGroup_s
import neo.Tools.Compilers.DMap.facebsp
import neo.Tools.Compilers.DMap.dmap.uArea_t
import neo.Tools.Compilers.DMap.dmap.dmapGlobals_t
import neo.Tools.Compilers.DMap.dmap.bspface_s
import neo.Tools.Compilers.DMap.portals
import neo.Tools.Compilers.DMap.leakfile
import neo.Tools.Compilers.DMap.usurface
import neo.Tools.Compilers.DMap.optimize
import neo.Tools.Compilers.DMap.tritjunction
import neo.Tools.Compilers.DMap.dmap.shadowOptLevel_t
import neo.Tools.Compilers.DMap.output
import neo.Tools.Compilers.DMap.dmap.bspbrush_s
import neo.Tools.Compilers.DMap.dmap.tree_s
import neo.Tools.Compilers.DMap.tritjunction.hashVert_s
import neo.Tools.Compilers.DMap.optimize.optVertex_s
import neo.Tools.Compilers.DMap.dmap.mesh_t
import neo.Tools.Compilers.DMap.dmap.parseMesh_s
import neo.Tools.Compilers.DMap.dmap.textureVectors_t
import neo.Tools.Compilers.DMap.dmap.drawSurfRef_s
import neo.Tools.Compilers.DMap.dmap.node_s
import neo.Tools.Compilers.DMap.dmap.uPortal_s
import neo.Renderer.tr_local.idRenderLightLocal
import neo.Tools.Compilers.DMap.dmap.Dmap_f
import org.lwjgl.opengl.GL11
import neo.Renderer.tr_backend
import neo.Renderer.qgl
import neo.Tools.Compilers.DMap.glfile
import neo.Renderer.tr_trisurf
import neo.Tools.Compilers.DMap.portals.interAreaPortal_t
import neo.Tools.Compilers.DMap.gldraw
import neo.Tools.Compilers.DMap.optimize.optEdge_s
import neo.Tools.Compilers.DMap.optimize.originalEdges_t
import neo.Tools.Compilers.DMap.optimize.optIsland_t
import neo.Tools.Compilers.DMap.optimize.edgeLength_t
import neo.Tools.Compilers.DMap.optimize.LengthSort
import neo.Tools.Compilers.DMap.optimize.optTri_s
import neo.Tools.Compilers.DMap.optimize.edgeCrossing_s
import neo.Tools.Compilers.DMap.shadowopt3
import neo.Tools.Compilers.DMap.shadowopt3.shadowOptEdge_s
import neo.Tools.Compilers.DMap.shadowopt3.silQuad_s
import neo.Tools.Compilers.DMap.shadowopt3.shadowTri_t
import neo.Renderer.tr_local.optimizedShadow_t
import neo.Tools.Compilers.DMap.shadowopt3.silPlane_t
import neo.Renderer.tr_stencilshadow
import neo.Renderer.tr_local.idRenderEntityLocal
import neo.Renderer.Interaction.srfCullInfo_t
import neo.Renderer.tr_stencilshadow.shadowGen_t
import neo.Renderer.Interaction
import neo.Tools.Compilers.DMap.optimize_gcc
import neo.Tools.Compilers.RoqVQ.Roq.roq
import neo.Tools.Compilers.RoqVQ.Codec.codec
import neo.Tools.Compilers.RoqVQ.NSBitmapImageRep
import neo.Tools.Compilers.RoqVQ.RoqParam.roqParam
import neo.Tools.Compilers.RoqVQ.QuadDefs
import neo.Tools.Compilers.RoqVQ.QuadDefs.quadcel
import neo.Tools.Compilers.RoqVQ.Roq.j_compress_ptr
import neo.Tools.Compilers.RoqVQ.Roq
import neo.Tools.Compilers.RoqVQ.Roq.RoQFileEncode_f
import neo.Tools.Compilers.RoqVQ.Codec
import neo.Tools.Compilers.RoqVQ.GDefs
import neo.Tools.Compilers.RoqVQ.RoqParam
import neo.Tools.Compilers.RenderBump.renderbump
import neo.Tools.Compilers.RenderBump.renderbump.triHash_t
import neo.Tools.Compilers.RenderBump.renderbump.binLink_t
import neo.Tools.Compilers.RenderBump.renderbump.triLink_t
import neo.Tools.Compilers.RenderBump.renderbump.renderBump_t
import neo.Renderer.Image_process
import neo.Tools.Compilers.RenderBump.renderbump.RenderBump_f
import neo.Tools.Compilers.RenderBump.renderbump.RenderBumpFlat_f
import org.lwjgl.opengl.ARBMultitexture
import org.lwjgl.opengl.ARBVertexBufferObject
import org.lwjgl.opengl.GL12
import org.lwjgl.opengl.ARBImaging
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.ARBTextureCompression
import org.lwjgl.opengl.ARBVertexShader
import org.lwjgl.opengl.ARBVertexProgram
import org.lwjgl.opengl.EXTDepthBoundsTest
import java.nio.DoubleBuffer
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL43
import neo.Renderer.Image.idImageManager
import neo.Renderer.Image.ddsFilePixelFormat_t
import neo.Renderer.Image.ddsFileHeader_t
import neo.Renderer.Image.idImage
import neo.framework.FileSystem_h.backgroundDownload_s
import neo.Renderer.Image.cubeFiles_t
import neo.Renderer.Image.textureDepth_t
import neo.Renderer.Material.textureFilter_t
import neo.Renderer.Image.GeneratorFunction
import neo.Renderer.Material.textureRepeat_t
import neo.Renderer.Image.textureType_t
import neo.framework.FileSystem_h.dlType_t
import neo.Renderer.tr_local.tmu_t
import org.lwjgl.opengl.GL31
import neo.Renderer.Image_load
import org.lwjgl.opengl.EXTTextureCompressionS3TC
import org.lwjgl.opengl.EXTTextureFilterAnisotropic
import org.lwjgl.opengl.GL14
import org.lwjgl.opengl.EXTBGRA
import neo.Renderer.Image_program
import neo.Renderer.Image_init.R_DefaultImage
import neo.Renderer.Image_init.R_WhiteImage
import neo.Renderer.Image_init.R_BlackImage
import neo.Renderer.Image_init.R_BorderClampImage
import neo.Renderer.Image_init.R_FlatNormalImage
import neo.Renderer.Image_init.R_AmbientNormalImage
import neo.Renderer.Image_init.R_SpecularTableImage
import neo.Renderer.Image_init.R_Specular2DTableImage
import neo.Renderer.Image_init.R_RampImage
import neo.Renderer.Image_init.R_AlphaNotchImage
import neo.Renderer.Image_init.R_FogImage
import neo.Renderer.Image_init.R_FogEnterImage
import neo.Renderer.Image_init.makeNormalizeVectorCubeMap
import neo.Renderer.Image_init.R_CreateNoFalloffImage
import neo.Renderer.Image_init.R_QuadraticImage
import neo.Renderer.Image_init.R_RGBA8Image
import neo.Renderer.Image_init.R_ReloadImages_f
import neo.Renderer.Image_init.R_ListImages_f
import neo.Renderer.Image_init.R_CombineCubeImages_f
import org.lwjgl.opengl.EXTSharedTexturePalette
import neo.Renderer.Image.idImageManager.filterName_t
import neo.Renderer.Image_init
import neo.Renderer.Model.silEdge_t
import neo.Renderer.Model.lightingCache_s
import neo.Renderer.VertexCache.vertCache_s
import neo.Renderer.tr_local.viewDef_s
import neo.Renderer.tr_font
import neo.Renderer.tr_font.poor
import neo.Renderer.tr_local.idScreenRect
import neo.Renderer.tr_main
import neo.Renderer.tr_local.frameData_t
import neo.Renderer.tr_local.frameMemoryBlock_s
import neo.Renderer.tr_local.viewEntity_s
import neo.Renderer.tr_local.viewLight_s
import neo.Renderer.tr_local.drawSurf_s
import neo.Renderer.tr_main.R_QsortSurfaces
import neo.Renderer.tr_light
import neo.Renderer.tr_subview
import neo.Renderer.tr_render
import neo.Renderer.draw_arb.RB_ARB_DrawThreeTextureInteraction
import neo.Renderer.draw_arb.RB_ARB_DrawInteraction
import neo.Renderer.draw_common
import neo.Renderer.draw_arb
import neo.Renderer.tr_render.DrawInteraction
import neo.Renderer.tr_local.drawInteraction_t
import neo.Renderer.VertexCache
import org.lwjgl.opengl.ARBTextureEnvCombine
import org.lwjgl.opengl.ARBTextureEnvDot3
import neo.Renderer.Material.stageVertexColor_t
import neo.Renderer.GuiModel.guiModelSurface_t
import neo.Renderer.GuiModel.idGuiModel
import neo.Renderer.Material.expOpType_t
import neo.Renderer.Material.expOp_t
import neo.Renderer.Material.colorStage_t
import neo.Renderer.Cinematic.idCinematic
import neo.Renderer.Material.dynamicidImage_t
import neo.Renderer.Material.texgen_t
import neo.Renderer.Material.textureStage_t
import neo.Renderer.MegaTexture.idMegaTexture
import neo.Renderer.Material.stageLighting_t
import neo.Renderer.Material.newShaderStage_t
import neo.Renderer.Material.decalInfo_t
import neo.Renderer.Material.deform_t
import neo.Renderer.Material.mtrParsingData_s
import neo.Renderer.Material.expRegister_t
import neo.Renderer.draw_arb2
import org.lwjgl.opengl.ARBFragmentProgram
import neo.Renderer.Material.idMaterial.infoParm_t
import neo.Renderer.Model_ma.ma_t
import neo.Renderer.Model_ma
import neo.Renderer.Model_ma.maNodeHeader_t
import neo.Renderer.Model_ma.maAttribHeader_t
import neo.Renderer.Model_ma.maTransform_s
import neo.Renderer.Model_ma.maMesh_t
import neo.Renderer.Model_ma.maFace_t
import neo.Renderer.Model_ma.maObject_t
import neo.Renderer.Model_ma.maFileNode_t
import neo.Renderer.Model_ma.maMaterialNode_s
import neo.Renderer.Model_ma.maMaterial_t
import neo.Renderer.Model_ma.maModel_s
import neo.Renderer.tr_local.backEndName_t
import neo.Renderer.ModelOverlay.idRenderModelOverlay
import neo.Renderer.tr_deform
import neo.Renderer.tr_guisurf
import neo.Renderer.ModelDecal.idRenderModelDecal
import neo.Renderer.Interaction.idInteraction
import neo.Renderer.tr_local.areaReference_s
import neo.Renderer.tr_local.backEndState_t
import neo.Renderer.RenderSystem.glconfig_s
import neo.Renderer.tr_local.idRenderSystemLocal
import neo.Renderer.RenderWorld_local.portalArea_s
import neo.Renderer.tr_local.idRenderLight
import neo.Renderer.RenderWorld_local.doublePortal_s
import neo.Renderer.tr_local.shadowFrustum_t
import neo.Renderer.RenderWorld_local.idRenderWorldLocal
import neo.Renderer.tr_local.idRenderEntity
import neo.Renderer.tr_local.renderCommand_t
import neo.Renderer.tr_local.emptyCommand_t
import neo.Renderer.tr_local.glstate_t
import neo.Renderer.tr_local.backEndCounters_t
import neo.Renderer.tr_local.drawSurfsCommand_t
import neo.Renderer.tr_local.performanceCounters_t
import neo.Renderer.tr_local.renderCrop_t
import neo.Renderer.tr_rendertools
import neo.Renderer.tr_local.demoCommand_t
import neo.Renderer.tr_local.setBufferCommand_t
import neo.Renderer.MegaTexture
import neo.Renderer.tr_local.copyRenderCommand_t
import neo.Renderer.tr_local.localTrace_t
import neo.Renderer.tr_trace
import neo.Renderer.Cinematic
import neo.Renderer.Cinematic.cinStatus_t
import neo.Renderer.Cinematic.idCinematicLocal
import neo.Renderer.draw_arb2.progDef_t
import neo.Renderer.tr_local.program_t
import neo.Renderer.draw_arb2.RB_ARB2_DrawInteraction
import neo.Renderer.tr_local.programParameter_t
import neo.Renderer.draw_arb2.R_ReloadARBPrograms_f
import neo.Renderer.Model_ase.ase_t
import neo.Renderer.Model_ase.aseModel_s
import neo.Renderer.Model_ase
import neo.Renderer.Model_ase.aseObject_t
import neo.Renderer.Model_ase.aseMesh_t
import neo.Renderer.Model_ase.aseMaterial_t
import neo.Renderer.Model_ase.ASE
import neo.Renderer.Model_ase.ASE_KeyGEOMOBJECT
import neo.Renderer.Model_ase.ASE_KeyGROUP
import neo.Renderer.Model_ase.ASE_KeyMATERIAL_LIST
import neo.Renderer.Model_ase.aseFace_t
import neo.Renderer.Model_ase.ASE_KeyMAP_DIFFUSE
import neo.Renderer.Model_ase.ASE_KeyMATERIAL
import neo.Renderer.Model_ase.ASE_KeyNODE_TM
import neo.Renderer.Model_ase.ASE_KeyMESH_VERTEX_LIST
import neo.Renderer.Model_ase.ASE_KeyMESH_FACE_LIST
import neo.Renderer.Model_ase.ASE_KeyTFACE_LIST
import neo.Renderer.Model_ase.ASE_KeyCFACE_LIST
import neo.Renderer.Model_ase.ASE_KeyMESH_TVERTLIST
import neo.Renderer.Model_ase.ASE_KeyMESH_CVERTLIST
import neo.Renderer.Model_ase.ASE_KeyMESH_NORMALS
import neo.Renderer.Model_ase.ASE_KeyMESH
import neo.Renderer.Model_ase.ASE_KeyMESH_ANIMATION
import neo.Renderer.Model_lwo.lwClip
import neo.Renderer.Model_lwo.lwPlugin
import neo.Renderer.Model_lwo
import neo.Renderer.Model_lwo.lwFreeClip
import neo.Renderer.Model_lwo.lwEnvelope
import neo.Renderer.Model_lwo.lwKey
import neo.Renderer.Model_lwo.compare_keys
import neo.Renderer.Model_lwo.lwFreeEnvelope
import neo.Renderer.Model_lwo.LW
import neo.Renderer.Model_lwo.lwNode
import neo.Renderer.Model_lwo.lwObject
import neo.Renderer.Model_lwo.lwLayer
import neo.Renderer.Model_lwo.lwVMap
import neo.Renderer.Model_lwo.lwSurface
import neo.Renderer.Model_lwo.lwTexture
import neo.Renderer.Model_lwo.lwFreeSurface
import neo.Renderer.Model_lwo.lwPolygonList
import neo.Renderer.Model_lwo.lwPolygon
import neo.Renderer.Model_lwo.lwPointList
import neo.Renderer.Model_lwo.lwPoint
import neo.Renderer.Model_lwo.lwPolVert
import neo.Renderer.Model_lwo.lwTagList
import neo.Renderer.Model_lwo.lwTMap
import neo.Renderer.Model_lwo.lwGradKey
import neo.Renderer.Model_lwo.lwFreeTexture
import neo.Renderer.Model_lwo.lwFreePlugin
import neo.Renderer.Model_lwo.compare_textures
import neo.Renderer.Model_lwo.compare_shaders
import neo.Renderer.Model_lwo.lwFreeVMap
import neo.Renderer.Model_lwo.lwVMapPt
import neo.Renderer.Model_lwo.lwEParam
import neo.Renderer.Model_lwo.lwClipAnim
import neo.Renderer.Model_lwo.lwClipCycle
import neo.Renderer.Model_lwo.lwClipSeq
import neo.Renderer.Model_lwo.lwClipStill
import neo.Renderer.Model_lwo.lwClipXRef
import neo.Renderer.Model_lwo.lwVParam
import neo.Renderer.Model_lwo.lwGradient
import neo.Renderer.Model_lwo.lwImageMap
import neo.Renderer.Model_lwo.lwProcedural
import neo.Renderer.Model_lwo.lwTParam
import neo.Renderer.Model_lwo.lwCParam
import neo.Renderer.Model_lwo.lwLine
import neo.Renderer.Model_lwo.lwRMap
import neo.Renderer.Model_lwo.lwFree
import neo.Renderer.Model_lwo.lwFreeLayer
import neo.Renderer.Model_md3
import neo.Renderer.Model_md3.md3XyzNormal_t
import neo.Renderer.Model_md3.md3Shader_t
import neo.Renderer.Model_md3.md3Triangle_t
import neo.Renderer.Model_md3.md3St_t
import neo.Renderer.Model_md3.md3Frame_s
import neo.Renderer.Model_md3.md3Surface_s
import neo.Renderer.Model_md3.md3Tag_s
import neo.Renderer.Model_local.idRenderModelStatic
import neo.Renderer.Model_md3.md3Header_s
import neo.Renderer.tr_local.deformInfo_s
import neo.Renderer.Model_md5.vertexWeight_s
import neo.Renderer.Model_md5
import neo.Renderer.Model_md5.idMD5Mesh
import neo.Renderer.Model_md5.idRenderModelMD5
import neo.Renderer.Model_prt
import neo.Renderer.tr_deform.eyeIsland_t
import neo.Renderer.tr_render.triFunc
import neo.Renderer.tr_render.RB_T_RenderTriangleSurface
import neo.Renderer.Image_init.imageClassificate_t
import neo.Renderer.Image_init.IMAGE_CLASSIFICATION
import neo.Renderer.Image_init.sortedImage_t
import neo.Renderer.Image_init.R_QsortImageSizes
import neo.Renderer.Image_init.R_AlphaRampImage
import neo.Renderer.Image_init.R_RGB8Image
import neo.Renderer.Model_beam
import neo.Renderer.ModelDecal.decalProjectionInfo_s
import neo.Renderer.ModelDecal
import java.util.NoSuchElementException
import neo.Renderer.tr_guisurf.R_ReloadGuis_f
import neo.Renderer.tr_guisurf.R_ListGuis_f
import neo.Renderer.tr_subview.orientation_t
import neo.Renderer.tr_trisurf.SilEdgeSort
import neo.Renderer.tr_trisurf.faceTangents_t
import neo.Renderer.tr_trisurf.tangentVert_t
import neo.Renderer.tr_trisurf.indexSort_t
import neo.Renderer.tr_trisurf.IndexSort
import neo.Renderer.tr_trisurf.R_ShowTriSurfMemory_f
import neo.Renderer.draw_common.RB_T_FillDepthBuffer
import neo.Renderer.draw_common.RB_T_Shadow
import neo.Renderer.draw_common.RB_T_BlendLight
import neo.Renderer.draw_common.RB_T_BasicFog
import neo.Renderer.Image_files.BMPHeader_t
import neo.Renderer.Image_files.pcx_t
import neo.Renderer.Image_files.TargaHeader
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.awt.image.DataBufferByte
import neo.Renderer.Interaction.surfaceInteraction_t
import neo.Renderer.Interaction.areaNumRef_s
import neo.Renderer.Interaction.idInteraction.frustumStates
import neo.Renderer.tr_shadowbounds
import neo.Renderer.Interaction.R_ShowInteractionMemory_f
import neo.Renderer.MegaTexture.fillColors
import neo.Renderer.MegaTexture.idTextureTile
import neo.Renderer.MegaTexture.megaTextureHeader_t
import neo.Renderer.MegaTexture.idTextureLevel
import neo.Renderer.MegaTexture.R_EmptyLevelImage
import neo.Renderer.MegaTexture._TargaHeader
import neo.Renderer.MegaTexture.idMegaTexture.MakeMegaTexture_f
import neo.Renderer.Model_local
import neo.Renderer.Model_local.idRenderModelStatic.matchVert_s
import neo.idlib.containers.VectorSet.idVectorSubset
import neo.TempDump.Atomics.renderEntityShadow
import neo.TempDump.Atomics.renderLightShadow
import neo.TempDump.Atomics.renderViewShadow
import neo.Renderer.RenderWorld.R_ListRenderLightDefs_f
import neo.Renderer.RenderWorld.R_ListRenderEntityDefs_f
import neo.Renderer.tr_polytope
import neo.Renderer.RenderWorld_local.portal_s
import neo.Renderer.tr_lightrun.R_ModulateLights_f
import neo.Renderer.tr_lightrun.R_RegenerateWorld_f
import neo.Renderer.VertexCache.idVertexCache
import neo.Renderer.VertexCache.vertBlockTag_t
import neo.Renderer.VertexCache.R_ListVertexCache_f
import neo.idlib.geometry.DrawVert
import neo.Renderer.Model_liquid
import neo.Renderer.Model_sprite
import neo.Renderer.ModelManager.idRenderModelManagerLocal
import neo.Renderer.ModelManager.idRenderModelManagerLocal.ListModels_f
import neo.Renderer.ModelManager.idRenderModelManagerLocal.PrintModel_f
import neo.framework.CmdSystem.idCmdSystem.ArgCompletion_ModelName
import neo.Renderer.ModelManager.idRenderModelManagerLocal.ReloadModels_f
import neo.Renderer.ModelManager.idRenderModelManagerLocal.TouchModel_f
import neo.Renderer.Model_beam.idRenderModelBeam
import neo.Renderer.Model_sprite.idRenderModelSprite
import neo.Renderer.Model_md3.idRenderModelMD3
import neo.Renderer.Model_prt.idRenderModelPrt
import neo.Renderer.ModelOverlay.overlayVertex_s
import neo.Renderer.ModelOverlay.overlaySurface_s
import neo.Renderer.ModelOverlay.overlayMaterial_s
import neo.Renderer.ModelOverlay
import neo.Renderer.tr_rendertools.debugLine_s
import neo.Renderer.tr_rendertools.debugPolygon_s
import neo.Renderer.tr_rendertools.debugText_s
import neo.Renderer.simplex
import neo.Renderer.tr_turboshadow
import neo.Renderer.tr_orderIndexes
import neo.Renderer.tr_orderIndexes.vertRef_s
import neo.Renderer.tr_shadowbounds.polyhedron
import neo.Renderer.tr_shadowbounds.poly
import neo.Renderer.tr_shadowbounds.MyArray
import neo.Renderer.tr_shadowbounds.edge
import neo.Renderer.tr_stencilshadow.indexRef_t
import neo.Renderer.RenderSystem_init.vidmode_s
import neo.Renderer.RenderSystem_init.R_SizeUp_f
import neo.Renderer.RenderSystem_init.R_SizeDown_f
import neo.Renderer.RenderSystem_init.R_TouchGui_f
import neo.Renderer.RenderSystem_init.R_ScreenShot_f
import neo.Renderer.RenderSystem_init.R_EnvShot_f
import neo.Renderer.RenderSystem_init.R_MakeAmbientMap_f
import neo.Renderer.RenderSystem_init.R_Benchmark_f
import neo.Renderer.RenderSystem_init.GfxInfo_f
import neo.Renderer.RenderSystem_init.R_TestImage_f
import neo.framework.CmdSystem.idCmdSystem.ArgCompletion_ImageName
import neo.Renderer.RenderSystem_init.R_TestVideo_f
import neo.framework.CmdSystem.idCmdSystem.ArgCompletion_VideoName
import neo.Renderer.RenderSystem_init.R_ReportSurfaceAreas_f
import neo.Renderer.RenderSystem_init.R_ReportImageDuplication_f
import neo.Renderer.RenderSystem_init.R_VidRestart_f
import neo.Renderer.RenderSystem_init.R_ListModes_f
import neo.Renderer.RenderSystem_init.R_ReloadSurface_f
import org.lwjgl.opengl.EXTStencilWrap
import neo.Renderer.RenderSystem_init.R_QsortSurfaceAreas
import neo.Renderer.RenderWorld_local.areaNode_t
import neo.Renderer.RenderWorld_local
import neo.Renderer.RenderWorld_portals.portalStack_s
import neo.Renderer.RenderWorld_portals
import neo.Renderer.RenderWorld_demo.demoHeader_t
import neo.framework.Async.MsgChannel
import neo.framework.Compressor.idCompressor
import neo.framework.Async.MsgChannel.idMsgQueue
import neo.sys.sys_public.idPort
import neo.framework.File_h.idFile_BitMsg
import neo.framework.Async.ServerScan.idServerScan
import neo.framework.Async.AsyncNetwork
import neo.framework.Async.ServerScan.networkServer_t
import neo.framework.Async.ServerScan.inServer_t
import neo.framework.Async.ServerScan.serverSort_t
import neo.framework.Async.ServerScan.scan_state_t
import neo.framework.Async.ServerScan
import neo.framework.Async.ServerScan.idServerScan.Cmp
import neo.framework.Async.MsgChannel.idMsgChannel
import neo.framework.Async.AsyncClient.clientState_t
import neo.framework.Async.AsyncClient.pakDlEntry_t
import neo.framework.FileSystem_h.dlMime_t
import neo.framework.Async.AsyncClient.clientUpdateState_t
import neo.framework.Async.AsyncClient.idAsyncClient.HandleGuiCommand
import neo.framework.Session.msgBoxType_t
import neo.framework.Async.AsyncNetwork.CLIENT_RELIABLE
import neo.framework.Async.AsyncClient
import neo.framework.Async.AsyncNetwork.CLIENT_UNRELIABLE
import neo.framework.Async.AsyncNetwork.SERVER_UNRELIABLE
import neo.framework.Async.AsyncNetwork.SERVER_RELIABLE
import neo.framework.Async.AsyncNetwork.SERVER_PRINT
import neo.framework.Async.AsyncClient.authKeyMsg_t
import neo.framework.Async.AsyncClient.authBadKeyStatus_t
import neo.framework.FileSystem_h.fsPureReply_t
import neo.framework.File_h.idFile_Permanent
import neo.framework.FileSystem_h.dlStatus_t
import neo.framework.Async.AsyncNetwork.SERVER_DL
import neo.framework.Async.AsyncNetwork.SERVER_PAK
import neo.framework.Session.HandleGuiCommand_t
import neo.framework.Async.AsyncServer.authReply_t
import neo.framework.Async.AsyncServer.authReplyMsg_t
import neo.framework.Async.AsyncServer.authState_t
import neo.framework.Async.AsyncServer.serverClientState_t
import neo.framework.Async.AsyncServer.idAsyncServer
import neo.framework.Async.AsyncServer.challenge_s
import neo.framework.Async.AsyncServer
import neo.framework.Async.AsyncServer.serverClient_s
import neo.framework.FileSystem_h.findFile_t
import neo.framework.Async.AsyncServer.RConRedirect
import neo.framework.Async.AsyncClient.idAsyncClient
import neo.framework.Async.AsyncNetwork.master_s
import neo.framework.Async.AsyncNetwork.idAsyncNetwork.SpawnServer_f
import neo.framework.Async.AsyncNetwork.idAsyncNetwork.Connect_f
import neo.framework.Async.AsyncNetwork.idAsyncNetwork.Reconnect_f
import neo.framework.Async.AsyncNetwork.idAsyncNetwork.GetServerInfo_f
import neo.framework.Async.AsyncNetwork.idAsyncNetwork.GetLANServers_f
import neo.framework.Async.AsyncNetwork.idAsyncNetwork.ListServers_f
import neo.framework.Async.AsyncNetwork.idAsyncNetwork.RemoteConsole_f
import neo.framework.Async.AsyncNetwork.idAsyncNetwork.Heartbeat_f
import neo.framework.Async.AsyncNetwork.idAsyncNetwork.Kick_f
import neo.framework.Async.AsyncNetwork.idAsyncNetwork.CheckNewVersion_f
import neo.framework.Async.AsyncNetwork.idAsyncNetwork.UpdateUI_f
import neo.framework.UsercmdGen.inhibit_t
import neo.framework.Unzip.tm_unz
import neo.framework.Common.version_s
import kotlin.jvm.Volatile
import neo.framework.Common.idCommonLocal
import neo.framework.Common.ListHash
import neo.idlib.LangDict.idLangDict
import neo.framework.Common.idCommonLocal.asyncStats_t
import neo.framework.Common.errorParm_t
import neo.framework.Common.Com_ExecMachineSpec_f
import neo.framework.Common.Com_Error_f
import neo.framework.Common.Com_Crash_f
import neo.framework.Common.Com_Freeze_f
import neo.framework.Common.Com_Quit_f
import neo.framework.Common.Com_WriteConfig_f
import neo.framework.Common.Com_ReloadEngine_f
import neo.framework.Common.Com_SetMachineSpec_f
import neo.framework.Common.Com_Editor_f
import neo.framework.Common.Com_EditLights_f
import neo.framework.Common.Com_EditSounds_f
import neo.framework.Common.Com_EditDecls_f
import neo.framework.Common.Com_EditAFs_f
import neo.framework.Common.Com_EditParticles_f
import neo.framework.Common.Com_EditScripts_f
import neo.framework.Common.Com_EditGUIs_f
import neo.framework.Common.Com_EditPDAs_f
import neo.framework.Common.Com_ScriptDebugger_f
import neo.framework.Common.Com_MaterialEditor_f
import neo.framework.Common.Com_LocalizeGuis_f
import neo.framework.Common.Com_LocalizeMaps_f
import neo.framework.Common.Com_ReloadLanguage_f
import neo.framework.Common.Com_LocalizeGuiParmsTest_f
import neo.framework.Common.Com_LocalizeMapsTest_f
import neo.framework.Common.Com_StartBuild_f
import neo.framework.Common.Com_FinishBuild_f
import neo.framework.Common.Com_Help_f
import neo.framework.DeclAF.idAFVector
import neo.framework.DeclAF.idAFVector.type
import neo.framework.File_h
import neo.idlib.containers.CLong
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import neo.framework.File_h.idFile_InZip
import neo.framework.Console.idConsoleLocal
import neo.framework.Console.idConsole
import neo.framework.Console.Con_Clear_f
import neo.framework.Console.Con_Dump_f
import neo.framework.Session.Session_RescanSI_f
import neo.framework.Session.Session_Map_f
import neo.framework.Session.Session_DevMap_f
import neo.framework.Session.Session_TestMap_f
import neo.framework.Session.Sess_WritePrecache_f
import neo.framework.Session.Session_PromptKey_f
import neo.framework.Session.Session_DemoShot_f
import neo.framework.Session.Session_RecordDemo_f
import neo.framework.Session.Session_CompressDemo_f
import neo.framework.Session.Session_StopRecordingDemo_f
import neo.framework.Session.Session_PlayDemo_f
import neo.framework.Session.Session_TimeDemo_f
import neo.framework.Session_local.timeDemo_t
import neo.framework.Session.Session_TimeDemoQuit_f
import neo.framework.Session.Session_AVIDemo_f
import neo.framework.Session.Session_AVIGame_f
import neo.framework.Session.Session_AVICmdDemo_f
import neo.framework.Session.Session_WriteCmdDemo_f
import neo.framework.Session.Session_PlayCmdDemo_f
import neo.framework.Session.Session_TimeCmdDemo_f
import neo.framework.Session.Session_Disconnect_f
import neo.framework.Session.Session_EndOfDemo_f
import neo.framework.Session.Session_ExitCmdDemo_f
import neo.framework.Session.Session_TestGUI_f
import neo.framework.Session.LoadGame_f
import neo.framework.Session.SaveGame_f
import neo.framework.Session.TakeViewNotes_f
import neo.framework.Session.TakeViewNotes2_f
import neo.framework.Session.Session_Hitch_f
import neo.framework.Session_local.idSessionLocal
import neo.framework.Session.idSession
import neo.framework.DeclSkin.skinMapping_t
import neo.framework.DemoFile
import neo.framework.KeyInput.keyname_t
import neo.framework.KeyInput.idKey
import neo.framework.KeyInput.Key_Bind_f
import neo.framework.KeyInput.idKeyInput.ArgCompletion_KeyName
import neo.framework.KeyInput.Key_BindUnBindTwo_f
import neo.framework.KeyInput.Key_Unbind_f
import neo.framework.KeyInput.Key_Unbindall_f
import neo.framework.KeyInput.Key_ListBinds_f
import neo.framework.CmdSystem.idCmdSystemLocal
import neo.framework.CmdSystem.idCmdSystem.ArgCompletion_Boolean
import neo.framework.CmdSystem.idCmdSystem.ArgCompletion_FileName
import neo.framework.CmdSystem.idCmdSystem.ArgCompletion_ConfigName
import neo.framework.CmdSystem.idCmdSystem.ArgCompletion_SaveGame
import neo.framework.CmdSystem.idCmdSystem.ArgCompletion_DemoName
import neo.framework.CmdSystem.idCmdSystemLocal.SystemList_f
import neo.framework.CmdSystem.idCmdSystemLocal.RendererList_f
import neo.framework.CmdSystem.idCmdSystemLocal.SoundList_f
import neo.framework.CmdSystem.idCmdSystemLocal.GameList_f
import neo.framework.CmdSystem.idCmdSystemLocal.ToolList_f
import neo.framework.CmdSystem.idCmdSystemLocal.Exec_f
import neo.framework.CmdSystem.idCmdSystemLocal.Vstr_f
import neo.framework.CmdSystem.idCmdSystemLocal.Echo_f
import neo.framework.CmdSystem.idCmdSystemLocal.Parse_f
import neo.framework.CmdSystem.idCmdSystemLocal.Wait_f
import neo.framework.EditField.autoComplete_s
import neo.framework.EditField
import neo.framework.EditField.FindMatches
import neo.framework.EditField.FindIndexMatch
import neo.framework.EditField.PrintMatches
import neo.framework.EditField.PrintCvarMatches
import neo.framework.EventLoop.idEventLoop
import neo.framework.Compressor
import neo.framework.Compressor.idCompressor_None
import neo.framework.Compressor.idCompressor_BitStream
import neo.framework.Compressor.idCompressor_RunLength
import neo.framework.Compressor.idCompressor_RunLength_ZeroBased
import neo.framework.Compressor.idCompressor_Huffman
import neo.framework.Compressor.idCompressor_Arithmetic
import neo.framework.Compressor.idCompressor_LZSS
import neo.framework.Compressor.idCompressor_LZSS_WordAligned
import neo.framework.Compressor.idCompressor_LZW
import neo.framework.Compressor.huffmanNode_t
import neo.framework.Compressor.nodetype
import neo.framework.Compressor.idCompressor_Arithmetic.acProbs_t
import neo.framework.Compressor.idCompressor_Arithmetic.acSymbol_t
import neo.framework.Compressor.idCompressor_Arithmetic.acProbs_s
import neo.framework.Compressor.idCompressor_Arithmetic.acSymbol_s
import neo.framework.Compressor.idCompressor_LZW.dictionary
import neo.framework.CVarSystem.idCVarSystemLocal
import neo.framework.CVarSystem.idCVarSystemLocal.Toggle_f
import neo.framework.CVarSystem.idCVarSystemLocal.Set_f
import neo.framework.CVarSystem.idCVarSystemLocal.SetS_f
import neo.framework.CVarSystem.idCVarSystemLocal.SetU_f
import neo.framework.CVarSystem.idCVarSystemLocal.SetT_f
import neo.framework.CVarSystem.idCVarSystemLocal.SetA_f
import neo.framework.CVarSystem.idCVarSystemLocal.Reset_f
import neo.framework.CVarSystem.idCVarSystemLocal.Restart_f
import neo.framework.CVarSystem.idCVarSystemLocal.show
import neo.framework.FileSystem_h.idFileSystemLocal
import neo.framework.UsercmdGen.idUsercmdGenLocal
import neo.framework.UsercmdGen.userCmdString_t
import neo.framework.UsercmdGen.usercmdButton_t
import neo.framework.UsercmdGen.idUsercmdGen
import neo.framework.UsercmdGen.idUsercmdGenLocal.KeyboardCallback
import neo.framework.UsercmdGen.idUsercmdGenLocal.MouseButtonCallback
import neo.framework.UsercmdGen.idUsercmdGenLocal.MouseCursorCallback
import neo.framework.UsercmdGen.idUsercmdGenLocal.MouseScrollCallback
import neo.sys.sys_public.joystickAxis_t
import neo.framework.UsercmdGen.buttonState_t
import org.lwjgl.glfw.GLFWCursorPosCallback
import org.lwjgl.glfw.GLFWScrollCallback
import org.lwjgl.glfw.GLFWMouseButtonCallback
import org.lwjgl.glfw.GLFWKeyCallback
import neo.framework.DeclManager.huffmanCode_s
import neo.framework.DeclManager.huffmanNode_s
import neo.framework.DeclManager.idDeclManagerLocal
import java.lang.NoSuchMethodException
import java.lang.SecurityException
import neo.framework.DeclManager.idDeclBase
import neo.framework.DeclManager.idDeclLocal
import neo.framework.DeclManager.idDeclFile
import java.lang.IllegalArgumentException
import java.lang.reflect.InvocationTargetException
import neo.framework.DeclManager.idDeclType
import neo.framework.DeclManager.idDeclFolder
import neo.framework.DeclManager.idDeclManagerLocal.ListDecls_f
import neo.framework.DeclManager.idDeclManagerLocal.ReloadDecls_f
import neo.framework.DeclManager.idDeclManagerLocal.TouchDecl_f
import neo.framework.DeclManager.ListHuffmanFrequencies_f
import neo.framework.DeclParticle.ParticleParmDesc
import neo.framework.DeclParticle
import neo.framework.DeclParticle.idParticleParm
import neo.framework.DeclParticle.prtCustomPth_t
import neo.framework.DeclParticle.prtDirection_t
import neo.framework.DeclParticle.prtDistribution_t
import neo.framework.DeclParticle.prtOrientation_t
import neo.framework.FileSystem_h.pureExclusion_s
import neo.framework.FileSystem_h.excludeExtension
import neo.framework.FileSystem_h.excludePathPrefixAndExtension
import neo.framework.FileSystem_h.excludeFullName
import neo.framework.FileSystem_h.idInitExclusions
import neo.framework.FileSystem_h.urlDownload_s
import neo.framework.FileSystem_h.fileDownload_s
import neo.framework.FileSystem_h.idModList
import neo.framework.FileSystem_h.pureExclusionFunc_t
import neo.framework.FileSystem_h.fileInPack_s
import neo.framework.FileSystem_h.addonInfo_t
import neo.framework.FileSystem_h.binaryStatus_t
import neo.framework.FileSystem_h.pureStatus_t
import neo.framework.FileSystem_h.directory_t
import neo.framework.FileSystem_h.searchpath_s
import neo.framework.FileSystem_h.pack_t
import neo.framework.FileSystem_h.idDEntry
import neo.framework.FileSystem_h.idFileSystemLocal.BackgroundDownloadThread
import java.nio.file.InvalidPathException
import java.util.UUID
import java.nio.file.Files
import java.nio.file.LinkOption
import neo.framework.FileSystem_h.idFileSystemLocal.Dir_f
import neo.framework.FileSystem_h.idFileSystemLocal.DirTree_f
import neo.framework.FileSystem_h.idFileSystemLocal.Path_f
import neo.framework.FileSystem_h.idFileSystemLocal.TouchFile_f
import neo.framework.FileSystem_h.idFileSystemLocal.TouchFileList_f
import neo.framework.DemoChecksum
import neo.framework.Session_local.fileTIME_T
import neo.framework.Session_local.logCmd_t
import neo.framework.Session_local.mapSpawnData_t
import neo.framework.Session_local.idSessionLocal.cdKeyState_t
import neo.framework.Session_local
import neo.framework.Session_menu.idListSaveGameCompare
import java.util.stream.IntStream
import java.util.function.IntUnaryOperator
import java.nio.file.StandardOpenOption
import java.util.HashSet
import java.nio.LongBuffer
import java.lang.StackTraceElement
import java.lang.NoSuchFieldException
import javax.swing.undo.CannotUndoException
import org.junit.Before

/**
 *
 */
object AFEditor_resource {
    const val IDC_AF_VIEW_AF = 212
    const val IDC_AF_VIEW_LINES = 214
    const val IDC_AF_VIEW_MD5 = 213
    const val IDC_AF_VIEW_PHYSICS = 215
    const val IDC_BODY_COLLISIONMODEL = 295
    const val IDC_BUTTON_AF_DELETE = 204
    const val IDC_BUTTON_AF_KILL = 207
    const val IDC_BUTTON_AF_NEW = 203
    const val IDC_BUTTON_AF_SAVE = 208
    const val IDC_BUTTON_AF_SPAWN = 205
    const val IDC_BUTTON_AF_TPOSE = 206
    const val IDC_BUTTON_BROWSE_MODEL = 243
    const val IDC_BUTTON_BROWSE_SKIN = 246
    const val IDC_BUTTON_CM_BROWSE = 299
    const val IDC_BUTTON_DELETEBODY = 294
    const val IDC_BUTTON_DELETECONSTRAINT = 359
    const val IDC_BUTTON_NEWBODY = 292
    const val IDC_BUTTON_NEWCONSTRAINT = 357
    const val IDC_BUTTON_RENAMEBODY = 293
    const val IDC_BUTTON_RENAMECONSTRAINT = 358
    const val IDC_CHECK_LINES_DEPTHTEST = 230
    const val IDC_CHECK_LINES_USEARROWS = 231
    const val IDC_CHECK_MD5_SKELETON = 228
    const val IDC_CHECK_MD5_SKELETONONLY = 229
    const val IDC_CHECK_PHYSICS_DRAG_ENTITIES = 237
    const val IDC_CHECK_PHYSICS_NOFRICTION = 232
    const val IDC_CHECK_PHYSICS_NOGRAVITY = 234
    const val IDC_CHECK_PHYSICS_NOLIMITS = 233
    const val IDC_CHECK_PHYSICS_NOSELFCOLLISION = 235
    const val IDC_CHECK_PHYSICS_SHOW_DRAG_SELECTION = 238
    const val IDC_CHECK_PHYSICS_TIMING = 236
    const val IDC_CHECK_SELFCOLLISION = 262
    const val IDC_CHECK_VIEW_BODIES = 216
    const val IDC_CHECK_VIEW_BODYMASS = 218
    const val IDC_CHECK_VIEW_BODYNAMES = 217
    const val IDC_CHECK_VIEW_CONSTRAINEDBODIES = 226
    const val IDC_CHECK_VIEW_CONSTRAINTNAMES = 222
    const val IDC_CHECK_VIEW_CONSTRAINTS = 223
    const val IDC_CHECK_VIEW_INERTIATENSOR = 220
    const val IDC_CHECK_VIEW_LIMITS = 225
    const val IDC_CHECK_VIEW_PRIMARYONLY = 224
    const val IDC_CHECK_VIEW_TOTALMASS = 219
    const val IDC_CHECK_VIEW_TREES = 227
    const val IDC_CHECK_VIEW_VELOCITY = 221
    const val IDC_COMBO_AF = 202
    const val IDC_COMBO_ANCHOR2_JOINT = 484
    const val IDC_COMBO_ANCHOR_JOINT = 373
    const val IDC_COMBO_BAS_LIMIT_AXIS_JOINT1 = 464
    const val IDC_COMBO_BAS_LIMIT_AXIS_JOINT2 = 465
    const val IDC_COMBO_BAS_LIMIT_JOINT1 = 456
    const val IDC_COMBO_BAS_LIMIT_JOINT2 = 457
    const val IDC_COMBO_BODIES = 291
    const val IDC_COMBO_BONE_JOINT1 = 300
    const val IDC_COMBO_BONE_JOINT2 = 301
    const val IDC_COMBO_CM_TYPE = 297
    const val IDC_COMBO_CONSTRAINTS = 356
    const val IDC_COMBO_CONSTRAINT_BODY1 = 365
    const val IDC_COMBO_CONSTRAINT_BODY2 = 366
    const val IDC_COMBO_CONSTRAINT_TYPE = 362
    const val IDC_COMBO_HINGE_AXIS_JOINT1 = 424
    const val IDC_COMBO_HINGE_AXIS_JOINT2 = 425
    const val IDC_COMBO_MODIFIEDJOINT = 348
    const val IDC_COMBO_ORIGIN_BONECENTER_JOINT1 = 329
    const val IDC_COMBO_ORIGIN_BONECENTER_JOINT2 = 330
    const val IDC_COMBO_ORIGIN_JOINT = 331
    const val IDC_COMBO_SLIDER_AXIS_JOINT1 = 473
    const val IDC_COMBO_SLIDER_AXIS_JOINT2 = 474
    const val IDC_COMBO_UNIVERSAL_JOINT1_SHAFT1 = 384
    const val IDC_COMBO_UNIVERSAL_JOINT1_SHAFT2 = 393
    const val IDC_COMBO_UNIVERSAL_JOINT2_SHAFT1 = 385
    const val IDC_COMBO_UNIVERSAL_JOINT2_SHAFT2 = 394
    const val IDC_COMBO_UNIVERSAL_LIMIT_JOINT1 = 406
    const val IDC_COMBO_UNIVERSAL_LIMIT_JOINT2 = 407
    const val IDC_DIALOG_AF_BODY_START = 290
    const val IDC_DIALOG_AF_CONSTRAINT_BAS_START = 440
    const val IDC_DIALOG_AF_CONSTRAINT_HINGE_START = 420
    const val IDC_DIALOG_AF_CONSTRAINT_SLIDER_START = 471
    const val IDC_DIALOG_AF_CONSTRAINT_SPRING_START = 481
    const val IDC_DIALOG_AF_CONSTRAINT_START = 355
    const val IDC_DIALOG_AF_CONSTRAINT_UNIVERSAL_START = 370
    const val IDC_DIALOG_AF_PROPERTIES_START = 239
    const val IDC_DIALOG_AF_START = 201

    //
    const val IDC_DIALOG_AF_TAB_MODE = 200
    const val IDC_DIALOG_AF_VIEW_START = 211
    const val IDC_EDIT_AF_NAME = 209
    const val IDC_EDIT_AF_VECTOR_X = 323
    const val IDC_EDIT_AF_VECTOR_Y = 324
    const val IDC_EDIT_AF_VECTOR_Z = 325
    const val IDC_EDIT_ANCHOR2_X = 486
    const val IDC_EDIT_ANCHOR2_Y = 488
    const val IDC_EDIT_ANCHOR2_Z = 490
    const val IDC_EDIT_ANCHOR_X = 375
    const val IDC_EDIT_ANCHOR_Y = 377
    const val IDC_EDIT_ANCHOR_Z = 379
    const val IDC_EDIT_ANGLES_PITCH = 336
    const val IDC_EDIT_ANGLES_ROLL = 340
    const val IDC_EDIT_ANGLES_YAW = 338
    const val IDC_EDIT_ANGULARACCELERATION = 274
    const val IDC_EDIT_ANGULARFRICTION = 252
    const val IDC_EDIT_ANGULARVELOCITY = 270
    const val IDC_EDIT_ANGULAR_TOLERANCE = 283
    const val IDC_EDIT_BAS_LIMIT_AXIS_PITCH = 467
    const val IDC_EDIT_BAS_LIMIT_AXIS_YAW = 469
    const val IDC_EDIT_BAS_LIMIT_CONE_ANGLE = 445
    const val IDC_EDIT_BAS_LIMIT_PITCH = 458
    const val IDC_EDIT_BAS_LIMIT_PYRAMID_ANGLE1 = 447
    const val IDC_EDIT_BAS_LIMIT_PYRAMID_ANGLE2 = 449
    const val IDC_EDIT_BAS_LIMIT_ROLL = 451
    const val IDC_EDIT_BAS_LIMIT_YAW = 460
    const val IDC_EDIT_CLIPMASK = 266
    const val IDC_EDIT_CM_DENSITY = 315
    const val IDC_EDIT_CM_HEIGHT = 303
    const val IDC_EDIT_CM_INERTIASCALE = 318
    const val IDC_EDIT_CM_LENGTH = 309
    const val IDC_EDIT_CM_NAME = 298
    const val IDC_EDIT_CM_NUMSIDES = 312
    const val IDC_EDIT_CM_WIDTH = 306
    const val IDC_EDIT_CONSTRAINTFRICTION = 258
    const val IDC_EDIT_CONSTRAINT_FRICTION = 368
    const val IDC_EDIT_CONTACTFRICTION = 255
    const val IDC_EDIT_CONTACTMOTORDIRECTION = 345
    const val IDC_EDIT_CONTAINEDJOINTS = 354
    const val IDC_EDIT_CONTENTS = 264
    const val IDC_EDIT_FRICTIONDIRECTION = 343
    const val IDC_EDIT_HINGE_AXIS_PITCH = 426
    const val IDC_EDIT_HINGE_AXIS_YAW = 428
    const val IDC_EDIT_HINGE_LIMIT_ANGLE1 = 435
    const val IDC_EDIT_HINGE_LIMIT_ANGLE2 = 437
    const val IDC_EDIT_HINGE_LIMIT_ANGLE3 = 438
    const val IDC_EDIT_LINEARACCELERATION = 272
    const val IDC_EDIT_LINEARFRICTION = 249
    const val IDC_EDIT_LINEARVELOCITY = 268
    const val IDC_EDIT_LINEAR_TOLERANCE = 281
    const val IDC_EDIT_MAXIMUM_MOVE_TIME = 287
    const val IDC_EDIT_MINIMUM_MOVE_TIME = 289
    const val IDC_EDIT_MODEL = 242
    const val IDC_EDIT_NO_MOVE_TIME = 285
    const val IDC_EDIT_SKIN = 245
    const val IDC_EDIT_SLIDER_AXIS_PITCH = 476
    const val IDC_EDIT_SLIDER_AXIS_YAW = 478
    const val IDC_EDIT_SPRING_COMPRESS = 499
    const val IDC_EDIT_SPRING_DAMPING = 501
    const val IDC_EDIT_SPRING_MAX_LENGTH = 512
    const val IDC_EDIT_SPRING_MIN_LENGTH = 508
    const val IDC_EDIT_SPRING_REST_LENGTH = 503
    const val IDC_EDIT_SPRING_STRETCH = 497
    const val IDC_EDIT_TOTALMASS = 277
    const val IDC_EDIT_UNIVERSAL_LIMIT_CONE_ANGLE = 414
    const val IDC_EDIT_UNIVERSAL_LIMIT_PITCH = 410
    const val IDC_EDIT_UNIVERSAL_LIMIT_PYRAMID_ANGLE1 = 416
    const val IDC_EDIT_UNIVERSAL_LIMIT_PYRAMID_ANGLE2 = 418
    const val IDC_EDIT_UNIVERSAL_LIMIT_ROLL = 412
    const val IDC_EDIT_UNIVERSAL_LIMIT_YAW = 408
    const val IDC_EDIT_UNIVERSAL_PITCH_SHAFT1 = 386
    const val IDC_EDIT_UNIVERSAL_PITCH_SHAFT2 = 395
    const val IDC_EDIT_UNIVERSAL_YAW_SHAFT1 = 388
    const val IDC_EDIT_UNIVERSAL_YAW_SHAFT2 = 397
    const val IDC_RADIO_ANCHOR2_COORDINATES = 485
    const val IDC_RADIO_ANCHOR2_JOINT = 483
    const val IDC_RADIO_ANCHOR_COORDINATES = 374
    const val IDC_RADIO_ANCHOR_JOINT = 372
    const val IDC_RADIO_BAS_LIMIT_ANGLES = 455
    const val IDC_RADIO_BAS_LIMIT_AXIS_ANGLES = 466
    const val IDC_RADIO_BAS_LIMIT_AXIS_BONE = 463
    const val IDC_RADIO_BAS_LIMIT_BONE = 454
    const val IDC_RADIO_BAS_LIMIT_CONE = 443
    const val IDC_RADIO_BAS_LIMIT_NONE = 442
    const val IDC_RADIO_BAS_LIMIT_PYRAMID = 444
    const val IDC_RADIO_HINGE_AXIS_ANGLES = 423
    const val IDC_RADIO_HINGE_AXIS_BONE = 422
    const val IDC_RADIO_HINGE_LIMIT_ANGLES = 433
    const val IDC_RADIO_HINGE_LIMIT_NONE = 431
    const val IDC_RADIO_MODIFY_BOTH = 352
    const val IDC_RADIO_MODIFY_ORIENTATION = 350
    const val IDC_RADIO_MODIFY_POSITION = 351
    const val IDC_RADIO_ORIGIN_BONECENTER = 321
    const val IDC_RADIO_ORIGIN_COORDINATES = 320
    const val IDC_RADIO_ORIGIN_JOINT = 322
    const val IDC_RADIO_SLIDER_AXIS_ANGLES = 475
    const val IDC_RADIO_SLIDER_AXIS_BONE = 472
    const val IDC_RADIO_SPRING_MAX_LENGTH = 511
    const val IDC_RADIO_SPRING_MIN_LENGTH = 507
    const val IDC_RADIO_SPRING_NO_MAX_LENGTH = 510
    const val IDC_RADIO_SPRING_NO_MIN_LENGTH = 506
    const val IDC_RADIO_UNIVERSAL_ANGLES_SHAFT1 = 383
    const val IDC_RADIO_UNIVERSAL_ANGLES_SHAFT2 = 392
    const val IDC_RADIO_UNIVERSAL_BONE_SHAFT1 = 382
    const val IDC_RADIO_UNIVERSAL_BONE_SHAFT2 = 391
    const val IDC_RADIO_UNIVERSAL_LIMIT_ANGLES = 405
    const val IDC_RADIO_UNIVERSAL_LIMIT_BONE = 404
    const val IDC_RADIO_UNIVERSAL_LIMIT_CONE = 401
    const val IDC_RADIO_UNIVERSAL_LIMIT_NONE = 400
    const val IDC_RADIO_UNIVERSAL_LIMIT_PYRAMID = 402
    const val IDC_SPIN_AF_VECTOR_X = 326
    const val IDC_SPIN_AF_VECTOR_Y = 327
    const val IDC_SPIN_AF_VECTOR_Z = 328
    const val IDC_SPIN_ANCHOR2_X = 487
    const val IDC_SPIN_ANCHOR2_Y = 489
    const val IDC_SPIN_ANCHOR2_Z = 491
    const val IDC_SPIN_ANCHOR_X = 376
    const val IDC_SPIN_ANCHOR_Y = 378
    const val IDC_SPIN_ANCHOR_Z = 380
    const val IDC_SPIN_ANGLES_PITCH = 337
    const val IDC_SPIN_ANGLES_ROLL = 341
    const val IDC_SPIN_ANGLES_YAW = 339
    const val IDC_SPIN_ANGULARFRICTION = 253
    const val IDC_SPIN_BAS_LIMIT_AXIS_PITCH = 468
    const val IDC_SPIN_BAS_LIMIT_AXIS_YAW = 470
    const val IDC_SPIN_BAS_LIMIT_CONE_ANGLE = 446
    const val IDC_SPIN_BAS_LIMIT_PITCH = 459
    const val IDC_SPIN_BAS_LIMIT_PYRAMID_ANGLE1 = 448
    const val IDC_SPIN_BAS_LIMIT_PYRAMID_ANGLE2 = 450
    const val IDC_SPIN_BAS_LIMIT_ROLL = 452
    const val IDC_SPIN_BAS_LIMIT_YAW = 461
    const val IDC_SPIN_CM_DENSITY = 316
    const val IDC_SPIN_CM_HEIGHT = 304
    const val IDC_SPIN_CM_LENGTH = 310
    const val IDC_SPIN_CM_NUMSIDES = 313
    const val IDC_SPIN_CM_WIDTH = 307
    const val IDC_SPIN_CONSTRAINTFRICTION = 259
    const val IDC_SPIN_CONSTRAINT_FRICTION = 369
    const val IDC_SPIN_CONTACTFRICTION = 256
    const val IDC_SPIN_HINGE_AXIS_PITCH = 427
    const val IDC_SPIN_HINGE_AXIS_YAW = 429
    const val IDC_SPIN_HINGE_LIMIT_ANGLE1 = 434
    const val IDC_SPIN_HINGE_LIMIT_ANGLE2 = 436
    const val IDC_SPIN_HINGE_LIMIT_ANGLE3 = 439
    const val IDC_SPIN_LINEARFRICTION = 250
    const val IDC_SPIN_SLIDER_AXIS_PITCH = 477
    const val IDC_SPIN_SLIDER_AXIS_YAW = 479
    const val IDC_SPIN_SPRING_COMPRESS = 500
    const val IDC_SPIN_SPRING_DAMPING = 502
    const val IDC_SPIN_SPRING_MAX_LENGTH = 513
    const val IDC_SPIN_SPRING_MIN_LENGTH = 509
    const val IDC_SPIN_SPRING_REST_LENGTH = 504
    const val IDC_SPIN_SPRING_STRETCH = 498
    const val IDC_SPIN_TOTALMASS = 278
    const val IDC_SPIN_UNIVERSAL_LIMIT_CONE_ANGLE = 415
    const val IDC_SPIN_UNIVERSAL_LIMIT_PITCH = 411
    const val IDC_SPIN_UNIVERSAL_LIMIT_PYRAMID_ANGLE1 = 417
    const val IDC_SPIN_UNIVERSAL_LIMIT_PYRAMID_ANGLE2 = 419
    const val IDC_SPIN_UNIVERSAL_LIMIT_ROLL = 413
    const val IDC_SPIN_UNIVERSAL_LIMIT_YAW = 409
    const val IDC_SPIN_UNIVERSAL_PITCH_SHAFT1 = 387
    const val IDC_SPIN_UNIVERSAL_PITCH_SHAFT2 = 396
    const val IDC_SPIN_UNIVERSAL_YAW_SHAFT1 = 389
    const val IDC_SPIN_UNIVERSAL_YAW_SHAFT2 = 398
    const val IDC_STATIC_AF_NAME = 210
    const val IDC_STATIC_ANCHOR = 371
    const val IDC_STATIC_ANCHOR2 = 482
    const val IDC_STATIC_ANGLES_PITCH = 333
    const val IDC_STATIC_ANGLES_ROLL = 335
    const val IDC_STATIC_ANGLES_YAW = 334
    const val IDC_STATIC_ANGULARACCELERATION = 273
    const val IDC_STATIC_ANGULARFRICTION = 251
    const val IDC_STATIC_ANGULARVELOCITY = 269
    const val IDC_STATIC_ANGULAR_TOLERANCE = 282
    const val IDC_STATIC_BAS_LIMIT_AXIS = 462
    const val IDC_STATIC_BAS_LIMIT_ORIENTATION = 453
    const val IDC_STATIC_BAS_LIMIT_TYPE = 441
    const val IDC_STATIC_BODY1 = 363
    const val IDC_STATIC_BODY2 = 364
    const val IDC_STATIC_BODY_ORIGIN_AND_ANGLES = 319
    const val IDC_STATIC_CLIPMASK = 265
    const val IDC_STATIC_CM_ANGLES = 332
    const val IDC_STATIC_CM_DENSITY = 314
    const val IDC_STATIC_CM_HEIGHT = 302
    const val IDC_STATIC_CM_INERTIASCALE = 317
    const val IDC_STATIC_CM_LENGTH = 308
    const val IDC_STATIC_CM_NUMSIDES = 311
    const val IDC_STATIC_CM_TYPE = 296
    const val IDC_STATIC_CM_WIDTH = 305
    const val IDC_STATIC_COLLISIONDETECTION = 261
    const val IDC_STATIC_CONSTRAINT = 257
    const val IDC_STATIC_CONSTRAINT_FRICTION = 367
    const val IDC_STATIC_CONSTRAINT_GENERAL = 360
    const val IDC_STATIC_CONSTRAINT_TYPE = 361
    const val IDC_STATIC_CONTACTFRICTION = 254
    const val IDC_STATIC_CONTACTMOTORDIRECTION = 344
    const val IDC_STATIC_CONTAINEDJOINTS = 353
    const val IDC_STATIC_CONTENTS = 263
    const val IDC_STATIC_FRICTION = 247
    const val IDC_STATIC_FRICTIONDIRECTION = 342
    const val IDC_STATIC_HINGE_AXIS = 421
    const val IDC_STATIC_HINGE_LIMIT = 430
    const val IDC_STATIC_HINGE_LIMIT2 = 432
    const val IDC_STATIC_JOINTS = 346
    const val IDC_STATIC_LINEARACCELERATION = 271
    const val IDC_STATIC_LINEARFRICTION = 248
    const val IDC_STATIC_LINEARVELOCITY = 267
    const val IDC_STATIC_LINEAR_TOLERANCE = 280
    const val IDC_STATIC_MASS = 275
    const val IDC_STATIC_MAXIMUM_MOVE_TIME = 286
    const val IDC_STATIC_MD5 = 240
    const val IDC_STATIC_MINIMUM_MOVE_TIME = 288
    const val IDC_STATIC_MODEL = 241
    const val IDC_STATIC_MODIFIEDJOINT = 347
    const val IDC_STATIC_MODIFY = 349
    const val IDC_STATIC_NO_MOVE_TIME = 284
    const val IDC_STATIC_SKIN = 244
    const val IDC_STATIC_SLIDER_AXIS = 480
    const val IDC_STATIC_SPRING_COMPRESS = 494
    const val IDC_STATIC_SPRING_DAMPING = 495
    const val IDC_STATIC_SPRING_LIMIT = 505
    const val IDC_STATIC_SPRING_REST_LENGTH = 496
    const val IDC_STATIC_SPRING_SETTINGS = 492
    const val IDC_STATIC_SPRING_STRETCH = 493
    const val IDC_STATIC_SUSPENDMOVEMENT = 279
    const val IDC_STATIC_SUSPENDSPEED = 260
    const val IDC_STATIC_TOTALMASS = 276
    const val IDC_STATIC_UNIVERSAL_LIMIT_ORIENTATION = 403
    const val IDC_STATIC_UNIVERSAL_LIMIT_TYPE = 399
    const val IDC_STATIC_UNIVERSAL_SHAFT1 = 381
    const val IDC_STATIC_UNIVERSAL_SHAFT2 = 390
    const val IDD_DIALOG_AF = 100
    const val IDD_DIALOG_AF_BODY = 104
    const val IDD_DIALOG_AF_CONSTRAINT = 105
    const val IDD_DIALOG_AF_CONSTRAINT_BALLANDSOCKET = 107
    const val IDD_DIALOG_AF_CONSTRAINT_FIXED = 106
    const val IDD_DIALOG_AF_CONSTRAINT_HINGE = 109
    const val IDD_DIALOG_AF_CONSTRAINT_SLIDER = 110
    const val IDD_DIALOG_AF_CONSTRAINT_SPRING = 111
    const val IDD_DIALOG_AF_CONSTRAINT_UNIVERSAL = 108
    const val IDD_DIALOG_AF_NAME = 101
    const val IDD_DIALOG_AF_PROPERTIES = 103
    const val IDD_DIALOG_AF_VIEW = 102
    const val IDI_ICON2 = 112

    //
    //
    // Next default values for new objects
    // 
    // #ifdef APSTUDIO_INVOKED
    // #ifndef APSTUDIO_READONLY_SYMBOLS
    const val _APS_3D_CONTROLS = 1
    const val _APS_NEXT_COMMAND_VALUE = 20000
    const val _APS_NEXT_CONTROL_VALUE = 514
    const val _APS_NEXT_RESOURCE_VALUE = 113
    const val _APS_NEXT_SYMED_VALUE = 113 // #endif
    // #endif
}
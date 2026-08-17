package neo.ui

import neo.Game.Animation.Anim.idMD5Anim
import neo.Game.GameEdit.gameEdit
import neo.Renderer.RenderSystem
import neo.Renderer.RenderWorld
import neo.Renderer.RenderWorld.idRenderWorld
import neo.Renderer.RenderWorld.renderEntity_s
import neo.Renderer.RenderWorld.renderLight_s
import neo.Renderer.RenderWorld.renderView_s
import neo.framework.Common
import neo.idlib.Dict_h.idDict
import neo.idlib.Text.Parser.idParser
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Str.idStr.Companion.Icmp
import neo.idlib.geometry.JointTransform.idJointMat
import neo.idlib.math.*
import neo.ui.DeviceContext.idDeviceContext
import neo.ui.SimpleWindow.drawWin_t
import neo.ui.UserInterfaceLocal.idUserInterfaceLocal
import neo.ui.Window.idWindow
import neo.ui.Winvar.idWinBool
import neo.ui.Winvar.idWinStr
import neo.ui.Winvar.idWinVec4
import kotlin.math.atan

class RenderWindow {
    class idRenderWindow : idWindow {
        private val animClass = idStr()
        private val animName = idWinStr()
        private val lightColor = idWinVec4()
        private val lightOrigin = idWinVec4()
        private val modelName = idWinStr()
        private val modelOrigin = idWinVec4()
        private val modelRotate = idWinVec4()
        private val needsRender = idWinBool()
        private val rLight = renderLight_s()
        private val viewOffset = idWinVec4()
        private var animEndTime = 0
        private var animLength = 0
        private var  /*qhandle_t*/lightDef = 0
        private var modelAnim: idMD5Anim? = null
        private var  /*qhandle_t*/modelDef = 0
        private var refdef: renderView_s? = null
        private var updateAnimation = false
        private var world: idRenderWorld? = null
        private var worldEntity: renderEntity_s? = null

        //
        private val  /*qhandle_t*/worldModelDef = 0

        //
        //
        constructor(gui: idUserInterfaceLocal) : super(gui) {
            this.gui = gui
            CommonInit()
        }

        constructor(dc: idDeviceContext?, gui: idUserInterfaceLocal?) : super(dc, gui) {
            this.dc = dc
            this.gui = gui
            CommonInit()
        }

        override fun Draw(time: Int, x: Float, y: Float) {
            PreRender()
            Render(time)

            refdef = renderView_s()
            refdef!!.vieworg.set(viewOffset.ToVec3()) //refdef.vieworg.Set(-128, 0, 0);
            refdef!!.viewaxis.Identity()
            refdef!!.shaderParms[0] = 1.0f
            refdef!!.shaderParms[1] = 1.0f
            refdef!!.shaderParms[2] = 1.0f
            refdef!!.shaderParms[3] = 1.0f

            // DG: for scaling menus to 4:3 (like that spinning mars globe in the main menu)
            var rx = drawRect.x
            var ry = drawRect.y
            var rw = drawRect.w
            var rh = drawRect.h
            if (dc!!.IsMenuScaleFixActive()) {
                val xa = floatArrayOf(rx)
                val ya = floatArrayOf(ry)
                val wa = floatArrayOf(rw)
                val ha = floatArrayOf(rh)
                dc!!.AdjustCoords(xa, ya, wa, ha)
                rx = xa[0]; ry = ya[0]; rw = wa[0]; rh = ha[0]
            }
            refdef!!.x = rx.toInt()
            refdef!!.y = ry.toInt()
            refdef!!.width = rw.toInt()
            refdef!!.height = rh.toInt() // DG end
            refdef!!.fov_x = 90.0f
            refdef!!.fov_y = (2 * atan((drawRect.h / drawRect.w)) * idMath.M_RAD2DEG)
            refdef!!.time = time
            world!!.RenderScene(refdef!!)
        }

        override fun GetWinVarByName(
            _name: String?, winLookup: Boolean /*= false*/, owner: Array<drawWin_t?>? /*= NULL*/
        ): Winvar.idWinVar? {
            if (Icmp(_name!!, "model") == 0) {
                return modelName
            }
            if (Icmp(_name, "anim") == 0) {
                return animName
            }
            if (Icmp(_name, "lightOrigin") == 0) {
                return lightOrigin
            }
            if (Icmp(_name, "lightColor") == 0) {
                return lightColor
            }
            if (Icmp(_name, "modelOrigin") == 0) {
                return modelOrigin
            }
            if (Icmp(_name, "modelRotate") == 0) {
                return modelRotate
            }
            if (Icmp(_name, "viewOffset") == 0) {
                return viewOffset
            }
            return if (Icmp(_name, "needsRender") == 0) {
                needsRender
            } else super.GetWinVarByName(_name, winLookup, owner)
        }

        private fun CommonInit() {
            world = RenderSystem.renderSystem.AllocRenderWorld()
            needsRender.data = true
            lightOrigin.set(idVec4(-128.0f, 0.0f, 0.0f, 1.0f))
            lightColor.set(idVec4(1.0f, 1.0f, 1.0f, 1.0f))
            modelOrigin.Zero()
            viewOffset.set(idVec4(-128.0f, 0.0f, 0.0f, 1.0f))
            modelAnim = null
            animLength = 0
            animEndTime = -1
            modelDef = -1
            updateAnimation = true
        }

        override fun ParseInternalVar(_name: String?, src: idParser): Boolean {
            if (Icmp(_name!!, "animClass") == 0) {
                ParseString(src, animClass)
                return true
            }
            return super.ParseInternalVar(_name, src)
        }

        /**
         * This function renders the 3D shit to the screen.
         *
         * @param time
         */
        private fun Render(time: Int) {
            rLight.origin.set(lightOrigin.ToVec3())
            rLight.shaderParms[RenderWorld.SHADERPARM_RED] = lightColor.x()
            rLight.shaderParms[RenderWorld.SHADERPARM_GREEN] = lightColor.y()
            rLight.shaderParms[RenderWorld.SHADERPARM_BLUE] = lightColor.z()
            world!!.UpdateLightDef(lightDef, rLight)
            if (worldEntity!!.hModel != null) {
                if (updateAnimation) {
                    BuildAnimation(time)
                }
                if (modelAnim != null) {
                    if (time > animEndTime) {
                        animEndTime = time + animLength
                    }
                    gameEdit.ANIM_CreateAnimFrame(
                        worldEntity!!.hModel,
                        modelAnim,
                        worldEntity!!.numJoints,
                        worldEntity!!.joints,
                        animLength - (animEndTime - time),
                        vec3_origin,
                        false
                    )
                }
                worldEntity!!.axis.set(idAngles(modelRotate.x(), modelRotate.y(), modelRotate.z()).ToMat3())
                world!!.UpdateEntityDef(modelDef, worldEntity!!)
            }
        }

        private fun PreRender() {
            if (needsRender.data) {
                world!!.InitFromMap(null)
                val spawnArgs = idDict()
                spawnArgs.Set("classname", "light")
                spawnArgs.Set("name", "light_1")
                spawnArgs.Set("origin", lightOrigin.ToVec3().ToString())
                spawnArgs.Set("_color", lightColor.ToVec3().ToString())
                gameEdit.ParseSpawnArgsToRenderLight(spawnArgs, rLight)
                lightDef = world!!.AddLightDef(rLight)
                if (modelName.c_str() == null || modelName.c_str()!!.isEmpty()) {
                    Common.common.Warning("Window '%s' in gui '%s': no model set", GetName(), GetGui().GetSourceFile())
                }
                worldEntity = renderEntity_s()
                spawnArgs.Clear()
                spawnArgs.Set("classname", "func_static")
                spawnArgs.Set("model", modelName.c_str()!!)
                spawnArgs.Set("origin", modelOrigin.c_str())
                gameEdit.ParseSpawnArgsToRenderEntity(spawnArgs, worldEntity!!)
                if (worldEntity!!.hModel != null) {
                    val v = idVec3(modelRotate.ToVec3())
                    worldEntity!!.axis.set(v.ToMat3())
                    worldEntity!!.shaderParms[0] = 1.0f
                    worldEntity!!.shaderParms[1] = 1.0f
                    worldEntity!!.shaderParms[2] = 1.0f
                    worldEntity!!.shaderParms[3] = 1.0f
                    modelDef = world!!.AddEntityDef(worldEntity!!)
                }
                needsRender.data = false
            }
        }

        private fun BuildAnimation(time: Int) {
            if (!updateAnimation) {
                return
            }
            if (animName.Length() != 0 && animClass.Length() != 0) {
                worldEntity!!.numJoints = worldEntity!!.hModel!!.NumJoints()
                worldEntity!!.joints = Array(worldEntity!!.numJoints) { idJointMat() }
                modelAnim = gameEdit.ANIM_GetAnimFromEntityDef(animClass.toString(), animName.toString())
                if (modelAnim != null) {
                    animLength = gameEdit.ANIM_GetLength(modelAnim)
                    animEndTime = time + animLength
                }
            }
            updateAnimation = false
        }
    }
}

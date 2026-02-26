package neo.ui

import neo.Renderer.Material
import neo.Renderer.Material.idMaterial
import neo.TempDump.itob
import neo.TempDump.wrapToNativeBuffer
import neo.framework.Common.Companion.common
import neo.framework.DeclManager
import neo.framework.FileSystem_h.fileSystem
import neo.framework.FileSystem_h.idFileList
import neo.framework.File_h.idFile
import neo.framework.KeyInput.K_MOUSE1
import neo.framework.KeyInput.K_MOUSE2
import neo.framework.KeyInput.K_SPACE
import neo.framework.Session
import neo.framework.Session.logStats_t
import neo.idlib.Text.Parser.idParser
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Str.idStr.Companion.Icmp
import neo.idlib.Text.Str.va
import neo.idlib.containers.List.idList
import neo.idlib.math.idVec4
import neo.sys.sys_public.sysEventType_t
import neo.sys.sys_public.sysEvent_s
import neo.ui.DeviceContext.idDeviceContext
import neo.ui.Rectangle.idRectangle
import neo.ui.UserInterfaceLocal.idUserInterfaceLocal
import neo.ui.Window.idWindow
import java.util.*
import kotlin.math.abs

class MarkerWindow {
    class markerData_t {
        var mat: idMaterial? = null
        val rect = idRectangle()
        var time = 0
    }

    class idMarkerWindow : idWindow {
        private val loggedStats = arrayOfNulls<logStats_t>(Session.MAX_LOGGED_STATS)
        private var currentMarker = 0
        private var currentTime = 0
        private var imageBuff: IntArray? = null
        private val markerColor: idVec4 = idVec4()
        private var markerMat: idMaterial? = null
        private var markerStop: idMaterial? = null
        private val markerTimes = idList<markerData_t>()
        private var numStats = 0
        private val statData: idStr = idStr()
        private var stopTime = 0

        constructor(gui: idUserInterfaceLocal) : super(gui) {
            this.gui = gui
            CommonInit()
        }

        constructor(dc: idDeviceContext?, gui: idUserInterfaceLocal?) : super(dc, gui) {
            this.dc = dc
            this.gui = gui
            CommonInit()
        }

        fun HandleEvent(event: sysEvent_s, updateVisuals: Boolean): String {
            if (!(event.evType == sysEventType_t.SE_KEY && event.evValue2 != 0)) {
                return ""
            }
            val key = event.evValue
            if (event.evValue2 != 0 && key == K_MOUSE1) {
                gui!!.GetDesktop()!!.SetChildWinVarVal("markerText", "text", "")
                val c = markerTimes.Num()
                var i: Int
                i = 0
                while (i < c) {
                    val md = markerTimes[i]
                    if (md.rect.Contains(gui!!.CursorX(), gui!!.CursorY())) {
                        currentMarker = i
                        gui!!.SetStateInt("currentMarker", md.time)
                        stopTime = md.time
                        gui!!.GetDesktop()!!
                            .SetChildWinVarVal(
                                "markerText",
                                "text",
                                va("Marker set at %.2i:%.2i", md.time / 60 / 60, md.time / 60 % 60)
                            )
                        gui!!.GetDesktop()!!.SetChildWinVarVal("markerText", "visible", "1")
                        gui!!.GetDesktop()!!.SetChildWinVarVal("markerBackground", "matcolor", "1 1 1 1")
                        gui!!.GetDesktop()!!.SetChildWinVarVal("markerBackground", "text", "")
                        gui!!.GetDesktop()!!.SetChildWinVarVal("markerBackground", "background", md.mat!!.GetName())
                        break
                    }
                    i++
                }
                if (i == c) {
                    // no marker selected;
                    currentMarker = -1
                    gui!!.SetStateInt("currentMarker", currentTime)
                    stopTime = currentTime
                    gui!!.GetDesktop()!!
                        .SetChildWinVarVal(
                            "markerText",
                            "text",
                            va("Marker set at %.2i:%.2i", currentTime / 60 / 60, currentTime / 60 % 60)
                        )
                    gui!!.GetDesktop()!!.SetChildWinVarVal("markerText", "visible", "1")
                    gui!!.GetDesktop()!!.SetChildWinVarVal("markerBackground", "matcolor", "0 0 0 0")
                    gui!!.GetDesktop()!!.SetChildWinVarVal("markerBackground", "text", "No Preview")
                }
                val pct = gui!!.State().GetFloat("loadPct")
                val len = gui!!.State().GetInt("loadLength")
                if (stopTime > len * pct) {
                    return "cmdDemoGotoMarker"
                }
            } else if (key == K_MOUSE2) {
                stopTime = -1
                gui!!.GetDesktop()!!.SetChildWinVarVal("markerText", "text", "")
                gui!!.SetStateInt("currentMarker", -1)
                return "cmdDemoGotoMarker"
            } else if (key == K_SPACE) {
                return "cmdDemoPauseFrame"
            }
            return ""
        }

        override fun Draw(time: Int, x: Float, y: Float) {
            var pct: Float
            var r = idRectangle(clientRect)
            var len = gui!!.State().GetInt("loadLength")
            if (len == 0) {
                len = 1
            }
            if (numStats > 1) {
                val c = markerTimes.Num()
                if (c > 0) {
                    for (i in 0 until c) {
                        val md = markerTimes[i]
                        if (md.rect.w == 0.0f) {
                            md.rect.x = r.x + r.w * (md.time / len) - 8
                            md.rect.y = r.y + r.h - 20
                            md.rect.w = 16.0f
                            md.rect.h = 16.0f
                        }
                        dc!!.DrawMaterial(md.rect.x, md.rect.y, md.rect.w, md.rect.h, markerMat, markerColor)
                    }
                }
            }
            r.y += 10.0f
            if (r.w > 0 && r.Contains(gui!!.CursorX(), gui!!.CursorY())) {
                pct = (gui!!.CursorX() - r.x) / r.w
                currentTime = (len * pct).toInt()
                r.x = if (gui!!.CursorX() > r.x + r.w - 40) gui!!.CursorX() - 40 else gui!!.CursorX()
                r.y = gui!!.CursorY() - 15
                r.w = 40.0f
                r.h = 20.0f
                dc!!.DrawText(
                    va("%.2i:%.2i", currentTime / 60 / 60, currentTime / 60 % 60),
                    0.25f,
                    0,
                    idDeviceContext.colorWhite,
                    r,
                    false
                )
            }
            if (stopTime >= 0 && markerStop != null) {
                r = idRectangle(clientRect)
                r.y += (r.h - 32) / 2
                pct = stopTime.toFloat() / len
                r.x += r.w * pct - 16
                val color = idVec4(1.0f, 1.0f, 1.0f, 0.65f)
                dc!!.DrawMaterial(r.x, r.y, 32.0f, 32.0f, markerStop, color)
            }
        }

        override fun RouteMouseCoords(xd: Float, yd: Float): String? {
            val ret = super.RouteMouseCoords(xd, yd)
            idRectangle()
            var i: Int
            val c = markerTimes.Num()
            var len = gui!!.State().GetInt("loadLength")
            if (len == 0) {
                len = 1
            }
            i = 0
            while (i < c) {
                val md = markerTimes[i]
                if (md.rect.Contains(gui!!.CursorY(), gui!!.CursorX())) {
                    gui!!.GetDesktop()!!.SetChildWinVarVal("markerBackground", "background", md.mat!!.GetName())
                    gui!!.GetDesktop()!!.SetChildWinVarVal("markerBackground", "matcolor", "1 1 1 1")
                    gui!!.GetDesktop()!!.SetChildWinVarVal("markerBackground", "text", "")
                    break
                }
                i++
            }
            if (i >= c) {
                if (currentMarker == -1) {
                    gui!!.GetDesktop()!!.SetChildWinVarVal("markerBackground", "matcolor", "0 0 0 0")
                    gui!!.GetDesktop()!!.SetChildWinVarVal("markerBackground", "text", "No Preview")
                } else {
                    val md = markerTimes[currentMarker]
                    gui!!.GetDesktop()!!.SetChildWinVarVal("markerBackground", "background", md.mat!!.GetName())
                    gui!!.GetDesktop()!!.SetChildWinVarVal("markerBackground", "matcolor", "1 1 1 1")
                    gui!!.GetDesktop()!!.SetChildWinVarVal("markerBackground", "text", "")
                }
            }
            return ret
        }

        override fun Activate(activate: Boolean, act: idStr) {
            super.Activate(activate, act)
            if (activate) {
                var i: Int
                gui!!.GetDesktop()!!.SetChildWinVarVal("markerText", "text", "")
                imageBuff = IntArray(512 * 64 * 4) // Mem_Alloc(512 * 64 * 4);
                markerTimes.Clear()
                currentMarker = -1
                currentTime = -1
                stopTime = -1
                statData!!.set(gui!!.State().GetString("statData"))
                numStats = 0
                if (statData.Length() != 0) {
                    val file: idFile? = fileSystem.OpenFileRead(statData.toString())
                    if (file != null) {
                        numStats = file.ReadInt()
                        //                        file->Read(loggedStats, numStats * sizeof(loggedStats[0]));
                        i = 0
                        while (i < numStats) {
                            file.Read(loggedStats[i]!!)
                            if (loggedStats[i]!!.health < 0) {
                                loggedStats[i]!!.health = 0
                            }
                            if (loggedStats[i]!!.stamina < 0) {
                                loggedStats[i]!!.stamina = 0
                            }
                            if (loggedStats[i]!!.heartRate < 0) {
                                loggedStats[i]!!.heartRate = 0.0f
                            }
                            if (loggedStats[i]!!.combat < 0) {
                                loggedStats[i]!!.combat = 0
                            }
                            i++
                        }
                        fileSystem.CloseFile(file)
                    }
                }
                if (numStats > 1 && background != null) {
                    val markerPath = statData
                    markerPath.StripFilename()
                    val markers: idFileList
                    markers = fileSystem.ListFiles(markerPath.toString(), ".tga", false, true)
                    var name: idStr
                    i = 0
                    while (i < markers.GetNumFiles()) {
                        name = idStr(markers.GetFile(i))
                        val md = markerData_t()
                        md.mat = DeclManager.declManager.FindMaterial(name)
                        md.mat!!.SetSort(Material.SS_GUI.toFloat())
                        name.StripPath()
                        name.StripFileExtension()
                        md.time = name.toString().toInt()
                        markerTimes.Append(md)
                        i++
                    }
                    fileSystem.FreeFileList(markers)
                    //                    memset(imageBuff, 0, 512 * 64 * 4);
                    Arrays.fill(imageBuff, 0, 512 * 64 * 4, 0)
                    val step = 511.0f / (numStats - 1)
                    var x1: Float
                    var y1: Float
                    var x2: Float
                    var y2: Float
                    x1 = 0 - step
                    i = 0
                    while (i < numStats - 1) {
                        x1 += step
                        x2 = x1 + step
                        y1 = 63.0f * (loggedStats[i]!!.health / HEALTH_MAX)
                        y2 = 63.0f * (loggedStats[i + 1]!!.health / HEALTH_MAX)
                        Line(x1, y1, x2, y2, imageBuff!!, -0xffff01)
                        y1 = 63.0f * (loggedStats[i]!!.heartRate / RATE_MAX)
                        y2 = 63 * (loggedStats[i + 1]!!.heartRate / RATE_MAX)
                        Line(x1, y1, x2, y2, imageBuff!!, -0xff0100)
                        // stamina not quite as high on graph so health does not get obscured with both at 100%
                        y1 = 62.0f * (loggedStats[i]!!.stamina / STAMINA_MAX)
                        y2 = 62.0f * (loggedStats[i + 1]!!.stamina / STAMINA_MAX)
                        Line(x1, y1, x2, y2, imageBuff!!, -0x10000)
                        y1 = 63.0f * (loggedStats[i]!!.combat / COMBAT_MAX)
                        y2 = 63.0f * (loggedStats[i + 1]!!.combat / COMBAT_MAX)
                        Line(x1, y1, x2, y2, imageBuff!!, -0xff0001)
                        i++
                    }
                    val stage = background!!.GetStage(0)
                    stage?.texture?.image?.get(0)?.UploadScratch(wrapToNativeBuffer(itob(imageBuff!!)), 512, 64)
                    //                    Mem_Free(imageBuff);
                    imageBuff = null
                }
            }
        }

        override fun ParseInternalVar(_name: String?, src: idParser): Boolean {
            if (Icmp(_name!!, "markerMat") == 0) {
                val str = idStr()
                ParseString(src, str)
                markerMat = DeclManager.declManager.FindMaterial(str)
                markerMat!!.SetSort(Material.SS_GUI.toFloat())
                return true
            }
            if (Icmp(_name, "markerStop") == 0) {
                val str = idStr()
                ParseString(src, str)
                markerStop = DeclManager.declManager.FindMaterial(str)
                markerStop!!.SetSort(Material.SS_GUI.toFloat())
                return true
            }
            if (Icmp(_name, "markerColor") == 0) {
                ParseVec4(src, markerColor!!)
                return true
            }
            return super.ParseInternalVar(_name, src)
        }

        private fun CommonInit() {
            numStats = 0
            currentTime = -1
            currentMarker = -1
            stopTime = -1
            imageBuff = null
            markerMat = null
            markerStop = null
        }

        private fun Line(x1: Int, y1: Int, x2: Int, y2: Int, out: IntArray, color: Int) {
            var x1 = x1
            var y1 = y1
            var deltax = abs((x2 - x1).toFloat()).toInt()
            var deltay = abs((y2 - y1).toFloat()).toInt()
            val incx = if (x1 > x2) -1 else 1
            val incy = if (y1 > y2) -1 else 1
            val right: Int
            val up: Int
            var dir: Int
            if (deltax > deltay) {
                right = deltay * 2
                up = right - deltax * 2
                dir = right - deltax
                while (deltax-- >= 0) {
                    Point(x1, y1, out, color)
                    x1 += incx
                    y1 += if (dir > 0) incy else 0
                    dir += if (dir > 0) up else right
                }
            } else {
                right = deltax * 2
                up = right - deltay * 2
                dir = right - deltay
                while (deltay-- >= 0) {
                    Point(x1, y1, out, color)
                    x1 += if (dir > 0) incx else 0
                    y1 += incy
                    dir += if (dir > 0) up else right
                }
            }
        }

        private fun Line(x1: Float, y1: Float, x2: Float, y2: Float, out: IntArray, color: Int) {
            this.Line(x1.toInt(), y1.toInt(), x2.toInt(), y2.toInt(), out, color)
        }

        private fun Point(x: Int, y: Int, out: IntArray, color: Int) {
            val index = (63 - y) * 512 + x
            if (index >= 0 && index < 512 * 64) {
                out[index] = color
            } else {
                common.Warning("Out of bounds on point %d : %d", x, y)
            }
        }

        companion object {
            const val COMBAT_MAX = 100
            const val HEALTH_MAX = 100
            const val RATE_MAX = 125
            const val STAMINA_MAX = 12
        }
    }
}

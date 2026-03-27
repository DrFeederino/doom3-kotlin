/*
===========================================================================

Doom 3 GPL Source Code
Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.

This file is part of the Doom 3 GPL Source Code ("Doom 3 Source Code").

Doom 3 Source Code is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

Doom 3 Source Code is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with Doom 3 Source Code.  If not, see <http://www.gnu.org/licenses/>.

In addition, the Doom 3 Source Code is also subject to certain additional terms. You should have received a copy of these additional terms immediately following the terms and conditions of the GNU General Public License which accompanied the Doom 3 Source Code.  If not, please request a copy in writing from id Software at the address below.

If you have questions concerning this license or the applicable additional terms, you may contact in writing id Software LLC, c/o ZeniMax Media Inc., Suite 120, Rockville, Maryland 20850 USA.

===========================================================================
*/

package neo.sys

import neo.Renderer.glConfig
import neo.Renderer.r_logFile
import neo.Renderer.tr
import neo.TempDump
import neo.framework.Common.Companion.common
import neo.framework.FileSystem_h
import neo.framework.Licensee.ENGINE_VERSION
import neo.framework.MACOS_X
import neo.framework.UsercmdGen
import neo.idlib.Text.Str.idStr
import neo.idlib.idLib
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.glfw.GLFWGammaRamp
import org.lwjgl.glfw.GLFWImage
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11.*
import org.lwjgl.system.MemoryUtil
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer
import java.nio.channels.FileChannel
import java.nio.file.Paths
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger
import javax.imageio.ImageIO


object win_glimp {
    private val ospath: StringBuilder = StringBuilder(FileSystem_h.MAX_OSPATH)
    var errorCallback: GLFWErrorCallback? = null
    var window: Long = 0
    private var initialFrames = 0
    private var isEnabled = false
    var gammaOrigError = false
    var gammaOrigSet = false
    var gammaOrigRed: ShortArray = ShortArray(256)
    var gammaOrigGreen: ShortArray = ShortArray(256)
    var gammaOrigBlue: ShortArray = ShortArray(256)


    private fun loadIcoAndSetWindowIcon(window: Long) {
        try {
            val stream = win_glimp.javaClass.classLoader.getResourceAsStream("neo/sys/RC/res/doom.ico") ?: return
            val icoData = stream.readAllBytes()
            stream.close()

            val ico = ByteBuffer.wrap(icoData).order(ByteOrder.LITTLE_ENDIAN)

            // ICO Header
            ico.short // reserved
            val type = ico.short.toInt()
            val count = ico.short.toInt()
            if (type != 1 || count <= 0) return

            // Read directory entries
            data class IcoEntry(val width: Int, val height: Int, val dataSize: Int, val dataOffset: Int)

            val entries = ArrayList<IcoEntry>(count)
            for (i in 0 until count) {
                val w = ico.get().toInt() and 0xFF
                val h = ico.get().toInt() and 0xFF
                ico.get() // colorCount
                ico.get() // reserved
                ico.short // planes
                ico.short // bitCount
                val size = ico.int
                val offset = ico.int
                entries.add(IcoEntry(if (w == 0) 256 else w, if (h == 0) 256 else h, size, offset))
            }

            // Parse each icon entry into RGBA pixel data
            val rgbaBuffers = ArrayList<Triple<Int, Int, ByteBuffer>>()

            for (entry in entries) {
                try {
                    // Check for PNG magic bytes
                    if (entry.dataSize >= 8 &&
                        icoData[entry.dataOffset] == 0x89.toByte() &&
                        icoData[entry.dataOffset + 1] == 0x50.toByte()
                    ) {
                        val img = ImageIO.read(
                            ByteArrayInputStream(icoData, entry.dataOffset, entry.dataSize)
                        ) ?: continue
                        val w = img.width
                        val h = img.height
                        val argbPixels = img.getRGB(0, 0, w, h, null, 0, w)
                        val rgba = MemoryUtil.memAlloc(w * h * 4)
                        for (pixel in argbPixels) {
                            rgba.put(((pixel shr 16) and 0xFF).toByte())
                            rgba.put(((pixel shr 8) and 0xFF).toByte())
                            rgba.put((pixel and 0xFF).toByte())
                            rgba.put(((pixel shr 24) and 0xFF).toByte())
                        }
                        rgba.flip()
                        rgbaBuffers.add(Triple(w, h, rgba))
                        continue
                    }

                    // BMP DIB entry
                    ico.position(entry.dataOffset)
                    ico.int // biSize
                    ico.int // biWidth (use entry.width instead — more reliable)
                    ico.int // biHeight (doubled for XOR+AND mask)
                    ico.short // biPlanes
                    val biBitCount = ico.short.toInt()
                    ico.int // biCompression
                    ico.int // biSizeImage
                    ico.int // biXPelsPerMeter
                    ico.int // biYPelsPerMeter
                    val biClrUsed = ico.int
                    ico.int // biClrImportant

                    val w = entry.width
                    val h = entry.height

                    // Read color palette (BGRA, 4 bytes per entry)
                    val paletteCount = if (biBitCount <= 8) {
                        if (biClrUsed > 0) biClrUsed else (1 shl biBitCount)
                    } else 0

                    val palette = IntArray(paletteCount)
                    for (j in 0 until paletteCount) {
                        val b = ico.get().toInt() and 0xFF
                        val g = ico.get().toInt() and 0xFF
                        val r = ico.get().toInt() and 0xFF
                        ico.get() // reserved
                        palette[j] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                    }

                    // Read pixel data (bottom-up rows)
                    val pixels = IntArray(w * h) // ARGB
                    val xorRowSize = ((w * biBitCount + 31) / 32) * 4
                    val pixelDataStart = ico.position()

                    for (y in 0 until h) {
                        // Row 0 in file = bottom row of image, so map to pixels[(h-1-y)*w..]
                        ico.position(pixelDataStart + y * xorRowSize)
                        val destY = h - 1 - y
                        for (x in 0 until w) {
                            pixels[destY * w + x] = when (biBitCount) {
                                8 -> palette[ico.get().toInt() and 0xFF]
                                4 -> {
                                    val byteVal = if (x % 2 == 0) {
                                        ico.get().toInt() and 0xFF
                                    } else {
                                        icoData[ico.position() - 1].toInt() and 0xFF
                                    }
                                    val idx = if (x % 2 == 0) (byteVal shr 4) else (byteVal and 0x0F)
                                    palette[idx]
                                }

                                32 -> {
                                    val b = ico.get().toInt() and 0xFF
                                    val g = ico.get().toInt() and 0xFF
                                    val r = ico.get().toInt() and 0xFF
                                    val a = ico.get().toInt() and 0xFF
                                    (a shl 24) or (r shl 16) or (g shl 8) or b
                                }

                                24 -> {
                                    val b = ico.get().toInt() and 0xFF
                                    val g = ico.get().toInt() and 0xFF
                                    val r = ico.get().toInt() and 0xFF
                                    (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                                }

                                else -> 0
                            }
                        }
                    }

                    // Read AND mask (1bpp, bottom-up) for transparency
                    val andMaskStart = pixelDataStart + h * xorRowSize
                    val andRowSize = ((w + 31) / 32) * 4

                    for (y in 0 until h) {
                        val destY = h - 1 - y
                        val rowOffset = andMaskStart + y * andRowSize
                        for (x in 0 until w) {
                            val byteIdx = x / 8
                            val bitIdx = 7 - (x % 8)
                            val maskByte = icoData[rowOffset + byteIdx].toInt() and 0xFF
                            if ((maskByte shr bitIdx) and 1 == 1) {
                                pixels[destY * w + x] = pixels[destY * w + x] and 0x00FFFFFF
                            }
                        }
                    }

                    // Convert ARGB int array to RGBA ByteBuffer
                    val rgba = MemoryUtil.memAlloc(w * h * 4)
                    for (pixel in pixels) {
                        rgba.put(((pixel shr 16) and 0xFF).toByte()) // R
                        rgba.put(((pixel shr 8) and 0xFF).toByte())  // G
                        rgba.put((pixel and 0xFF).toByte())           // B
                        rgba.put(((pixel shr 24) and 0xFF).toByte())  // A
                    }
                    rgba.flip()
                    rgbaBuffers.add(Triple(w, h, rgba))

                } catch (e: Exception) {
                    continue
                }
            }

            if (rgbaBuffers.isEmpty()) return

            // Create GLFWImage.Buffer with all icon sizes
            val images = GLFWImage.malloc(rgbaBuffers.size)
            for (i in rgbaBuffers.indices) {
                val (w, h, rgba) = rgbaBuffers[i]
                images.position(i).width(w).height(h).pixels(rgba)
            }
            images.position(0)

            glfwSetWindowIcon(window, images)

            // Free native resources
            images.free()
            for ((_, _, rgba) in rgbaBuffers) {
                MemoryUtil.memFree(rgba)
            }

        } catch (e: Exception) {
            common.Printf("Failed to load window icon: %s\n", e.message ?: "unknown error")
        }
    }

    /*
     ===================
     GLW_SetFullScreen
     ===================
     */
    fun GLW_SetFullScreen(parms: glimpParms_t): Boolean {
        glfwDefaultWindowHints()
        glfwSetErrorCallback(GLFWErrorCallback.createPrint(System.err).set())
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE)

        if (window == 0L) {
            window = glfwCreateWindow(
                parms.width,
                parms.height,
                ENGINE_VERSION,
                if (parms.fullScreen) glfwGetPrimaryMonitor() else MemoryUtil.NULL,
                MemoryUtil.NULL
            )
            loadIcoAndSetWindowIcon(window)
        }

        if (MACOS_X) {
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4)
            glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3)
            glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)
            glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE)
            glfwGetWindowContentScale(window, glConfig.scaleX, glConfig.scaleY)
            // get scale factor and update width and height by scale factor of each axis
            parms.width = (parms.width * glConfig.scaleX[0]).toInt()
            parms.height = (parms.height * glConfig.scaleY[0]).toInt()
            glConfig.vidWidth = (glConfig.vidWidth * glConfig.scaleX[0]).toInt()
            glConfig.vidHeight = (glConfig.vidHeight * glConfig.scaleX[0]).toInt()
        }

        glfwMakeContextCurrent(window)
        GL.createCapabilities()
        glViewport(0, 0, parms.width, parms.height)
        glfwShowWindow(window)
        glfwFocusWindow(window)
        glfwPollEvents()

        glfwSetInputMode(window, GLFW_LOCK_KEY_MODS, GLFW_TRUE)
        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED)
        glfwSetKeyCallback(window, UsercmdGen.usercmdGen.keyboardCallback)
        //glfwSetCharCallback(window, UsercmdGen.usercmdGen.keyboardCharCallback)
        glfwSetCursorPosCallback(window, UsercmdGen.usercmdGen.mouseCursorCallback)
        glfwSetScrollCallback(window, UsercmdGen.usercmdGen.mouseScrollCallback)
        glfwSetMouseButtonCallback(window, UsercmdGen.usercmdGen.mouseButtonCallback)
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)

        idLib.common.Printf("ok\n")
        return true
    }

    /*
     ===================
     GLimp_Init

     This is the platform specific OpenGL initialization function.  It
     is responsible for loading OpenGL, initializing it,
     creating a window of the appropriate size, doing
     fullscreen manipulations, etc.  Its overall responsibility is
     to make sure that a functional OpenGL subsystem is operating
     when it returns to the ref.

     If there is any failure, the renderer will revert back to safe
     parameters and try again.
     ===================
     */

    fun GLimp_Init(parms: glimpParms_t): Boolean {
        common.Printf("Initializing OpenGL subsystem\n")

        if (!glfwInit())
            throw IllegalStateException("Unable to initialize GLFW")

        var colorbits = 24
        var depthbits = 24
        var stencilbits = 8

        for (i in 0 until 16) {
            val multisamples = parms.multiSamples
            if (i % 4 == 0 && i != 0) {
                // one pass, reduce
                when (i / 4) {
                    2 -> if (colorbits == 24) colorbits = 16
                    1 -> {
                        if (depthbits == 24) depthbits = 16 else if (depthbits == 16) depthbits = 8
                        if (stencilbits == 24) stencilbits = 16 else if (stencilbits == 16) stencilbits = 8
                    }

                    3 -> if (stencilbits == 24) stencilbits = 16 else if (stencilbits == 16) stencilbits = 8
                }
            }

            var tcolorbits = colorbits
            var tdepthbits = depthbits
            var tstencilbits = stencilbits

            if (i % 4 == 3) {
                // reduce colorbits
                if (tcolorbits == 24) tcolorbits = 16
            }

            if (i % 4 == 2) {
                // reduce depthbits
                if (tdepthbits == 24) tdepthbits = 16 else if (tdepthbits == 16) tdepthbits = 8
            }

            if (i % 4 == 1) {
                // reduce stencilbits
                tstencilbits = if (tstencilbits == 24) 16 else if (tstencilbits == 16) 8 else 0
            }

            var channelcolorbits = 4
            if (tcolorbits == 24) channelcolorbits = 8

            val talphabits = channelcolorbits

            glfwWindowHint(GLFW_RED_BITS, channelcolorbits)
            glfwWindowHint(GLFW_GREEN_BITS, channelcolorbits)
            glfwWindowHint(GLFW_BLUE_BITS, channelcolorbits)
            glfwWindowHint(GLFW_DOUBLEBUFFER, 1)
            glfwWindowHint(GLFW_DEPTH_BITS, tdepthbits)
            glfwWindowHint(GLFW_STENCIL_BITS, tstencilbits)
            glfwWindowHint(GLFW_ALPHA_BITS, talphabits)
            glfwWindowHint(GLFW_STEREO, if (parms.stereo) 1 else 0)
            glfwWindowHint(GLFW_SAMPLES, multisamples)
        }


        if (!GLW_SetFullScreen(parms)) {
            GLimp_Shutdown()
            return false
        }

        if (window == 0L) {
            common.Warning("No usable GL mode found: %d", glGetError())
            return false
        }

        return true
    }

    fun GLimp_GrabInput(flags: Integer) {
        if (window == 0L) {
            common.Warning("GLimp_GrabInput called without window")
            return
        }

        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED)
    }

    // If the desired mode can't be set satisfactorily, false will be returned.
    // The renderer will then reset the glimpParms to "safe mode" of 640x480
    // fullscreen and try again.  If that also fails, the error will be fatal.
    fun GLimp_SetGamma(red: ShortArray, green: ShortArray, blue: ShortArray) {
        if (window == 0L) {
            common.Warning("GLimp_SetGamma called without window")
            return
        }

        val monitor = glfwGetPrimaryMonitor()
        if (monitor == MemoryUtil.NULL) {
            common.Warning("GLimp_SetGamma: no primary monitor")
            return
        }

        if (!gammaOrigSet) {
            gammaOrigSet = true
            val origRamp = glfwGetGammaRamp(monitor)
            if (origRamp == null) {
                gammaOrigError = true
                common.Warning("Failed to get Gamma Ramp\n")
            } else {
                gammaOrigRed = origRamp.red().toArray()
                gammaOrigGreen = origRamp.green().toArray()
                gammaOrigBlue = origRamp.blue().toArray()
            }
        }

        try {
            val gammaRamp = GLFWGammaRamp.create()
            gammaRamp.size(red.size)
            gammaRamp.red(ShortBuffer.wrap(red))
                .green(ShortBuffer.wrap(green))
                .blue(ShortBuffer.wrap(blue))
            glfwSetGammaRamp(monitor, gammaRamp)
        } catch (e: Exception) {
            common.Warning("Couldn't set gamma ramp: %s", e.message ?: "unknown error")
        }
    }

    fun ShortBuffer.toArray(): ShortArray {
        val shortArray = ShortArray(this.capacity())
        for (i in 0 until capacity()) {
            shortArray[i] = this[i]
        }

        return shortArray
    }
    /*
     ===================
     GLimp_Shutdown

     This routine does all OS specific shutdown procedures for the OpenGL
     subsystem.
     ===================
     */

    fun GLimp_Shutdown() {
        glfwDestroyWindow(window)
        window = 0L
        glfwTerminate()
    }

    /*
     =================
     GLimp_ResetGamma

     Restore original system gamma setting
     =================
     */
    fun GLimp_ResetGamma() {
        if (gammaOrigError) {
            common.Warning("Can't reset hardware gamma because getting the Gamma Ramp at startup failed!\n")
            common.Warning("You might have to restart the game for gamma/brightness in shaders to work properly.\n")
            return
        }

        if (gammaOrigSet) {
            val monitor = glfwGetPrimaryMonitor()
            if (monitor != MemoryUtil.NULL) {
                val gammaRamp = GLFWGammaRamp.create()
                gammaRamp.size(gammaOrigRed.size)
                gammaRamp.red(ShortBuffer.wrap(gammaOrigRed))
                gammaRamp.green(ShortBuffer.wrap(gammaOrigGreen))
                gammaRamp.blue(ShortBuffer.wrap(gammaOrigBlue))
                glfwSetGammaRamp(monitor, gammaRamp)
            }
        }
    }


    fun GLimp_SwapBuffers() {
        var error = glGetError()
        if (error > 0) {
            common.Warning("GL Error: %d", error)
        }
        glfwSwapBuffers(window)
        glfwPollEvents()
    }

    // These are used for managing SMP handoffs of the OpenGL context
    // between threads, and as a performance tunining aid.  Setting
    // 'r_skipRenderContext 1' will call GLimp_DeactivateContext() before
    // the 3D rendering code, and GLimp_ActivateContext() afterwards.  On
    // most OpenGL implementations, this will result in all OpenGL calls
    // being immediate returns, which lets us guage how much time is
    // being spent inside OpenGL.

    fun GLimp_EnableLogging(enable: Boolean) { //TODO:activate this function. EDIT:make sure it works.
        var enable = enable
        try {
            // return if we're already active
            if (isEnabled && enable) {
                // decrement log counter and stop if it has reached 0
                r_logFile!!.SetInteger(r_logFile!!.GetInteger() - 1)
                if (r_logFile!!.GetInteger() != 0) {
                    return
                }
                idLib.common.Printf("closing logfile '%s' after %d frames.\n", ospath, initialFrames)
                enable = false
                tr.logFile!!.close()
                tr.logFile = null
            }

            // return if we're already disabled
            if (!enable && !isEnabled) {
                return
            }
            isEnabled = enable
            if (enable) {
                if (tr.logFile == null) {
//			struct tm		*newtime;
//			ID_TIME_T			aclock;
                    var qpath = ""
                    var i: Int
                    val path: String
                    initialFrames = r_logFile!!.GetInteger()

                    // scan for an unused filename
                    i = 0
                    while (i < 9999) {
                        qpath = String.format("renderlog_%d.txt", i)
                        if (FileSystem_h.fileSystem.ReadFile(qpath, null, null) == -1) {
                            break // use this name
                        }
                        i++
                    }
                    path = FileSystem_h.fileSystem.RelativePathToOSPath(qpath, "fs_savepath")
                    idStr.Copynz(ospath, path)
                    tr.logFile = FileChannel.open(Paths.get(ospath.toString()), TempDump.fopenOptions("wt"))

                    // write the time out to the top of the file
//			time( &aclock );
//			newtime = localtime( &aclock );
                    tr.logFile!!.write(TempDump.atobb(String.format("// %s", Date())))
                    tr.logFile!!.write(
                        TempDump.atobb(
                            String.format(
                                "// %s\n\n",
                                idLib.cvarSystem.GetCVarString("si_version")
                            )
                        )
                    )
                }
            }
        } catch (ex: IOException) {
            Logger.getLogger(win_glimp::class.java.name).log(Level.SEVERE, null, ex)
            idLib.common.Warning("---GLimp_EnableLogging---\n%s\n---", ex.message!!)
        }
    }


    fun GLimp_DeactivateContext() {
        common.DPrintf("TODO: GLimp_ActivateContext\n")
    }


    fun GLimp_ActivateContext() {
        common.DPrintf("TODO: GLimp_DeactivateContext\n")
    }


    fun GLimp_SetScreenParms(parms: glimpParms_t) {
        common.DPrintf("TODO: GLimp_SetScreenParms\n")
    }

    /*
     ====================================================================

     IMPLEMENTATION SPECIFIC FUNCTIONS

     ====================================================================
     */
    class glimpParms_t {
        var displayHz = 0
        var fullScreen = false
        var height = 0
        var multiSamples = 0
        var stereo = false
        var width = 0
    }
}
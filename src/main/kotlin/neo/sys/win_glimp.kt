package neo.sys

import neo.Renderer.r_logFile
import neo.Renderer.tr
import neo.TempDump
import neo.framework.Common.Companion.common
import neo.framework.FileSystem_h
import neo.framework.UsercmdGen
import neo.idlib.Text.Str.idStr
import neo.idlib.idLib
import org.lwjgl.PointerBuffer
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.glfw.GLFWGammaRamp
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11.*
import org.lwjgl.system.MemoryUtil
import java.io.IOException
import java.nio.ShortBuffer
import java.nio.channels.FileChannel
import java.nio.file.Paths
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger


object win_glimp {
    private val ospath: StringBuilder = StringBuilder(FileSystem_h.MAX_OSPATH)
    var errorCallback: GLFWErrorCallback? = null
    var window: Long = 0
    private var initialFrames = 0
    private var isEnabled = false
    private val d3_ico_resource = win_glimp.javaClass.classLoader.getResourceAsStream("neo/sys/RC/res/doom.ico")

    //    private val d3_icon = GLFWImage.Buffer(ByteBuffer.wrap(d3_ico_resource.readAllBytes()))
    var gammaOrigError = false
    var gammaOrigSet = false
    var gammaOrigRed: ShortArray = ShortArray(256)
    var gammaOrigGreen: ShortArray = ShortArray(256)
    var gammaOrigBlue: ShortArray = ShortArray(256)


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
                "DOOM 3",
                if (parms.fullScreen) glfwGetPrimaryMonitor() else MemoryUtil.NULL,
                MemoryUtil.NULL
            )
        }

        glfwMakeContextCurrent(window)
        GL.createCapabilities()
        glViewport(0, 0, parms.width, parms.height)
        glfwShowWindow(window)
        glfwFocusWindow(window)
        glfwPollEvents()

        glfwSetInputMode(window, GLFW_LOCK_KEY_MODS, GLFW_FALSE)
        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED)
        glfwSetKeyCallback(window, UsercmdGen.usercmdGen.keyboardCallback)
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

        val gammaRamp = glfwGetGammaRamp(window)

        if (!gammaOrigSet) {
            gammaOrigSet = true
            if (gammaRamp == null) {
                gammaOrigError = true
                common.Warning("Failed to get Gamma Ramp: %s\n", glfwGetError(PointerBuffer.allocateDirect(1024)))
            }
        }

        if (gammaRamp != null) {
            gammaOrigRed = gammaRamp.red().toArray()
            gammaOrigGreen = gammaRamp.green().toArray()
            gammaOrigBlue = gammaRamp.blue().toArray()

            gammaRamp.red(ShortBuffer.wrap(red))
                .green(ShortBuffer.wrap(green))
                .blue(ShortBuffer.wrap(blue))

            if (glfwSetGammaRamp(window, gammaRamp) == null) {
                common.Warning("Couldn't set gamma ramp: %s", glfwGetError(PointerBuffer.allocateDirect(1024)))
            }
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

    // Destroys the rendering context, closes the window, resets the resolution,
    // and resets the gamma ramps.
    fun GLimp_ResetGamma() {
        if (gammaOrigError) {
            common.Warning("Can't reset hardware gamma because getting the Gamma Ramp at startup failed!\n")
            common.Warning("You might have to restart the game for gamma/brightness in shaders to work properly.\n")
            return
        }

        if (gammaOrigSet) {
            val gammaRamp = GLFWGammaRamp.create()
            gammaRamp.red(ShortBuffer.wrap(gammaOrigRed))
            gammaRamp.green(ShortBuffer.wrap(gammaOrigGreen))
            gammaRamp.blue(ShortBuffer.wrap(gammaOrigBlue))
            glfwSetGammaRamp(window, gammaRamp)
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
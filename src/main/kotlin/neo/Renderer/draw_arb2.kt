/*
===========================================================================

Doom 3 GPL Source Code
Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
Translated to Kotlin by Dr. Feederino with support of Claude Code

This file is part of the Doom 3 GPL Source Code ("Doom 3 Source Code").
Original source: neo/renderer/draw_arb2.cpp

===========================================================================
*/

package neo.Renderer

import neo.Renderer.Material.stageVertexColor_t
import neo.Renderer.qgl.qglProgramEnvParameter4fvARB
import neo.Renderer.tr_render.DrawInteraction
import neo.framework.CmdSystem.cmdFunction_t
import neo.framework.Common
import neo.framework.FileSystem_h.fileSystem
import neo.idlib.CmdArgs
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Str.idStr.Companion.Icmp
import neo.idlib.geometry.DrawVert.idDrawVert
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.ARBFragmentProgram.GL_FRAGMENT_PROGRAM_ARB
import org.lwjgl.opengl.ARBMultitexture
import org.lwjgl.opengl.ARBVertexProgram
import org.lwjgl.opengl.GL11
import java.nio.ByteBuffer

object draw_arb2 {
    const val MAX_GLPROGS = 200

    /*
     =========================================================================================

     GENERAL INTERACTION RENDERING

     =========================================================================================
     */
    private val NEG_ONE = floatArrayOf(-1.0f, -1.0f, -1.0f, -1.0f)
    private val ONE = floatArrayOf(1.0f, 1.0f, 1.0f, 1.0f)

    //
    private val ZERO = floatArrayOf(0.0f, 0.0f, 0.0f, 0.0f)

    // a single file can have both a vertex program and a fragment program
    var progs = Array<progDef_t>(MAX_GLPROGS) { progDef_t() }

    //
    init {
        var a = 0
        progs[a++] = progDef_t(ARBVertexProgram.GL_VERTEX_PROGRAM_ARB, program_t.VPROG_TEST, "test.vfp")
        progs[a++] = progDef_t(GL_FRAGMENT_PROGRAM_ARB, program_t.FPROG_TEST, "test.vfp")
        progs[a++] = progDef_t(ARBVertexProgram.GL_VERTEX_PROGRAM_ARB, program_t.VPROG_INTERACTION, "interaction.vfp")
        progs[a++] =
            progDef_t(GL_FRAGMENT_PROGRAM_ARB, program_t.FPROG_INTERACTION, "interaction.vfp")
        progs[a++] =
            progDef_t(ARBVertexProgram.GL_VERTEX_PROGRAM_ARB, program_t.VPROG_BUMPY_ENVIRONMENT, "bumpyEnvironment.vfp")
        progs[a++] = progDef_t(
            GL_FRAGMENT_PROGRAM_ARB,
            program_t.FPROG_BUMPY_ENVIRONMENT,
            "bumpyEnvironment.vfp"
        )
        progs[a++] = progDef_t(ARBVertexProgram.GL_VERTEX_PROGRAM_ARB, program_t.VPROG_AMBIENT, "ambientLight.vfp")
        progs[a++] = progDef_t(GL_FRAGMENT_PROGRAM_ARB, program_t.FPROG_AMBIENT, "ambientLight.vfp")
        progs[a++] = progDef_t(ARBVertexProgram.GL_VERTEX_PROGRAM_ARB, program_t.VPROG_STENCIL_SHADOW, "shadow.vp")
        progs[a++] = progDef_t(ARBVertexProgram.GL_VERTEX_PROGRAM_ARB, program_t.VPROG_ENVIRONMENT, "environment.vfp")
        progs[a++] =
            progDef_t(GL_FRAGMENT_PROGRAM_ARB, program_t.FPROG_ENVIRONMENT, "environment.vfp")
        progs[a++] = progDef_t(ARBVertexProgram.GL_VERTEX_PROGRAM_ARB, program_t.VPROG_GLASSWARP, "arbVP_glasswarp.txt")
        progs[a++] =
            progDef_t(GL_FRAGMENT_PROGRAM_ARB, program_t.FPROG_GLASSWARP, "arbFP_glasswarp.txt")

        // additional programs can be dynamically specified in materials
    }

    /*
     ====================
     GL_SelectTextureNoClient
     ====================
     */
    fun GL_SelectTextureNoClient(unit: Int) {
        backEnd!!.glState.currenttmu = unit
        qgl.qglActiveTextureARB(ARBMultitexture.GL_TEXTURE0_ARB + unit)
    }

    private fun findLineThatStartsWith(text: String, findMe: String): Int {
        var res = text.indexOf(findMe)
        while (res != -1) {
            // skip whitespace before match, if any
            var cur = res
            if (cur > 0) cur--
            while (cur > 0 && (text[cur] == ' ' || text[cur] == '\t')) {
                cur--
            }
            // now we should be at a newline (or at the beginning)
            if (cur == 0) return cur
            if (text[cur] == '\n' || text[cur] == '\r') return cur + 1
            // otherwise maybe we're in commented out text or whatever, search on
            res = text.indexOf(findMe, res + 1)
        }
        return -1
    }

    private fun isARBidentifierChar(c: Int): Boolean {
        // according to chapter 3.11.2 in ARB_fragment_program.txt identifiers can only
        // contain these chars (first char mustn't be a number, but that doesn't matter here)
        return c == '$'.code || c == '_'.code
                || (c in '0'.code..'9'.code)
                || (c in 'A'.code..'Z'.code)
                || (c in 'a'.code..'z'.code)
    }

    /*
     =============
     RB_ARB2_CreateDrawInteractions

     =============
     */
    fun RB_ARB2_CreateDrawInteractions(surf: drawSurf_s?) {
        var surf = surf
        if (surf == null) {
            return
        }

        // perform setup here that will be constant for all interactions
        tr_backend.GL_State(GLS_SRCBLEND_ONE or GLS_DSTBLEND_ONE or GLS_DEPTHMASK or backEnd!!.depthFunc)

        // bind the vertex program
        if (r_testARBProgram.GetBool()) {
            qgl.qglBindProgramARB(ARBVertexProgram.GL_VERTEX_PROGRAM_ARB, program_t.VPROG_TEST)
            qgl.qglBindProgramARB(GL_FRAGMENT_PROGRAM_ARB, program_t.FPROG_TEST)
        } else {
            qgl.qglBindProgramARB(ARBVertexProgram.GL_VERTEX_PROGRAM_ARB, program_t.VPROG_INTERACTION)
            qgl.qglBindProgramARB(GL_FRAGMENT_PROGRAM_ARB, program_t.FPROG_INTERACTION)
        }

        qgl.qglEnable(ARBVertexProgram.GL_VERTEX_PROGRAM_ARB)
        qgl.qglEnable(GL_FRAGMENT_PROGRAM_ARB)

        // enable the vertex arrays
        qgl.qglEnableVertexAttribArrayARB(8)
        qgl.qglEnableVertexAttribArrayARB(9)
        qgl.qglEnableVertexAttribArrayARB(10)
        qgl.qglEnableVertexAttribArrayARB(11)
        qgl.qglEnableClientState(GL11.GL_COLOR_ARRAY)

        // texture 0 is the normalization cube map for the vector towards the light
        GL_SelectTextureNoClient(0)
        if (backEnd!!.vLight!!.lightShader!!.IsAmbientLight()) {
            Image.globalImages.ambientNormalMap!!.Bind()
        } else {
            Image.globalImages.normalCubeMapImage!!.Bind()
        }

        // texture 6 is the specular lookup table
        GL_SelectTextureNoClient(6)
        if (r_testARBProgram.GetBool()) {
            Image.globalImages.specular2DTableImage!!.Bind() // variable specularity in alpha channel
        } else {
            Image.globalImages.specularTableImage!!.Bind()
        }
        while (surf != null) {
            // perform setup here that will not change over multiple interaction passes

            // set the vertex pointers
            val ac =
                idDrawVert(VertexCache.vertexCache.Position(surf.geo!!.ambientCache))
            qgl.qglColorPointer(4, GL11.GL_UNSIGNED_BYTE, idDrawVert.BYTES, ac.colorOffset().toLong())
            qgl.qglVertexAttribPointerARB(11, 3, GL11.GL_FLOAT, false, idDrawVert.BYTES, ac.normalOffset().toLong())
            qgl.qglVertexAttribPointerARB(10, 3, GL11.GL_FLOAT, false, idDrawVert.BYTES, ac.tangentsOffset_1().toLong())
            qgl.qglVertexAttribPointerARB(9, 3, GL11.GL_FLOAT, false, idDrawVert.BYTES, ac.tangentsOffset_0().toLong())
            qgl.qglVertexAttribPointerARB(8, 2, GL11.GL_FLOAT, false, idDrawVert.BYTES, ac.stOffset().toLong())
            qgl.qglVertexPointer(3, GL11.GL_FLOAT, idDrawVert.BYTES, ac.xyzOffset().toLong())

            // this may cause RB_ARB2_DrawInteraction to be exacuted multiple
            // times with different colors and images if the surface or light have multiple layers
            tr_render.RB_CreateSingleDrawInteractions(surf, RB_ARB2_DrawInteraction.INSTANCE)
            surf = surf.nextOnLight
        }
        qgl.qglDisableVertexAttribArrayARB(8)
        qgl.qglDisableVertexAttribArrayARB(9)
        qgl.qglDisableVertexAttribArrayARB(10)
        qgl.qglDisableVertexAttribArrayARB(11)
        qgl.qglDisableClientState(GL11.GL_COLOR_ARRAY)

        // disable features
        GL_SelectTextureNoClient(6)
        Image.globalImages.BindNull()

        GL_SelectTextureNoClient(5)
        Image.globalImages.BindNull()

        GL_SelectTextureNoClient(4)
        Image.globalImages.BindNull()

        GL_SelectTextureNoClient(3)
        Image.globalImages.BindNull()

        GL_SelectTextureNoClient(2)
        Image.globalImages.BindNull()

        GL_SelectTextureNoClient(1)
        Image.globalImages.BindNull()

        backEnd!!.glState.currenttmu = -1
        tr_backend.GL_SelectTexture(0)

        qgl.qglDisable(ARBVertexProgram.GL_VERTEX_PROGRAM_ARB)
        qgl.qglDisable(GL_FRAGMENT_PROGRAM_ARB)
    }

    /*
     ==================
     RB_ARB2_DrawInteractions
     ==================
     */
    fun RB_ARB2_DrawInteractions() {
        var vLight: viewLight_s?

        tr_backend.GL_SelectTexture(0)
        qgl.qglDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY)

        //
        // for each light, perform adding and shadowing
        //
        vLight = backEnd!!.viewDef!!.viewLights
        while (vLight != null) {
            backEnd!!.vLight = vLight

            // do fogging later
            if (vLight.lightShader!!.IsFogLight()) {
                vLight = vLight.next
                continue
            }
            if (vLight.lightShader!!.IsBlendLight()) {
                vLight = vLight.next
                continue
            }
            if (vLight.localInteractions[0] == null && vLight.globalInteractions[0] == null
                && vLight.translucentInteractions[0] == null
            ) {
                vLight = vLight.next
                continue
            }

            // clear the stencil buffer if needed
            if (vLight.globalShadows[0] != null || vLight.localShadows[0] != null) {
                backEnd!!.currentScissor = vLight.scissorRect
                if (r_useScissor.GetBool()) {
                    qgl.qglScissor(
                        backEnd!!.viewDef!!.viewport.x1 + backEnd!!.currentScissor!!.x1,
                        backEnd!!.viewDef!!.viewport.y1 + backEnd!!.currentScissor!!.y1,
                        backEnd!!.currentScissor!!.x2 + 1 - backEnd!!.currentScissor!!.x1,
                        backEnd!!.currentScissor!!.y2 + 1 - backEnd!!.currentScissor!!.y1
                    )
                }
                qgl.qglClear(GL11.GL_STENCIL_BUFFER_BIT)
            } else {
                // no shadows, so no need to read or write the stencil buffer
                // we might in theory want to use GL_ALWAYS instead of disabling
                // completely, to satisfy the invarience rules
                qgl.qglStencilFunc(GL11.GL_ALWAYS, 128, 255)
            }

            if (r_useShadowVertexProgram.GetBool()) {
                qgl.qglEnable(ARBVertexProgram.GL_VERTEX_PROGRAM_ARB)
                qgl.qglBindProgramARB(ARBVertexProgram.GL_VERTEX_PROGRAM_ARB, program_t.VPROG_STENCIL_SHADOW)
                draw_common.RB_StencilShadowPass(vLight.globalShadows[0])
                RB_ARB2_CreateDrawInteractions(vLight.localInteractions[0])
                qgl.qglEnable(ARBVertexProgram.GL_VERTEX_PROGRAM_ARB)
                qgl.qglBindProgramARB(ARBVertexProgram.GL_VERTEX_PROGRAM_ARB, program_t.VPROG_STENCIL_SHADOW)
                draw_common.RB_StencilShadowPass(vLight.localShadows[0])
                RB_ARB2_CreateDrawInteractions(vLight.globalInteractions[0])
                qgl.qglDisable(ARBVertexProgram.GL_VERTEX_PROGRAM_ARB) // if there weren't any globalInteractions, it would have stayed on
            } else {
                draw_common.RB_StencilShadowPass(vLight.globalShadows[0])
                RB_ARB2_CreateDrawInteractions(vLight.localInteractions[0])
                draw_common.RB_StencilShadowPass(vLight.localShadows[0])
                RB_ARB2_CreateDrawInteractions(vLight.globalInteractions[0])
            }

            // translucent surfaces never get stencil shadowed
            if (r_skipTranslucent.GetBool()) {
                vLight = vLight.next
                continue
            }

            qgl.qglStencilFunc(GL11.GL_ALWAYS, 128, 255)

            backEnd!!.depthFunc = GLS_DEPTHFUNC_LESS
            RB_ARB2_CreateDrawInteractions(vLight.translucentInteractions[0])

            backEnd!!.depthFunc = GLS_DEPTHFUNC_EQUAL
            vLight = vLight.next
        }

        // disable stencil shadow test
        qgl.qglStencilFunc(GL11.GL_ALWAYS, 128, 255)

        tr_backend.GL_SelectTexture(0)
        qgl.qglEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY)
    }

    /*
     =================
     R_LoadARBProgram
     =================
     */
    fun R_LoadARBProgram(progIndex: Int) {
        val ofs = BufferUtils.createIntBuffer(16)
        val err: Int
        val fullPath = idStr("glprogs/" + progs[progIndex].name)
        val fileBuffer = arrayOf<ByteBuffer?>(null)
        var buffer: String
        var start = 0
        val end: Int
        Common.common.Printf("%s", fullPath)

        // load the program even if we don't support it, so
        // fs_copyfiles can generate cross-platform data dumps
        fileSystem.ReadFile(fullPath.toString(), fileBuffer, null)
        if (fileBuffer[0] == null) {
            Common.common.Printf(": File not found\n")
            return
        }

        // copy to stack memory and free
        buffer = String(fileBuffer[0]!!.array())
        if (!glConfig.isInitialized) {
            return
        }

        //
        // submit the program string at start to GL
        //
        if (progs[progIndex].ident == program_t.PROG_INVALID.ordinal) {
            // allocate a new identifier for this program
            progs[progIndex].ident = program_t.PROG_USER.ordinal + progIndex
        }

        // vertex and fragment programs can both be present in a single file, so
        // scan for the proper header to be the start point, and stamp a 0 in after the end

        if (progs[progIndex].target == ARBVertexProgram.GL_VERTEX_PROGRAM_ARB) {
            if (!glConfig.ARBVertexProgramAvailable) {
                Common.common.Printf(": GL_VERTEX_PROGRAM_ARB not available\n")
                return
            }
            start = buffer.indexOf("!!ARBvp")
        }
        if (progs[progIndex].target == GL_FRAGMENT_PROGRAM_ARB) {
            if (!glConfig.ARBFragmentProgramAvailable) {
                Common.common.Printf(": GL_FRAGMENT_PROGRAM_ARB not available\n")
                return
            }
            start = buffer.indexOf("!!ARBfp")
        }
        if (-1 == start) {
            Common.common.Printf(": !!ARB not found\n")
            return
        }
        val endIdx = buffer.substring(start).indexOf("END")
        if (endIdx == -1) {
            Common.common.Printf(": END not found\n")
            return
        }
        end = start + endIdx
        buffer = buffer.substring(start, end + 3)

        // DG: hack gamma correction into shader
        if (r_gammaInShader.GetBool() && progs[progIndex].target == GL_FRAGMENT_PROGRAM_ARB &&
            buffer.indexOf("nodhewm3gammahack") == -1
        ) {

            // note that strlen("dhewm3tmpres") == strlen("result.color")
            val tmpres = "TEMP dhewm3tmpres; # injected by dhewm3 for gamma correction\n"

            // Note: program.env[PP_GAMMA_BRIGHTNESS].xyz = r_brightness; program.env[PP_GAMMA_BRIGHTNESS].w = 1.0/r_gamma
            // outColor.rgb = pow(dhewm3tmpres.rgb*r_brightness, vec3(1.0/r_gamma))
            // outColor.a = dhewm3tmpres.a;
            val extraLines =
                "# gamma correction in shader, injected by dhewm3 \n" +
                        // MUL_SAT clamps the result to [0, 1] - it must not be negative because
                        // POW might not work with a negative base (it looks wrong with intel's Linux driver)
                        // and clamping values >1 to 1 is ok because when writing to result.color
                        // it's clamped anyway and pow(base, exp) is always >= 1 for base >= 1
                        "MUL_SAT dhewm3tmpres.xyz, program.env[21], dhewm3tmpres;\n" + // first multiply with brightness
                        "POW result.color.x, dhewm3tmpres.x, program.env[21].w;\n" + // then do pow(dhewm3tmpres.xyz, vec3(1/gamma))
                        "POW result.color.y, dhewm3tmpres.y, program.env[21].w;\n" + // (apparently POW only supports scalars, not whole vectors)
                        "POW result.color.z, dhewm3tmpres.z, program.env[21].w;\n" +
                        "MOV result.color.w, dhewm3tmpres.w;\n" + // alpha remains unmodified
                        "\nEND\n\n" // we add this block right at the end, replacing the original "END" string

            val fullLen = buffer.length + tmpres.length + extraLines.length
            val outStr = StringBuilder(fullLen + 1)

            // add tmpres right after OPTION line (if any)
            var insertPos = findLineThatStartsWith(buffer, "OPTION")
            if (insertPos == -1) {
                // no OPTION? then just put it after the first line (usually sth like "!!ARBfp1.0\n")
                insertPos = 0
            }
            // but we want the position *after* that line
            while (buffer[insertPos] != '\n' && buffer[insertPos] != '\r') {
                ++insertPos
            }
            // skip the newline character(s) as well
            while (buffer[insertPos] == '\n' || buffer[insertPos] == '\r') {
                ++insertPos
            }

            // copy text up to insertPos
            outStr.append(buffer, 0, insertPos)
            // copy tmpres ("TEMP dhewm3tmpres; # ..")
            outStr.append(tmpres)
            // copy remaining original shader up to (excluding) "END"
            outStr.append(buffer.substring(insertPos, buffer.indexOf("END")))

            // replace all existing occurrences of "result.color" with "dhewm3tmpres"
            // and handle "OUTPUT bla = result.color;" -> "ALIAS  bla = dhewm3tmpres;"
            var resIdx = outStr.indexOf("result.color")
            while (resIdx != -1) {
                outStr.replace(resIdx, resIdx + 12, "dhewm3tmpres")

                // if this was part of "OUTPUT bla = result.color;", replace
                // "OUTPUT bla" with "ALIAS  bla" (so it becomes "ALIAS  bla = dhewm3tmpres;")
                var s = resIdx - 1
                // first skip whitespace before "dhewm3tmpres" (was "result.color")
                while (s > 0 && (outStr[s] == ' ' || outStr[s] == '\t')) {
                    --s
                }
                // if there's no '=' before result.color, this line can't be affected
                if (s > 0 && outStr[s] == '=' && s > 8) {
                    --s // we were on '=', so go to the char before and skip whitespace again
                    while (s > 0 && (outStr[s] == ' ' || outStr[s] == '\t')) {
                        --s
                    }
                    // now we should be at the end of "bla" (or however the variable/alias is called)
                    if (s > 7 && isARBidentifierChar(outStr[s].code)) {
                        --s
                        // skip all the remaining chars that are legal in identifiers
                        while (s > 0 && isARBidentifierChar(outStr[s].code)) {
                            --s
                        }
                        // there should be at least one space/tab between "OUTPUT" and "bla"
                        if (s > 6 && (outStr[s] == ' ' || outStr[s] == '\t')) {
                            --s
                            // skip remaining whitespace (if any)
                            while (s > 0 && (outStr[s] == ' ' || outStr[s] == '\t')) {
                                --s
                            }
                            // now we should be at "OUTPUT" (specifically at its last 'T'),
                            // if this is indeed such a case
                            if (s >= 5 && outStr[s] == 'T') {
                                val outputStart = s - 5
                                if (outStr.substring(outputStart, outputStart + 6) == "OUTPUT") {
                                    // it really is "OUTPUT" => replace "OUTPUT" with "ALIAS "
                                    outStr.replace(outputStart, outputStart + 6, "ALIAS ")
                                }
                            }
                        }
                    }
                }

                resIdx = outStr.indexOf("result.color", resIdx + 13)
            }

            assert(outStr.length <= fullLen)

            // now add extraLines that calculate and set a gamma-corrected result.color
            outStr.append(extraLines)
            buffer = outStr.toString()
        }

        // create the ByteBuffer AFTER potential gamma injection so the GPU gets the modified shader
        val substring = BufferUtils.createByteBuffer(buffer.length)
        substring.put(buffer.toByteArray()).flip()

        qgl.qglBindProgramARB(progs[progIndex].target, progs[progIndex].ident)
        qgl.qglGetError()
        qgl.qglProgramStringARB(
            progs[progIndex].target,
            ARBVertexProgram.GL_PROGRAM_FORMAT_ASCII_ARB,
            start,
            substring
        )

        err = qgl.qglGetError()
        qgl.qglGetIntegerv(ARBVertexProgram.GL_PROGRAM_ERROR_POSITION_ARB, ofs)
        if (err == GL11.GL_INVALID_OPERATION) {
            val str = qgl.qglGetString(ARBVertexProgram.GL_PROGRAM_ERROR_STRING_ARB)
            Common.common.Printf("\nGL_PROGRAM_ERROR_STRING_ARB: %s\n", str!!)
            if (ofs[0] < 0) {
                Common.common.Printf("GL_PROGRAM_ERROR_POSITION_ARB < 0 with error\n")
            } else if (ofs[0] >= buffer.length - start) {
                Common.common.Printf("error at end of program\n")
            } else {
                val printOfs = maxOf(ofs[0] - 20, 0)
                Common.common.Printf("error at %d:\n%s", ofs[0], buffer.substring(printOfs))
            }
            return
        }
        if (ofs[0] != -1) {
            Common.common.Printf("\nGL_PROGRAM_ERROR_POSITION_ARB != -1 without error\n")
            return
        }
        Common.common.Printf("\n")
    }

    /*
     ==================
     R_FindARBProgram

     Returns a GL identifier that can be bound to the given target, parsing
     a text file if it hasn't already been loaded.
     ==================
     */
    fun R_FindARBProgram( /*GLenum */
                          target: Int, program: String
    ): Int {
        var i: Int
        val stripped = idStr(program)

        stripped.StripFileExtension()

        // see if it is already loaded
        i = 0
        while (progs[i].name.isNotEmpty()) {
            if (progs[i].target != target) {
                i++
                continue
            }
            val compare = idStr(progs[i].name)
            compare.StripFileExtension()
            if (Icmp(stripped, compare) == 0) {
                return progs[i].ident
            }
            i++
        }

        if (i == MAX_GLPROGS) {
            Common.common.Error("R_FindARBProgram: MAX_GLPROGS")
        }

        // add it to the list and load it
        progs[i] = progDef_t(target, program_t.PROG_INVALID, program) // will be gen'd by R_LoadARBProgram

        R_LoadARBProgram(i)

        return progs[i].ident
    }

    /*
     ==================
     R_ARB2_Init

     ==================
     */
    fun R_ARB2_Init() {
        glConfig.allowARB2Path = false

        Common.common.Printf("ARB2 renderer: ")

        if (!glConfig.ARBVertexProgramAvailable || !glConfig.ARBFragmentProgramAvailable) {
            Common.common.Printf("Not available.\n")
            return
        }
        Common.common.Printf("Available.\n")

        glConfig.allowARB2Path = true
    }

    /*
     ==================
     RB_ARB2_DrawInteraction
     ==================
     */
    internal class RB_ARB2_DrawInteraction private constructor() : DrawInteraction() {
        override fun run(din: drawInteraction_t) {
            DBG_RB_ARB2_DrawInteraction++
            // load all the vertex program parameters
            qglProgramEnvParameter4fvARB(
                ARBVertexProgram.GL_VERTEX_PROGRAM_ARB,
                programParameter_t.PP_LIGHT_ORIGIN,
                din.localLightOrigin.ToFloatPtr()
            )
            qglProgramEnvParameter4fvARB(
                ARBVertexProgram.GL_VERTEX_PROGRAM_ARB,
                programParameter_t.PP_VIEW_ORIGIN,
                din.localViewOrigin.ToFloatPtr()
            )
            qglProgramEnvParameter4fvARB(
                ARBVertexProgram.GL_VERTEX_PROGRAM_ARB,
                programParameter_t.PP_LIGHT_PROJECT_S,
                din.lightProjection[0]!!.ToFloatPtr()
            )
            qglProgramEnvParameter4fvARB(
                ARBVertexProgram.GL_VERTEX_PROGRAM_ARB,
                programParameter_t.PP_LIGHT_PROJECT_T,
                din.lightProjection[1]!!.ToFloatPtr()
            )
            qglProgramEnvParameter4fvARB(
                ARBVertexProgram.GL_VERTEX_PROGRAM_ARB,
                programParameter_t.PP_LIGHT_PROJECT_Q,
                din.lightProjection[2]!!.ToFloatPtr()
            )
            qglProgramEnvParameter4fvARB(
                ARBVertexProgram.GL_VERTEX_PROGRAM_ARB,
                programParameter_t.PP_LIGHT_FALLOFF_S,
                din.lightProjection[3]!!.ToFloatPtr()
            )
            qglProgramEnvParameter4fvARB(
                ARBVertexProgram.GL_VERTEX_PROGRAM_ARB,
                programParameter_t.PP_BUMP_MATRIX_S,
                din.bumpMatrix[0].ToFloatPtr()
            )
            qglProgramEnvParameter4fvARB(
                ARBVertexProgram.GL_VERTEX_PROGRAM_ARB,
                programParameter_t.PP_BUMP_MATRIX_T,
                din.bumpMatrix[1].ToFloatPtr()
            )
            qglProgramEnvParameter4fvARB(
                ARBVertexProgram.GL_VERTEX_PROGRAM_ARB,
                programParameter_t.PP_DIFFUSE_MATRIX_S,
                din.diffuseMatrix[0].ToFloatPtr()
            )
            qglProgramEnvParameter4fvARB(
                ARBVertexProgram.GL_VERTEX_PROGRAM_ARB,
                programParameter_t.PP_DIFFUSE_MATRIX_T,
                din.diffuseMatrix[1].ToFloatPtr()
            )
            qglProgramEnvParameter4fvARB(
                ARBVertexProgram.GL_VERTEX_PROGRAM_ARB,
                programParameter_t.PP_SPECULAR_MATRIX_S,
                din.specularMatrix[0].ToFloatPtr()
            )
            qglProgramEnvParameter4fvARB(
                ARBVertexProgram.GL_VERTEX_PROGRAM_ARB,
                programParameter_t.PP_SPECULAR_MATRIX_T,
                din.specularMatrix[1].ToFloatPtr()
            )

            // testing fragment based normal mapping
            if (r_testARBProgram.GetBool()) {
                qglProgramEnvParameter4fvARB(
                    GL_FRAGMENT_PROGRAM_ARB,
                    2,
                    din.localLightOrigin.ToFloatPtr()
                )
                qglProgramEnvParameter4fvARB(
                    GL_FRAGMENT_PROGRAM_ARB,
                    3,
                    din.localViewOrigin.ToFloatPtr()
                )
            }
            when (din.vertexColor) {
                stageVertexColor_t.SVC_IGNORE -> {
                    qglProgramEnvParameter4fvARB(
                        ARBVertexProgram.GL_VERTEX_PROGRAM_ARB,
                        programParameter_t.PP_COLOR_MODULATE,
                        ZERO
                    )
                    qglProgramEnvParameter4fvARB(
                        ARBVertexProgram.GL_VERTEX_PROGRAM_ARB,
                        programParameter_t.PP_COLOR_ADD,
                        ONE
                    )
                }

                stageVertexColor_t.SVC_MODULATE -> {
                    qglProgramEnvParameter4fvARB(
                        ARBVertexProgram.GL_VERTEX_PROGRAM_ARB,
                        programParameter_t.PP_COLOR_MODULATE,
                        ONE
                    )
                    qglProgramEnvParameter4fvARB(
                        ARBVertexProgram.GL_VERTEX_PROGRAM_ARB,
                        programParameter_t.PP_COLOR_ADD,
                        ZERO
                    )
                }

                stageVertexColor_t.SVC_INVERSE_MODULATE -> {
                    qglProgramEnvParameter4fvARB(
                        ARBVertexProgram.GL_VERTEX_PROGRAM_ARB,
                        programParameter_t.PP_COLOR_MODULATE,
                        NEG_ONE
                    )
                    qglProgramEnvParameter4fvARB(
                        ARBVertexProgram.GL_VERTEX_PROGRAM_ARB,
                        programParameter_t.PP_COLOR_ADD,
                        ONE
                    )
                }
            }

            // set the constant colors
            qglProgramEnvParameter4fvARB(
                GL_FRAGMENT_PROGRAM_ARB,
                0,
                din.diffuseColor.ToFloatPtr()
            )
            qglProgramEnvParameter4fvARB(
                GL_FRAGMENT_PROGRAM_ARB,
                1,
                din.specularColor.ToFloatPtr()
            )

            // DG: brightness and gamma in shader as program.env[4]
            if (r_gammaInShader.GetBool()) {
                // program.env[4].xyz are all r_brightness, program.env[4].w is 1.0/r_gamma
                val parm = FloatArray(4)
                parm.fill(r_brightness.GetFloat())
                parm[3] =
                    1.0f / r_gamma.GetFloat() // 1.0/gamma so the shader doesn't have to do this calculation
                qglProgramEnvParameter4fvARB(GL_FRAGMENT_PROGRAM_ARB, programParameter_t.PP_GAMMA_BRIGHTNESS, parm)
            }

            // set the textures

            // texture 1 will be the per-surface bump map
            GL_SelectTextureNoClient(1)
            din.bumpImage!!.Bind()

            // texture 2 will be the light falloff texture
            GL_SelectTextureNoClient(2)
            din.lightFalloffImage!!.Bind()

            // texture 3 will be the light projection texture
            GL_SelectTextureNoClient(3)
            din.lightImage!!.Bind()

            // texture 4 is the per-surface diffuse map
            GL_SelectTextureNoClient(4)
            din.diffuseImage!!.Bind()

            // texture 5 is the per-surface specular map
            GL_SelectTextureNoClient(5)
            din.specularImage!!.Bind()

            // draw it
            tr_render.RB_DrawElementsWithCounters(din.surf!!.geo!!)
        }

        companion object {
            val INSTANCE: DrawInteraction = RB_ARB2_DrawInteraction()
            private var DBG_RB_ARB2_DrawInteraction = 0
        }
    }

    //===================================================================================
    class progDef_t(
        var target: Int, var ident: Int, // char			name[64];
        var name: String
    ) {
        constructor() : this(0, 0, "")
        constructor(target: Int, ident: program_t, name: String) : this(target, ident.ordinal, name)
    }

    /*
     ==================
     R_ReloadARBPrograms_f
     ==================
     */
    class R_ReloadARBPrograms_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            var i: Int
            Common.common.Printf("----- R_ReloadARBPrograms -----\n")
            i = 0
            while (progs[i].name.isNotEmpty()) {
                R_LoadARBProgram(i)
                i++
            }
        }

        companion object {
            val instance: cmdFunction_t = R_ReloadARBPrograms_f()
        }
    }
}

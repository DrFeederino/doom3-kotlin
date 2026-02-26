package neo.Tools.Compilers.DMap

import neo.Renderer.qgl
import neo.Renderer.tr_backend
import neo.TempDump.TODO_Exception
import neo.Tools.Compilers.DMap.dmap.mapTri_s
import neo.idlib.geometry.Winding.idWinding
import neo.idlib.math.idVec3
import org.lwjgl.opengl.GL11

object gldraw {
    const val GLSERV_PORT = 25001
    const val WIN_SIZE = 1024
    var draw_socket = 0
    var wins_init = false
    fun Draw_ClearWindow() {
        if (!dmap.dmapGlobals.drawflag) {
            return
        }
        GL11.glDrawBuffer(GL11.GL_FRONT)
        tr_backend.RB_SetGL2D()
        GL11.glClearColor(0.5f, 0.5f, 0.5f, 0f)
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT)
        GL11.glMatrixMode(GL11.GL_PROJECTION)
        GL11.glLoadIdentity()
        GL11.glOrtho(
            dmap.dmapGlobals.drawBounds[0, 0].toDouble(),
            dmap.dmapGlobals.drawBounds[1, 0].toDouble(),
            dmap.dmapGlobals.drawBounds[0, 1].toDouble(),
            dmap.dmapGlobals.drawBounds[1, 1].toDouble(),
            -1.0,
            1.0
        )
        GL11.glMatrixMode(GL11.GL_MODELVIEW)
        GL11.glLoadIdentity()
        //#endif
        GL11.glColor3f(0f, 0f, 0f)
        //	glPolygonMode (GL_FRONT_AND_BACK, GL_LINE);
        GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL)
        GL11.glDisable(GL11.GL_DEPTH_TEST)
        //	glEnable (GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
        GL11.glFlush()
    }

    fun Draw_SetRed() {
        if (!dmap.dmapGlobals.drawflag) {
            return
        }
        GL11.glColor3f(1f, 0f, 0f)
    }

    fun Draw_SetGrey() {
        if (!dmap.dmapGlobals.drawflag) {
            return
        }
        GL11.glColor3f(0.5f, 0.5f, 0.5f)
    }

    fun Draw_SetBlack() {
        if (!dmap.dmapGlobals.drawflag) {
            return
        }
        GL11.glColor3f(0.0f, 0.0f, 0.0f)
    }

    fun DrawWinding(w: idWinding) {
        var i: Int
        if (!dmap.dmapGlobals.drawflag) {
            return
        }
        GL11.glColor3f(0.3f, 0.0f, 0.0f)
        GL11.glBegin(GL11.GL_POLYGON)
        i = 0
        while (i < w.GetNumPoints()) {
            GL11.glVertex3f(w[i][0], w[i][1], w[i][2])
            i++
        }
        GL11.glEnd()
        GL11.glColor3f(1f, 0f, 0f)
        GL11.glBegin(GL11.GL_LINE_LOOP)
        i = 0
        while (i < w.GetNumPoints()) {
            GL11.glVertex3f(w[i][0], w[i][1], w[i][2])
            i++
        }
        GL11.glEnd()
        GL11.glFlush()
    }

    fun DrawAuxWinding(w: idWinding) {
        var i: Int
        if (!dmap.dmapGlobals.drawflag) {
            return
        }
        GL11.glColor3f(0.0f, 0.3f, 0.0f)
        GL11.glBegin(GL11.GL_POLYGON)
        i = 0
        while (i < w.GetNumPoints()) {
            GL11.glVertex3f(w[i][0], w[i][1], w[i][2])
            i++
        }
        GL11.glEnd()
        GL11.glColor3f(0.0f, 1.0f, 0.0f)
        GL11.glBegin(GL11.GL_LINE_LOOP)
        i = 0
        while (i < w.GetNumPoints()) {
            GL11.glVertex3f(w[i][0], w[i][1], w[i][2])
            i++
        }
        GL11.glEnd()
        GL11.glFlush()
    }

    fun DrawLine(v1: idVec3, v2: idVec3, color: Int) {
        if (!dmap.dmapGlobals.drawflag) {
            return
        }
        when (color) {
            0 -> GL11.glColor3f(0f, 0f, 0f)
            1 -> GL11.glColor3f(0f, 0f, 1f)
            2 -> GL11.glColor3f(0f, 1f, 0f)
            3 -> GL11.glColor3f(0f, 1f, 1f)
            4 -> GL11.glColor3f(1f, 0f, 0f)
            5 -> GL11.glColor3f(1f, 0f, 1f)
            6 -> GL11.glColor3f(1f, 1f, 0f)
            7 -> GL11.glColor3f(1f, 1f, 1f)
        }
        GL11.glBegin(GL11.GL_LINES)
        qgl.qglVertex3fv(v1.ToFloatPtr())
        qgl.qglVertex3fv(v2.ToFloatPtr())
        GL11.glEnd()
        GL11.glFlush()
    }

    fun GLS_BeginScene() {
        throw TODO_Exception()
    }

    fun GLS_Winding(w: idWinding, code: Int) {
        throw TODO_Exception()
    }

    fun GLS_Triangle(tri: mapTri_s, code: Int) {
        throw TODO_Exception()
    }

    fun GLS_EndScene() {
        throw TODO_Exception()
    }
}
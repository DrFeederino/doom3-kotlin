package neo.Renderer

import neo.Renderer.Material.idMaterial
import neo.Renderer.Model.srfTriangles_s
import neo.Renderer.RenderWorld.renderEntity_s
import neo.Renderer.tr_main.myGlMultMatrix
import neo.framework.DeclManager
import neo.framework.DemoFile.idDemoFile
import neo.idlib.containers.CInt
import neo.idlib.containers.List.idList
import neo.idlib.geometry.DrawVert.idDrawVert
import neo.idlib.geometry.Winding.idFixedWinding
import neo.idlib.math.idPlane
import neo.idlib.math.idVec2
import neo.idlib.math.idVec5

class GuiModel {
    internal class guiModelSurface_t {
        val color = FloatArray(4)
        var firstIndex = 0
        var firstVert = 0
        var material: idMaterial? = null
        var numIndexes = 0
        var numVerts = 0
    }

    class idGuiModel {
        //
        //
        private val indexes: idList<Int>

        //
        private val surfaces: idList<guiModelSurface_t>
        private val verts: idList<idDrawVert>
        private var surf: guiModelSurface_t? = null

        init {
            surfaces = idList()
            indexes = idList(1000) //.SetGranularity(1000);
            verts = idList(1000) //.SetGranularity(1000);
        }

        /*
         ================
         idGuiModel::Clear

         Begins collecting draw commands into surfaces
         ================
         */
        fun Clear() {
            surfaces.SetNum(0, false)
            indexes.SetNum(0, false)
            verts.SetNum(0, false)
            AdvanceSurf()
            //            if (bla) {
            clear++
            //            }
        }

        fun WriteToDemo(demo: idDemoFile?) {
            var i: Int
            var j: Int
            i = verts.Num()
            demo!!.WriteInt(i)
            j = 0
            while (j < i) {
                demo.WriteVec3(verts[j].xyz)
                demo.WriteVec2(verts[j].st)
                demo.WriteVec3(verts[j].normal)
                demo.WriteVec3(verts[j].tangents[0])
                demo.WriteVec3(verts[j].tangents[1])
                demo.WriteUnsignedChar(Char(verts[j].color[0].toUShort()))
                demo.WriteUnsignedChar(Char(verts[j].color[1].toUShort()))
                demo.WriteUnsignedChar(Char(verts[j].color[2].toUShort()))
                demo.WriteUnsignedChar(Char(verts[j].color[3].toUShort()))
                j++
            }
            i = indexes.Num()
            demo.WriteInt(i)
            j = 0
            while (j < i) {
                demo.WriteInt(indexes[j])
                j++
            }
            i = surfaces.Num()
            demo.WriteInt(i)
            j = 0
            while (j < i) {
                val surf = surfaces[j]

//                demo.WriteInt((int) surf.material);
                demo.Write(surf.material!!)
                demo.WriteFloat(surf.color[0])
                demo.WriteFloat(surf.color[1])
                demo.WriteFloat(surf.color[2])
                demo.WriteFloat(surf.color[3])
                demo.WriteInt(surf.firstVert)
                demo.WriteInt(surf.numVerts)
                demo.WriteInt(surf.firstIndex)
                demo.WriteInt(surf.numIndexes)
                demo.WriteHashString(surf.material!!.GetName())
                j++
            }
        }

        fun ReadFromDemo(demo: idDemoFile) {
            val i = CInt()
            var j: Int
            val k = CInt()
            val color = charArrayOf(0.toChar())
            i._val = verts.Num()
            demo.ReadInt(i)
            verts.SetNum(i._val, false)
            j = 0
            while (j < i._val) {
                demo.ReadVec3(verts[j].xyz)
                demo.ReadVec2(verts[j].st)
                demo.ReadVec3(verts[j].normal)
                demo.ReadVec3(verts[j].tangents[0])
                demo.ReadVec3(verts[j].tangents[1])
                demo.ReadUnsignedChar(color)
                verts[j].color[0] = color[0].code.toByte()
                demo.ReadUnsignedChar(color)
                verts[j].color[1] = color[0].code.toByte()
                demo.ReadUnsignedChar(color)
                verts[j].color[2] = color[0].code.toByte()
                demo.ReadUnsignedChar(color)
                verts[j].color[3] = color[0].code.toByte()
                j++
            }
            i._val = indexes.Num()
            demo.ReadInt(i)
            indexes.SetNum(i._val, false)
            j = 0
            while (j < i._val) {
                demo.ReadInt(k)
                indexes[j] = k._val
                j++
            }
            i._val = surfaces.Num()
            demo.ReadInt(i)
            surfaces.SetNum(i._val, false)
            j = 0
            while (j < i._val) {
                val surf = surfaces[j]

//                demo.ReadInt((int) surf.material);
                demo.Read(surf.material!!) //TODO:serialize?
                surf.color[0] = demo.ReadFloat()
                surf.color[1] = demo.ReadFloat()
                surf.color[2] = demo.ReadFloat()
                surf.color[3] = demo.ReadFloat()
                surf.firstVert = demo.ReadInt()
                surf.numVerts = demo.ReadInt()
                surf.firstIndex = demo.ReadInt()
                surf.numIndexes = demo.ReadInt()
                surf.material = DeclManager.declManager.FindMaterial(demo.ReadHashString())
                j++
            }
        }

        fun EmitToCurrentView(modelMatrix: FloatArray /*[16]*/, depthHack: Boolean) {
            val modelViewMatrix = FloatArray(16)
            var worldMVM = if (r_lockSurfaces.GetBool() && tr.viewDef == tr.primaryView) {
                tr.lockSurfacesRealViewDef?.worldSpace?.modelViewMatrix
            } else {
                tr.viewDef!!.worldSpace.modelViewMatrix
            }

            // DG: for r_lockSurfaces use the real world modelViewMatrix
            //     so GUIs don't float around
            if (r_lockSurfaces.GetBool() && tr.viewDef == tr.primaryView) {
                worldMVM = tr.lockSurfacesRealViewDef?.worldSpace?.modelViewMatrix
            }

            myGlMultMatrix(modelMatrix, worldMVM!!, modelViewMatrix)

            for (i in 0 until surfaces.Num()) {
                EmitSurface(surfaces[i], modelMatrix, modelViewMatrix, depthHack)
            }
        }

        /*
         ================
         idGuiModel::EmitFullScreen

         Creates a view that covers the screen and emit the surfaces
         ================
         */
        fun EmitFullScreen() {
            val viewDef: viewDef_s
            if (surfaces[0].numVerts == 0) {
                return
            }
            viewDef = viewDef_s() //R_ClearedFrameAlloc(sizeof(viewDef));

            // for gui editor
            if (null == tr.viewDef || !tr.viewDef!!.isEditor) {
                viewDef.renderView.x = 0
                viewDef.renderView.y = 0
                viewDef.renderView.width = RenderSystem.SCREEN_WIDTH
                viewDef.renderView.height = RenderSystem.SCREEN_HEIGHT
                tr.RenderViewToViewport(viewDef.renderView, viewDef.viewport)
                viewDef.scissor.x1 = 0
                viewDef.scissor.y1 = 0
                viewDef.scissor.x2 = viewDef.viewport.x2 - viewDef.viewport.x1
                viewDef.scissor.y2 = viewDef.viewport.y2 - viewDef.viewport.y1
            } else {
                viewDef.renderView.x = tr.viewDef!!.renderView.x
                viewDef.renderView.y = tr.viewDef!!.renderView.y
                viewDef.renderView.width = tr.viewDef!!.renderView.width
                viewDef.renderView.height = tr.viewDef!!.renderView.height
                viewDef.viewport.x1 = tr.viewDef!!.renderView.x
                viewDef.viewport.x2 = tr.viewDef!!.renderView.x + tr.viewDef!!.renderView.width
                viewDef.viewport.y1 = tr.viewDef!!.renderView.y
                viewDef.viewport.y2 = tr.viewDef!!.renderView.y + tr.viewDef!!.renderView.height
                viewDef.scissor.x1 = tr.viewDef!!.scissor.x1
                viewDef.scissor.y1 = tr.viewDef!!.scissor.y1
                viewDef.scissor.x2 = tr.viewDef!!.scissor.x2
                viewDef.scissor.y2 = tr.viewDef!!.scissor.y2
            }
            viewDef.floatTime = tr.frameShaderTime

            // qglOrtho( 0, 640, 480, 0, 0, 1 );		// always assume 640x480 virtual coordinates
            viewDef.projectionMatrix[0] = +2.0f / 640.0f
            viewDef.projectionMatrix[5] = -2.0f / 480.0f
            viewDef.projectionMatrix[10] = -2.0f / 1.0f
            viewDef.projectionMatrix[12] = -1.0f
            viewDef.projectionMatrix[13] = +1.0f
            viewDef.projectionMatrix[14] = -1.0f
            viewDef.projectionMatrix[15] = +1.0f
            viewDef.worldSpace.modelViewMatrix[0] = 1.0f
            viewDef.worldSpace.modelViewMatrix[5] = 1.0f
            viewDef.worldSpace.modelViewMatrix[10] = 1.0f
            viewDef.worldSpace.modelViewMatrix[15] = 1.0f
            viewDef.maxDrawSurfs = surfaces.Num()
            viewDef.drawSurfs =
                drawSurf_s.generateArray(viewDef.maxDrawSurfs) ///*(drawSurf_t **)*/ R_FrameAlloc(viewDef!!.maxDrawSurfs * sizeof(viewDef!!.drawSurfs[0]));
            viewDef.numDrawSurfs = 0
            val oldViewDef = tr.viewDef
            tr.viewDef = viewDef

            // add the surfaces to this view
            for (i in 0 until surfaces.Num()) {
                if (i == 33) {
                    surfaces[i].material!!.DBG_BALLS = i
                }
                EmitSurface(surfaces[i], viewDef.worldSpace.modelMatrix, viewDef.worldSpace.modelViewMatrix, false)
            }
            tr.viewDef = oldViewDef

            // add the command to draw this view
            RenderSystem.R_AddDrawViewCmd(viewDef)
        }

        // these calls are forwarded from the renderer
        fun SetColor(r: Float, g: Float, b: Float, a: Float) {
            setColorTotal++
            if (!glConfig.isInitialized) {
                return
            }
            if (r == surf!!.color[0] && g == surf!!.color[1] && b == surf!!.color[2] && a == surf!!.color[3]) {
                return  // no change
            }
            if (surf!!.numVerts != 0) {
//                if (bla) {
//                }
//                TempDump.printCallStack(setColorTotal + "");
//                System.out.printf("%d\n", setColorTotal);
                AdvanceSurf()
                setColor++
            }

            // change the parms
            surf!!.color[0] = r
            surf!!.color[1] = g
            surf!!.color[2] = b
            surf!!.color[3] = a
        }

        fun DrawStretchPic(
            dVerts: Array<idDrawVert>?,
            dIndexes: IntArray?,
            vertCount: Int,
            indexCount: Int,
            hShader: idMaterial?,
            clip: Boolean /*= true*/,
            min_x: Float /*= 0.0f*/,
            min_y: Float /*= 0.0f*/,
            max_x: Float /*= 640.0f*/,
            max_y: Float /*= 480.0f*/
        ) {
//            TempDump.printCallStack(bla4+"");
            bla4++
            if (!glConfig.isInitialized) {
                return
            }
            if (!(dVerts != null && dIndexes != null && vertCount != 0 && indexCount != 0 && hShader != null)) {
                return
            }

            // break the current surface if we are changing to a new material
//                    if (bla) {
//                    }
//            System.out.printf("%s\n%s\n\n", hShader, surf.material);
            if (hShader !== surf!!.material) {
                if (surf!!.numVerts != 0) {
                    AdvanceSurf()
                    //                    if (bla) {
//                    System.out.printf("~~ %d %d\n", Window.idWindow.bla1, Window.idWindow.bla2);
//                    }
                }
                hShader.EnsureNotPurged() // in case it was a gui item started before a level change
                surf!!.material = hShader
                //                TempDump.printCallStack(bla4 + "");
            }

            // add the verts and indexes to the current surface
            if (clip) {
                var i: Int
                var j: Int

                // FIXME:	this is grim stuff, and should be rewritten if we have any significant
                //			number of guis asking for clipping
                val w = idFixedWinding()
                i = 0
                while (i < indexCount) {
                    w.Clear()
                    w.AddPoint(
                        idVec5(
                            dVerts[dIndexes[i + 0]].xyz.x,
                            dVerts[dIndexes[i + 0]].xyz.y,
                            dVerts[dIndexes[i + 0]].xyz.z,
                            dVerts[dIndexes[i + 0]].st.x,
                            dVerts[dIndexes[i + 0]].st.y
                        )
                    )
                    w.AddPoint(
                        idVec5(
                            dVerts[dIndexes[i + 1]].xyz.x,
                            dVerts[dIndexes[i + 1]].xyz.y,
                            dVerts[dIndexes[i + 1]].xyz.z,
                            dVerts[dIndexes[i + 1]].st.x,
                            dVerts[dIndexes[i + 1]].st.y
                        )
                    )
                    w.AddPoint(
                        idVec5(
                            dVerts[dIndexes[i + 2]].xyz.x,
                            dVerts[dIndexes[i + 2]].xyz.y,
                            dVerts[dIndexes[i + 2]].xyz.z,
                            dVerts[dIndexes[i + 2]].st.x,
                            dVerts[dIndexes[i + 2]].st.y
                        )
                    )
                    j = 0
                    while (j < 3) {
                        if (w[j].x < min_x || w[j].x > max_x || w[j].y < min_y || w[j].y > max_y) {
                            break
                        }
                        j++
                    }
                    if (j < 3) {
                        val p = idPlane()
                        p.NormalY(p.NormalZ(0.0f))
                        p.NormalX(1.0f)
                        p.SetDist(min_x)
                        w.ClipInPlace(p)
                        p.NormalY(p.NormalZ(0.0f))
                        p.NormalX(-1.0f)
                        p.SetDist(-max_x)
                        w.ClipInPlace(p)
                        p.NormalX(p.NormalZ(0.0f))
                        p.NormalY(1.0f)
                        p.SetDist(min_y)
                        w.ClipInPlace(p)
                        p.NormalX(p.NormalZ(0.0f))
                        p.NormalY(-1.0f)
                        p.SetDist(-max_y)
                        w.ClipInPlace(p)
                    }
                    val numVerts = verts.Num()
                    verts.SetNum(numVerts + w.GetNumPoints(), false)
                    j = 0
                    while (j < w.GetNumPoints()) {
                        val dv = verts[numVerts + j]
                        dv.xyz.x = w[j].x
                        dv.xyz.y = w[j].y
                        dv.xyz.z = w[j].z
                        dv.st.x = w[j].s
                        dv.st.y = w[j].t
                        dv.normal.set(0.0f, 0.0f, 1.0f)
                        dv.tangents[0].set(1.0f, 0.0f, 0.0f)
                        dv.tangents[1].set(0.0f, 1.0f, 0.0f)
                        j++
                    }
                    surf!!.numVerts += w.GetNumPoints()
                    j = 2
                    while (j < w.GetNumPoints()) {
                        indexes.Append(numVerts - surf!!.firstVert)
                        indexes.Append(numVerts + j - 1 - surf!!.firstVert)
                        indexes.Append(numVerts + j - surf!!.firstVert)
                        surf!!.numIndexes += 3
                        j++
                    }
                    i += 3
                }
            } else {
                drawStretchPic++
                //                if (dVerts[0].xyz.x == 212) {
                val numVerts = verts.Num()
                val numIndexes = indexes.Num()
                verts.AssureSize(numVerts + vertCount)
                indexes.AssureSize(numIndexes + indexCount)
                surf!!.numVerts += vertCount
                surf!!.numIndexes += indexCount
                for (i in 0 until indexCount) {
                    indexes[numIndexes + i] = numVerts + dIndexes[i] - surf!!.firstVert
                }

                //                memcpy( & verts[numVerts], dverts, vertCount * sizeof(verts[0]));
                for (i in 0 until vertCount) {
                    verts[i + numVerts] = idDrawVert(dVerts[i])
                }
                //                }
            }
        }

        /*
         =============
         DrawStretchPic

         x/y/w/h are in the 0,0 to 640,480 range
         =============
         */
        fun DrawStretchPic(
            x: Float,
            y: Float,
            w: Float,
            h: Float,
            s1: Float,
            t1: Float,
            s2: Float,
            t2: Float,
            hShader: idMaterial?
        ) {
            var x = x
            var y = y
            var w = w
            var h = h
            var s1 = s1
            var t1 = t1
            var s2 = s2
            var t2 = t2
            val verts = arrayOf(
                idDrawVert(),
                idDrawVert(),
                idDrawVert(),
                idDrawVert()
            )
            /*glIndex_t*/
            val indexes = IntArray(6)
            if (!glConfig.isInitialized) {
                return
            }
            if (null == hShader) {
                return
            }

            // clip to edges, because the pic may be going into a guiShader
            // instead of full screen
            if (x < 0) {
                s1 += (s2 - s1) * -x / w
                w += x
                x = 0.0f
            }
            if (y < 0) {
                t1 += (t2 - t1) * -y / h
                h += y
                y = 0.0f
            }
            if (x + w > 640) {
                s2 -= (s2 - s1) * (x + w - 640) / w
                w = 640 - x
            }
            if (y + h > 480) {
                t2 -= (t2 - t1) * (y + h - 480) / h
                h = 480 - y
            }
            if (w <= 0 || h <= 0) {
                return  // completely clipped away
            }
            indexes[0] = 3
            indexes[1] = 0
            indexes[2] = 2
            indexes[3] = 2
            indexes[4] = 0
            indexes[5] = 1
            verts[0].xyz[0] = x
            verts[0].xyz[1] = y
            verts[0].xyz[2] = 0.0f
            verts[0].st[0] = s1
            verts[0].st[1] = t1
            verts[0].normal[0] = 0.0f
            verts[0].normal[1] = 0.0f
            verts[0].normal[2] = 1.0f
            verts[0].tangents[0][0] = 1.0f
            verts[0].tangents[0][1] = 0.0f
            verts[0].tangents[0][2] = 0.0f
            verts[0].tangents[1][0] = 0.0f
            verts[0].tangents[1][1] = 1.0f
            verts[0].tangents[1][2] = 0.0f
            verts[1].xyz[0] = x + w
            verts[1].xyz[1] = y
            verts[1].xyz[2] = 0.0f
            verts[1].st[0] = s2
            verts[1].st[1] = t1
            verts[1].normal[0] = 0.0f
            verts[1].normal[1] = 0.0f
            verts[1].normal[2] = 1.0f
            verts[1].tangents[0][0] = 1.0f
            verts[1].tangents[0][1] = 0.0f
            verts[1].tangents[0][2] = 0.0f
            verts[1].tangents[1][0] = 0.0f
            verts[1].tangents[1][1] = 1.0f
            verts[1].tangents[1][2] = 0.0f
            verts[2].xyz[0] = x + w
            verts[2].xyz[1] = y + h
            verts[2].xyz[2] = 0.0f
            verts[2].st[0] = s2
            verts[2].st[1] = t2
            verts[2].normal[0] = 0.0f
            verts[2].normal[1] = 0.0f
            verts[2].normal[2] = 1.0f
            verts[2].tangents[0][0] = 1.0f
            verts[2].tangents[0][1] = 0.0f
            verts[2].tangents[0][2] = 0.0f
            verts[2].tangents[1][0] = 0.0f
            verts[2].tangents[1][1] = 1.0f
            verts[2].tangents[1][2] = 0.0f
            verts[3].xyz[0] = x
            verts[3].xyz[1] = y + h
            verts[3].xyz[2] = 0.0f
            verts[3].st[0] = s1
            verts[3].st[1] = t2
            verts[3].normal[0] = 0.0f
            verts[3].normal[1] = 0.0f
            verts[3].normal[2] = 1.0f
            verts[3].tangents[0][0] = 1.0f
            verts[3].tangents[0][1] = 0.0f
            verts[3].tangents[0][2] = 0.0f
            verts[3].tangents[1][0] = 0.0f
            verts[3].tangents[1][1] = 1.0f
            verts[3].tangents[1][2] = 0.0f
            this.DrawStretchPic(verts /*[0]*/, indexes /*[0]*/, 4, 6, hShader, false, 0.0f, 0.0f, 640.0f, 480.0f)
            bla99++
        }

        /*
         =============
         DrawStretchTri

         x/y/w/h are in the 0,0 to 640,480 range
         =============
         */
        fun DrawStretchTri(
            p1: idVec2,
            p2: idVec2,
            p3: idVec2,
            t1: idVec2,
            t2: idVec2,
            t3: idVec2,
            material: idMaterial?
        ) {
            val tempVerts = Array(3) { idDrawVert() }
            /*glIndex_t*/
            val tempIndexes = IntArray(3)
            val vertCount = 3
            val indexCount = 3
            if (!glConfig.isInitialized) {
                return
            }
            if (null == material) {
                return
            }
            tempIndexes[0] = 1
            tempIndexes[1] = 0
            tempIndexes[2] = 2
            tempVerts[0]!!.xyz[0] = p1.x
            tempVerts[0]!!.xyz[1] = p1.y
            tempVerts[0]!!.xyz[2] = 0.0f
            tempVerts[0]!!.st[0] = t1.x
            tempVerts[0]!!.st[1] = t1.y
            tempVerts[0]!!.normal[0] = 0.0f
            tempVerts[0]!!.normal[1] = 0.0f
            tempVerts[0]!!.normal[2] = 1.0f
            tempVerts[0]!!.tangents[0][0] = 1.0f
            tempVerts[0]!!.tangents[0][1] = 0.0f
            tempVerts[0]!!.tangents[0][2] = 0.0f
            tempVerts[0]!!.tangents[1][0] = 0.0f
            tempVerts[0]!!.tangents[1][1] = 1.0f
            tempVerts[0]!!.tangents[1][2] = 0.0f
            tempVerts[1]!!.xyz[0] = p2.x
            tempVerts[1]!!.xyz[1] = p2.y
            tempVerts[1]!!.xyz[2] = 0.0f
            tempVerts[1]!!.st[0] = t2.x
            tempVerts[1]!!.st[1] = t2.y
            tempVerts[1]!!.normal[0] = 0.0f
            tempVerts[1]!!.normal[1] = 0.0f
            tempVerts[1]!!.normal[2] = 1.0f
            tempVerts[1]!!.tangents[0][0] = 1.0f
            tempVerts[1]!!.tangents[0][1] = 0.0f
            tempVerts[1]!!.tangents[0][2] = 0.0f
            tempVerts[1]!!.tangents[1][0] = 0.0f
            tempVerts[1]!!.tangents[1][1] = 1.0f
            tempVerts[1]!!.tangents[1][2] = 0.0f
            tempVerts[2]!!.xyz[0] = p3.x
            tempVerts[2]!!.xyz[1] = p3.y
            tempVerts[2]!!.xyz[2] = 0.0f
            tempVerts[2]!!.st[0] = t3.x
            tempVerts[2]!!.st[1] = t3.y
            tempVerts[2]!!.normal[0] = 0.0f
            tempVerts[2]!!.normal[1] = 0.0f
            tempVerts[2]!!.normal[2] = 1.0f
            tempVerts[2]!!.tangents[0][0] = 1.0f
            tempVerts[2]!!.tangents[0][1] = 0.0f
            tempVerts[2]!!.tangents[0][2] = 0.0f
            tempVerts[2]!!.tangents[1][0] = 0.0f
            tempVerts[2]!!.tangents[1][1] = 1.0f
            tempVerts[2]!!.tangents[1][2] = 0.0f

            // break the current surface if we are changing to a new material
            if (material !== surf!!.material) {
                if (surf!!.numVerts != 0) {
                    AdvanceSurf()
                    if (bla) {
                        bla4++
                    }
                }
                /*const_cast<idMaterial *>*/material.EnsureNotPurged() // in case it was a gui item started before a level change
                surf!!.material = material
            }
            val numVerts = verts.Num()
            val numIndexes = indexes.Num()
            verts.AssureSize(numVerts + vertCount)
            indexes.AssureSize(numIndexes + indexCount)
            surf!!.numVerts += vertCount
            surf!!.numIndexes += indexCount
            for (i in 0 until indexCount) {
                indexes[numIndexes + i] = numVerts + tempIndexes[i] - surf!!.firstVert
            }

//            memcpy(verts[numVerts], tempVerts, vertCount * sizeof(verts[0]));
            for (i in 0 until vertCount) {
                verts[numVerts + i] = idDrawVert(tempVerts[i]!!)
            }
        }

        //---------------------------
        private fun AdvanceSurf() {
            val s = guiModelSurface_t()
            if (surfaces.Num() != 0) {
                s.color[0] = surf!!.color[0]
                s.color[1] = surf!!.color[1]
                s.color[2] = surf!!.color[2]
                s.color[3] = surf!!.color[3]
                s.material = surf!!.material
            } else {
                s.color[0] = 1.0f
                s.color[1] = 1.0f
                s.color[2] = 1.0f
                s.color[3] = 1.0f
                s.material = tr.defaultMaterial
            }
            s.numIndexes = 0
            s.firstIndex = indexes.Num()
            s.numVerts = 0
            s.firstVert = verts.Num()
            surfaces.Append(s)
            surf = surfaces[surfaces.Num() - 1]
            //            TempDump.printCallStack(bla555 + "");
            setColorTotal
            setColor
            clear
            drawStretchPic
            bla555++
        }

        private fun EmitSurface(
            surf: guiModelSurface_t,
            modelMatrix: FloatArray /*[16]*/,
            modelViewMatrix: FloatArray /*[16]*/,
            depthHack: Boolean
        ) {
            val tri: srfTriangles_s
            if (surf.numVerts == 0) {
                return  // nothing in the surface
            }

            // copy verts and indexes
            tri = srfTriangles_s() ///*(srfTriangles_s *)*/ R_ClearedFrameAlloc(sizeof(tri));
            tri.numIndexes = surf.numIndexes
            tri.numVerts = surf.numVerts //TODO:see if we can get rid of these single element arrays. EDIT:done.
            tri.indexes =
                IntArray(tri.numIndexes) ///*(glIndex_t *)*/ R_FrameAlloc(tri.numIndexes * sizeof(tri.indexes[0]));
            //            memcpy(tri.indexes, indexes[surf.firstIndex], tri.numIndexes * sizeof(tri.indexes[0]));
            var s = surf.firstIndex
            var d = 0
            while (d < tri.numIndexes) {
                tri.indexes!![d] = indexes[s]
                s++
                d++
            }

            // we might be able to avoid copying these and just let them reference the list vars
            // but some things, like deforms and recursive
            // guis, need to access the verts in cpu space, not just through the vertex range
            tri.verts =
                Array(tri.numVerts) { idDrawVert() } ///*(idDrawVert *)*/ R_FrameAlloc(tri.numVerts * sizeof(tri.verts[0]));
            //            memcpy(tri.verts,  & verts[surf.firstVert], tri.numVerts * sizeof(tri.verts[0]));
            for (i in 0 until tri.numVerts) {
                tri.verts!![i] = idDrawVert(verts[surf.firstVert + i])
            }

            // move the verts to the vertex cache
            tri.ambientCache = VertexCache.vertexCache.AllocFrameTemp(tri.verts!!, tri.numVerts * idDrawVert.BYTES)

            // if we are out of vertex cache, don't create the surface
            if (null == tri.ambientCache) {
                return
            }
            val renderEntity: renderEntity_s
            renderEntity = renderEntity_s() //memset( & renderEntity, 0, sizeof(renderEntity));
            //            memcpy(renderEntity.shaderParms, surf.color, sizeof(surf.color));
            renderEntity.shaderParms[0] = surf.color[0]
            renderEntity.shaderParms[1] = surf.color[1]
            renderEntity.shaderParms[2] = surf.color[2]
            renderEntity.shaderParms[3] = surf.color[3]
            val guiSpace = viewEntity_s() ///*(viewEntity_t *)*/ R_ClearedFrameAlloc(sizeof( * guiSpace));
            //            memcpy(guiSpace.modelMatrix, modelMatrix, sizeof(guiSpace.modelMatrix));
            System.arraycopy(modelMatrix, 0, guiSpace.modelMatrix, 0, guiSpace.modelMatrix.size)
            //            memcpy(guiSpace.modelViewMatrix, modelViewMatrix, sizeof(guiSpace.modelViewMatrix));
            System.arraycopy(modelViewMatrix, 0, guiSpace.modelViewMatrix, 0, guiSpace.modelViewMatrix.size)
            guiSpace.weaponDepthHack = depthHack

            // add the surface, which might recursively create another gui
            tr_light.R_AddDrawSurf(tri, guiSpace, renderEntity, surf.material!!, tr.viewDef!!.scissor)
        }

        companion object {
            var bla = false
            var bla555 = 0
            var bla99 = 0
            private var clear = 0
            private var setColor = 0
            private var setColorTotal = 0
            private var drawStretchPic = 0
            private var bla4 = 0
        }
    }
}

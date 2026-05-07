/*
 * Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
 * Translated to Kotlin by Dr. Feederino with support of Claude Code
 *
 * This file is part of the Doom 3 Kotlin project.
 * Original source: neo/Game/Pvs.cpp, neo/Game/Pvs.h
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

import neo.Game.Game_local.idGameLocal
import neo.Renderer.RenderWorld.exitPortal_t
import neo.Renderer.RenderWorld.portalConnection_t
import neo.framework.Common
import neo.idlib.BV.idBounds
import neo.idlib.BitMsg.idBitMsg
import neo.idlib.Timer.idTimer
import neo.idlib.colorCyan
import neo.idlib.colorRed
import neo.idlib.containers.CInt
import neo.idlib.containers.memcmp
import neo.idlib.geometry.Winding.idFixedWinding
import neo.idlib.geometry.Winding.idWinding
import neo.idlib.math.*
import java.nio.ByteBuffer
import java.util.*
import kotlin.experimental.and
import kotlin.experimental.or

object Pvs {
    /*
     ===================================================================================

     PVS

     Note: mirrors and other special view portals are not taken into account

     ===================================================================================
     */
    const val MAX_BOUNDS_AREAS = 16
    const val MAX_CURRENT_PVS = 8 // must be a power of 2

    enum class pvsType_t {
        PVS_NORMAL,  //= 0,               // PVS through portals taking portal states into account
        PVS_ALL_PORTALS_OPEN,  //	= 1,	// PVS through portals assuming all portals are open
        PVS_CONNECTED_AREAS
        //	= 2	    // PVS considering all topologically connected areas visible
    }

    class pvsHandle_t {
        /*unsigned */
        var h // handle for current pvs
                = 0
        var i // index to current pvs
                = 0
    }

    class pvsCurrent_t {
        var handle // current pvs handle
                : pvsHandle_t = pvsHandle_t()
        var pvs // current pvs bit string
                : ByteArray? = null
    }

    class pvsPassage_t {
        var canSee // bit set for all portals that can be seen through this passage
                : ByteArray? = null
    }

    class pvsPortal_t {
        var areaNum // area this portal leads to
                = 0
        val bounds // winding bounds
                : idBounds = idBounds()
        var done // true if pvs is calculated for this portal
                = false
        var mightSee // used during construction
                : ByteArray? = null
        var passages // passages to portals in the area this portal leads to
                : Array<pvsPassage_t?>? = null
        val plane // winding plane, normal points towards the area this portal leads to
                : idPlane = idPlane()
        var vis // PVS for this portal
                : ByteArray? = null
        var w // winding goes counter clockwise seen from the area this portal is part of
                : idWinding? = null
    }

    class pvsArea_t {
        val bounds // bounds of the whole area
                : idBounds = idBounds()
        var numPortals // number of portals in this area
                = 0
        var portals // array with pointers to the portals of this area
                : Array<pvsPortal_t?>? = null
    }

    class pvsStack_t {
        var mightSee // bit set for all portals that might be visible through this passage/portal stack
                : ByteArray? = null
        var next // next stack entry
                : pvsStack_t? = null
    }

    class idPVS {
        private var areaPVS: ByteArray? = null
        private var areaQueue: IntArray? = null
        private var areaVisBytes = 0
        private var connectedAreas: BooleanArray? = null

        // current PVS for a specific source possibly taking portal states (open/closed) into account
        private val currentPVS: Array<pvsCurrent_t>
        private var numAreas: Int
        private var numPortals: Int

        // used to create PVS
        private var portalVisBytes = 0
        private var pvsAreas: Array<pvsArea_t?>? = null

        //
        private var pvsPortals: Array<pvsPortal_t?>? = null

        // setup for the current map
        fun Init() {
            val totalVisibleAreas: Int
            Shutdown()

            numAreas = Game_local.gameRenderWorld!!.NumAreas()
            if (numAreas <= 0) {
                return
            }
            connectedAreas = BooleanArray(numAreas)
            areaQueue = IntArray(numAreas)
            areaVisBytes = numAreas + 31 and 31.inv() shr 3

            areaPVS = ByteArray(numAreas * areaVisBytes) //	memset( areaPVS, 0xFF, numAreas * areaVisBytes );
            Arrays.fill(areaPVS, 0, numAreas * areaVisBytes, 0xFF.toByte())

            numPortals = GetPortalCount()
            portalVisBytes = numPortals + 31 and 31.inv() shr 3

            for (i in 0 until MAX_CURRENT_PVS) {
                currentPVS[i].handle.i = -1
                currentPVS[i].handle.h = 0
                currentPVS[i].pvs = ByteArray(areaVisBytes) //memset( currentPVS[i].pvs, 0, areaVisBytes );
            }
            val timer = idTimer()
            timer.Start()

            CreatePVSData()

            FrontPortalPVS()

            CopyPortalPVSToMightSee()

            PassagePVS()

            totalVisibleAreas = AreaPVSFromPortalPVS()

            DestroyPVSData()

            timer.Stop()

            Game_local.gameLocal.Printf("%5d msec to calculate PVS\n", timer.Milliseconds())
            Game_local.gameLocal.Printf("%5d areas\n", numAreas)
            Game_local.gameLocal.Printf("%5d portals\n", numPortals)
            Game_local.gameLocal.Printf("%5d areas visible on average\n", totalVisibleAreas / numAreas)

            if (numAreas * areaVisBytes < 1024) {
                Game_local.gameLocal.Printf("%5d bytes PVS data\n", numAreas * areaVisBytes)
            } else {
                Game_local.gameLocal.Printf("%5d KB PVS data\n", numAreas * areaVisBytes shr 10)
            }
        }

        fun Shutdown() {
            if (connectedAreas != null) {
//		delete connectedAreas;
                connectedAreas = null
            }
            if (areaQueue != null) {
//		delete areaQueue;
                areaQueue = null
            }
            if (areaPVS != null) {
//		delete areaPVS;
                areaPVS = null
            }
            if (currentPVS != null) {
                for (i in 0 until MAX_CURRENT_PVS) {
//			delete currentPVS[i].pvs;
                    currentPVS[i].pvs = null
                }
            }
        }

        // get the area(s) the source is in
        fun GetPVSArea(point: idVec3): Int {        // returns the area number
            return Game_local.gameRenderWorld!!.PointInArea(point)
        }

        fun GetPVSAreas(bounds: idBounds, areas: IntArray, maxAreas: Int): Int {    // returns number of areas
            return Game_local.gameRenderWorld!!.BoundsInAreas(bounds, areas, maxAreas)
        }

        // setup current PVS for the source
        fun SetupCurrentPVS(source: idVec3, type: pvsType_t? /*= PVS_NORMAL*/): pvsHandle_t {
            val sourceArea: Int
            sourceArea = Game_local.gameRenderWorld!!.PointInArea(source)
            return SetupCurrentPVS(sourceArea, type)
        }

        fun SetupCurrentPVS(source: idBounds, type: pvsType_t? /*= PVS_NORMAL*/): pvsHandle_t {
            val numSourceAreas: Int
            val sourceAreas = IntArray(MAX_BOUNDS_AREAS)
            numSourceAreas = Game_local.gameRenderWorld!!.BoundsInAreas(source, sourceAreas, MAX_BOUNDS_AREAS)
            return SetupCurrentPVS(sourceAreas, numSourceAreas, type)
        }


        fun SetupCurrentPVS(sourceArea: Int, type: pvsType_t? = pvsType_t.PVS_NORMAL /*= PVS_NORMAL*/): pvsHandle_t {
            var i: Int
            val handle: pvsHandle_t?
            handle = AllocCurrentPVS( /*reinterpret_cast<const unsigned int *>*/sourceArea)
            if (sourceArea < 0 || sourceArea >= numAreas) {
//		memset( currentPVS[handle.i].pvs, 0, areaVisBytes );
                Arrays.fill(currentPVS[handle.i].pvs, 0, areaVisBytes, 0.toByte())
                return handle
            }
            if (type != pvsType_t.PVS_CONNECTED_AREAS) {
//		memcpy( currentPVS[handle.i].pvs, areaPVS + sourceArea * areaVisBytes, areaVisBytes );
                System.arraycopy(areaPVS, sourceArea * areaVisBytes, currentPVS[handle.i].pvs, 0, areaVisBytes)
            } else {
//		memset( currentPVS[handle.i].pvs, -1, areaVisBytes );

                currentPVS[handle.i].pvs!!.fill(-1, 0, areaVisBytes)
            }
            if (type == pvsType_t.PVS_ALL_PORTALS_OPEN) {
                return handle
            }

//	memset( connectedAreas, 0, numAreas * sizeof( *connectedAreas ) );
            Arrays.fill(connectedAreas, 0, numAreas, false)
            GetConnectedAreas(sourceArea, connectedAreas!!)
            i = 0
            while (i < numAreas) {
                if (!connectedAreas!![i]) {
                    currentPVS[handle.i].pvs!![i shr 3] =
                        currentPVS[handle.i].pvs!![i shr 3] and (1 shl (i and 7)).inv().toByte()
                }
                i++
            }
            return handle
        }


        fun SetupCurrentPVS(
            sourceAreas: IntArray,
            numSourceAreas: Int,
            type: pvsType_t? = pvsType_t.PVS_NORMAL /*= PVS_NORMAL*/
        ): pvsHandle_t {
            var i: Int
            var j: Int
            /*unsigned*/
            var h: Int
            val handle: pvsHandle_t
            h = 0
            i = 0
            while (i < numSourceAreas) {
                h = h xor
                        /**
                         * reinterpret_cast<const unsigned int *>
                        </const> */
                        sourceAreas[i]
                i++
            }
            handle = AllocCurrentPVS(h)
            if (0 == numSourceAreas || sourceAreas[0] < 0 || sourceAreas[0] >= numAreas) {
                Arrays.fill(
                    currentPVS[handle.i].pvs,
                    0,
                    areaVisBytes,
                    0.toByte()
                ) //memset(currentPVS[handle.i].pvs, 0, areaVisBytes);
                return handle
            }
            if (type != pvsType_t.PVS_CONNECTED_AREAS) {
                // merge PVS of all areas the source is in
                System.arraycopy(
                    areaPVS,
                    sourceAreas[0] * areaVisBytes,
                    currentPVS[handle.i].pvs,
                    0,
                    areaVisBytes
                ) //		memcpy( currentPVS[handle.i].pvs, areaPVS + sourceAreas[0] * areaVisBytes, areaVisBytes );
                i = 1
                while (i < numSourceAreas) {
                    assert(sourceAreas[i] >= 0 && sourceAreas[i] < numAreas)
                    val vOffset = sourceAreas[i] * areaVisBytes
                    j = 0
                    while (j < areaVisBytes) {
                        currentPVS[handle.i].pvs!![j] =
                            (currentPVS[handle.i].pvs!![j].toInt() or areaPVS!![j + vOffset].toInt()).toByte()
                        j++
                    }
                    i++
                }
            } else {
                currentPVS[handle.i].pvs!!.fill(-1, 0, areaVisBytes)
                //memset( currentPVS[handle.i].pvs, -1, areaVisBytes );
            }
            if (type == pvsType_t.PVS_ALL_PORTALS_OPEN) {
                return handle
            }
            Arrays.fill(
                connectedAreas,
                0,
                numAreas,
                false
            ) //memset( connectedAreas, 0, numAreas * sizeof( *connectedAreas ) );

            // get all areas connected to any of the source areas
            i = 0
            while (i < numSourceAreas) {
                if (!connectedAreas!![sourceAreas[i]]) {
                    GetConnectedAreas(sourceAreas[i], connectedAreas!!)
                }
                i++
            }

            // remove unconnected areas from the PVS
            i = 0
            while (i < numAreas) {
                if (!connectedAreas!![i]) {
                    currentPVS[handle.i].pvs!![i shr 3] =
                        currentPVS[handle.i].pvs!![i shr 3] and (1 shl (i and 7)).inv().toByte()
                }
                i++
            }
            return handle
        }

        fun MergeCurrentPVS(pvs1: pvsHandle_t, pvs2: pvsHandle_t): pvsHandle_t {
            var i: Int
            val handle: pvsHandle_t?
            if (pvs1.i < 0 || pvs1.i >= MAX_CURRENT_PVS || pvs1.h != currentPVS[pvs1.i].handle.h || pvs2.i < 0 || pvs2.i >= MAX_CURRENT_PVS || pvs2.h != currentPVS[pvs2.i].handle.h
            ) {
                idGameLocal.Error("idPVS::MergeCurrentPVS: invalid handle")
            }
            handle = AllocCurrentPVS(pvs1.h xor pvs2.h)
            val ptrArr = currentPVS[handle.i].pvs!!
            val pvs1Arr = currentPVS[pvs1.i].pvs!!
            val pvs2Arr = currentPVS[pvs2.i].pvs!!
            i = 0
            while (i < areaVisBytes) {
                ptrArr[i] = (pvs1Arr[i].toInt() or pvs2Arr[i].toInt()).toByte()
                i++
            }
            return handle
        }

        fun FreeCurrentPVS(handle: pvsHandle_t) {
            if (handle.i < 0 || handle.i >= MAX_CURRENT_PVS || handle.h != currentPVS[handle.i].handle.h) {
                idGameLocal.Error("idPVS::FreeCurrentPVS: invalid handle")
            }
            currentPVS[handle.i].handle.i = -1
        }

        // returns true if the target is within the current PVS
        fun InCurrentPVS(handle: pvsHandle_t, target: idVec3): Boolean {
            val targetArea: Int
            if (handle.i < 0 || handle.i >= MAX_CURRENT_PVS || handle.h != currentPVS[handle.i].handle.h) {
                idGameLocal.Error("idPVS::InCurrentPVS: invalid handle")
            }
            targetArea = Game_local.gameRenderWorld!!.PointInArea(target)
            return if (targetArea == -1) {
                false
            } else currentPVS[handle.i].pvs!![targetArea shr 3].toInt() and (1 shl (targetArea and 7)) != 0
        }

        fun InCurrentPVS(handle: pvsHandle_t, target: idBounds): Boolean {
            var i: Int
            val numTargetAreas: Int
            val targetAreas = IntArray(MAX_BOUNDS_AREAS)
            if (handle.i < 0 || handle.i >= MAX_CURRENT_PVS || handle.h != currentPVS[handle.i].handle.h) {
                idGameLocal.Error("idPVS::InCurrentPVS: invalid handle")
            }
            numTargetAreas = Game_local.gameRenderWorld!!.BoundsInAreas(target, targetAreas, MAX_BOUNDS_AREAS)
            i = 0
            while (i < numTargetAreas) {
                if (currentPVS[handle.i].pvs!![targetAreas[i] shr 3].toInt() and (1 shl (targetAreas[i] and 7)) != 0) {
                    return true
                }
                i++
            }
            return false
        }

        fun InCurrentPVS(handle: pvsHandle_t, targetArea: Int): Boolean {
            if (handle.i < 0 || handle.i >= MAX_CURRENT_PVS || handle.h != currentPVS[handle.i].handle.h) {
                idGameLocal.Error("idPVS::InCurrentPVS: invalid handle")
            }
            return if (targetArea < 0 || targetArea >= numAreas) {
                false
            } else currentPVS[handle.i].pvs!![targetArea shr 3].toInt() and (1 shl (targetArea and 7)) != 0
        }

        fun InCurrentPVS(handle: pvsHandle_t, targetAreas: IntArray, numTargetAreas: Int): Boolean {
            var i: Int
            if (handle.i < 0 || handle.i >= MAX_CURRENT_PVS || handle.h != currentPVS[handle.i].handle.h) {
                idGameLocal.Error("idPVS::InCurrentPVS: invalid handle")
            }
            i = 0
            while (i < numTargetAreas) {
                if (targetAreas[i] < 0 || targetAreas[i] >= numAreas) {
                    i++
                    continue
                }
                if (currentPVS[handle.i].pvs!![targetAreas[i] shr 3].toInt() and (1 shl (targetAreas[i] and 7)) != 0) {
                    return true
                }
                i++
            }
            return false
        }

        // draw all portals that are within the PVS of the source
        fun DrawPVS(source: idVec3, type: pvsType_t? /*= PVS_NORMAL*/) {
            var i: Int
            var j: Int
            var k: Int
            var numPoints: Int
            var n: Int
            val sourceArea: Int
            var portal: exitPortal_t?
            val plane = idPlane()
            val offset = idVec3()
            val color: idVec4 = idVec4()
            val handle: pvsHandle_t?
            sourceArea = Game_local.gameRenderWorld!!.PointInArea(source)
            if (sourceArea == -1) {
                return
            }
            handle = SetupCurrentPVS(source, type)
            j = 0
            while (j < numAreas) {
                if (0 == currentPVS[handle.i].pvs!![j shr 3].toInt() and (1 shl (j and 7))) {
                    j++
                    continue
                }
                color.set(
                    if (j == sourceArea) {
                        colorRed
                    } else {
                        colorCyan
                    }
                )
                n = Game_local.gameRenderWorld!!.NumPortalsInArea(j)

                // draw all the portals of the area
                i = 0
                while (i < n) {
                    portal = Game_local.gameRenderWorld!!.GetPortal(j, i)
                    numPoints = portal.w!!.GetNumPoints()
                    portal.w!!.GetPlane(plane)
                    offset.set(plane.Normal().times(4.0f))
                    k = 0
                    while (k < numPoints) {
                        Game_local.gameRenderWorld!!.DebugLine(
                            color,
                            portal.w!![k].ToVec3().plus(offset),
                            portal.w!![(k + 1) % numPoints].ToVec3().plus(offset)
                        )
                        k++
                    }
                    i++
                }
                j++
            }
            FreeCurrentPVS(handle)
        }

        fun DrawPVS(source: idBounds, type: pvsType_t? /*= PVS_NORMAL*/) {
            var i: Int
            var j: Int
            var k: Int
            var numPoints: Int
            var n: Int
            val num: Int
            val areas = IntArray(MAX_BOUNDS_AREAS)
            var portal: exitPortal_t?
            val plane = idPlane()
            val offset = idVec3()
            val color: idVec4 = idVec4()
            val handle: pvsHandle_t?
            num = Game_local.gameRenderWorld!!.BoundsInAreas(source, areas, MAX_BOUNDS_AREAS)
            if (0 == num) {
                return
            }
            handle = SetupCurrentPVS(source, type)
            j = 0
            while (j < numAreas) {
                if (0 == currentPVS[handle.i].pvs!!.get(j shr 3).toInt() and (1 shl (j and 7))) {
                    j++
                    continue
                }
                i = 0
                while (i < num) {
                    if (j == areas[i]) {
                        break
                    }
                    i++
                }
                color.set(
                    if (i < num) {
                        colorRed
                    } else {
                        colorCyan
                    }
                )
                n = Game_local.gameRenderWorld!!.NumPortalsInArea(j)

                // draw all the portals of the area
                i = 0
                while (i < n) {
                    portal = Game_local.gameRenderWorld!!.GetPortal(j, i)
                    numPoints = portal.w!!.GetNumPoints()
                    portal.w!!.GetPlane(plane)
                    offset.set(plane.Normal().times(4.0f))
                    k = 0
                    while (k < numPoints) {
                        Game_local.gameRenderWorld!!.DebugLine(
                            color,
                            portal.w!![k].ToVec3().plus(offset),
                            portal.w!![(k + 1) % numPoints].ToVec3().plus(offset)
                        )
                        k++
                    }
                    i++
                }
                j++
            }
            FreeCurrentPVS(handle)
        }

        // visualize the PVS the handle points to
        fun DrawCurrentPVS(handle: pvsHandle_t, source: idVec3) {
            var i: Int
            var j: Int
            var k: Int
            var numPoints: Int
            var n: Int
            val sourceArea: Int
            var portal: exitPortal_t?
            val plane = idPlane()
            val offset = idVec3()
            val color: idVec4 = idVec4()
            if (handle.i < 0 || handle.i >= MAX_CURRENT_PVS || handle.h != currentPVS[handle.i].handle.h) {
                idGameLocal.Error("idPVS::DrawCurrentPVS: invalid handle")
            }
            sourceArea = Game_local.gameRenderWorld!!.PointInArea(source)
            if (sourceArea == -1) {
                return
            }
            j = 0
            while (j < numAreas) {
                if (0 == currentPVS[handle.i].pvs!!.get(j shr 3).toInt() and (1 shl (j and 7))) {
                    j++
                    continue
                }
                color.set(
                    if (j == sourceArea) {
                        colorRed
                    } else {
                        colorCyan
                    }
                )
                n = Game_local.gameRenderWorld!!.NumPortalsInArea(j)

                // draw all the portals of the area
                i = 0
                while (i < n) {
                    portal = Game_local.gameRenderWorld!!.GetPortal(j, i)
                    numPoints = portal.w!!.GetNumPoints()
                    portal.w!!.GetPlane(plane)
                    offset.set(plane.Normal().times(4.0f))
                    k = 0
                    while (k < numPoints) {
                        Game_local.gameRenderWorld!!.DebugLine(
                            color,
                            portal.w!![k].ToVec3().plus(offset),
                            portal.w!![(k + 1) % numPoints].ToVec3().plus(offset)
                        )
                        k++
                    }
                    i++
                }
                j++
            }
        }

        // #if ASYNC_WRITE_PVS
        fun WritePVS(handle: pvsHandle_t, msg: idBitMsg) {
            msg.WriteData(ByteBuffer.wrap(currentPVS[handle.i].pvs), areaVisBytes)
        }

        // #endif
        fun ReadPVS(handle: pvsHandle_t, msg: idBitMsg) {
            val l_pvs = ByteBuffer.allocate(256)
            var i: Int
            assert(areaVisBytes <= 256)
            msg.ReadData(l_pvs, areaVisBytes)
            if (memcmp(l_pvs.array(), currentPVS[handle.i].pvs, areaVisBytes)) {
                Common.common.Printf("PVS not matching ( %d areaVisBytes ) - server then client:\n", areaVisBytes)
                i = 0
                while (i < areaVisBytes) {
                    Common.common.Printf("%x ", l_pvs[i])
                    i++
                }
                Common.common.Printf("\n")
                i = 0
                while (i < areaVisBytes) {
                    Common.common.Printf("%x ", currentPVS[handle.i].pvs!!.get(i))
                    i++
                }
                Common.common.Printf("\n")
            }
        }

        // D3XP: returns true if any area visible from the handle's PVS contains a portal sky
        fun CheckAreasForPortalSky(handle: pvsHandle_t, origin: idVec3): Boolean {
            if (handle.i < 0 || handle.i >= MAX_CURRENT_PVS || handle.h != currentPVS[handle.i].handle.h) {
                return false
            }
            val sourceArea = Game_local.gameRenderWorld!!.PointInArea(origin)
            if (sourceArea == -1) {
                return false
            }
            var j = 0
            while (j < numAreas) {
                if (currentPVS[handle.i].pvs!![j shr 3].toInt() and (1 shl (j and 7)) == 0) {
                    j++
                    continue
                }
                if (Game_local.gameRenderWorld!!.CheckAreaForPortalSky(j)) {
                    return true
                }
                j++
            }
            return false
        }

        private fun GetPortalCount(): Int {
            var i: Int
            val na: Int
            var np: Int
            na = Game_local.gameRenderWorld!!.NumAreas()
            np = 0
            i = 0
            while (i < na) {
                np += Game_local.gameRenderWorld!!.NumPortalsInArea(i)
                i++
            }
            return np
        }

        private fun CreatePVSData() {
            var i: Int
            var j: Int
            var n: Int
            var cp: Int
            var portal: exitPortal_t?
            var area: pvsArea_t?
            var p: pvsPortal_t?
            if (0 == numPortals) {
                return
            }
            pvsPortals = arrayOfNulls(numPortals)
            pvsAreas = arrayOfNulls(numAreas)
            //	memset( pvsAreas, 0, numAreas * sizeof( *pvsAreas ) );
            cp = 0
            i = 0
            while (i < numAreas) {

                pvsAreas!![i] = pvsArea_t()
                area = pvsAreas!![i]
                area!!.bounds.Clear()
                n = Game_local.gameRenderWorld!!.NumPortalsInArea(i)
                // each area gets its own portal pointer array (C++ used portalPtrs + cp offset)
                val areaPortalPtrs: Array<pvsPortal_t?> = arrayOfNulls(n)
                j = 0
                while (j < n) {
                    portal = Game_local.gameRenderWorld!!.GetPortal(i, j)
                    p = pvsPortal_t()
                    pvsPortals!![cp++] = p
                    // the winding goes counter clockwise seen from this area
                    p.w = portal.w!!.Copy()
                    p.areaNum = portal.areas[1] // area[1] is always the area the portal leads to
                    p.vis = ByteArray(portalVisBytes)
                    p.mightSee = ByteArray(portalVisBytes)
                    p.w!!.GetBounds(p.bounds)
                    p.w!!.GetPlane(p.plane)
                    // plane normal points to outside the area
                    p.plane.set(p.plane.unaryMinus())
                    // no PVS calculated for this portal yet
                    p.done = false
                    areaPortalPtrs[area.numPortals++] = p
                    area.bounds.timesAssign(p.bounds)
                    j++
                }
                area.portals = areaPortalPtrs
                i++
            }
        }

        private fun DestroyPVSData() {
//	int i;

//	int i;
            if (null == pvsAreas) {
                return
            }

            // delete portal pointer array
//	delete[] pvsAreas[0].portals;
            // delete all areas
//	delete[] pvsAreas;

            // delete portal pointer array
//	delete[] pvsAreas[0].portals;
            // delete all areas
//	delete[] pvsAreas;
            pvsAreas = null

            // delete portal data
//	for ( i = 0; i < numPortals; i++ ) {
//		delete[] pvsPortals!![i].vis;
//		delete[] pvsPortals!![i].mightSee;
//		delete pvsPortals!![i].w!!;
//	}
            // delete portals
//	delete[] pvsPortals;

            // delete portal data
//	for ( i = 0; i < numPortals; i++ ) {
//		delete[] pvsPortals!![i].vis;
//		delete[] pvsPortals!![i].mightSee;
//		delete pvsPortals!![i].w!!;
//	}
            // delete portals
//	delete[] pvsPortals;
            pvsPortals = null
        }

        private fun CopyPortalPVSToMightSee() {
            var i: Int
            var p: pvsPortal_t?
            i = 0
            while (i < numPortals) {
                p = pvsPortals!![i]!!
                //		memcpy( p.mightSee, p.vis, portalVisBytes );
                System.arraycopy(p.vis, 0, p.mightSee, 0, portalVisBytes)
                i++
            }
        }

        private fun FloodFrontPortalPVS_r(portal: pvsPortal_t, areaNum: Int) {
            var i: Int
            var n: Int
            val area: pvsArea_t
            var p: pvsPortal_t
            area = pvsAreas!![areaNum]!!
            i = 0
            while (i < area.numPortals) {
                p = area.portals!![i]!!
                n = pvsPortals!!.indexOf(p) //TODO:very importante, what does thus do!?
                // don't flood through if this portal is not at the front
                if (0 == portal.mightSee!![n shr 3].toInt() and (1 shl (n and 7))) {
                    i++
                    continue
                }
                // don't flood through if already visited this portal
                if (portal.vis!![n shr 3].toInt() and (1 shl (n and 7)) != 0) {
                    i++
                    continue
                }
                // this portal might be visible
                portal.vis!![n shr 3] = (portal.vis!![n shr 3].toInt() or (1 shl (n and 7))).toByte()
                // flood through the portal
                FloodFrontPortalPVS_r(portal, p.areaNum)
                i++
            }
        }

        private fun FrontPortalPVS() {
            var i: Int
            var j: Int
            var k: Int
            var n: Int
            var p: Int
            var side1: Int
            var side2: Int
            var areaSide: Int
            var p1: pvsPortal_t?
            var p2: pvsPortal_t
            var area: pvsArea_t?
            i = 0
            while (i < numPortals) {
                p1 = pvsPortals!![i]!!
                j = 0
                while (j < numAreas) {
                    area = pvsAreas!![j]!!
                    side1 = area.bounds.PlaneSide(p1.plane)
                    areaSide = side1

                    // if the whole area is at the back side of the portal
                    if (areaSide == PLANESIDE_BACK) {
                        j++
                        continue
                    }
                    p = 0
                    while (p < area.numPortals) {
                        p2 = area.portals!![p]!!

                        // if we the whole area is not at the front we need to check
                        if (areaSide != PLANESIDE_FRONT) {
                            // if the second portal is completely at the back side of the first portal
                            side1 = p2.bounds.PlaneSide(p1.plane)
                            if (side1 == PLANESIDE_BACK) {
                                p++
                                continue
                            }
                        }

                        // if the first portal is completely at the front of the second portal
                        side2 = p1.bounds.PlaneSide(p2.plane)
                        if (side2 == PLANESIDE_FRONT) {
                            p++
                            continue
                        }

                        // if the second portal is not completely at the front of the first portal
                        if (side1 != PLANESIDE_FRONT) {
                            // more accurate check
                            k = 0
                            while (k < p2.w!!.GetNumPoints()) {

                                // if more than an epsilon at the front side
                                if (p1.plane.Side(p2.w!![k].ToVec3(), ON_EPSILON) == PLANESIDE_FRONT) {
                                    break
                                }
                                k++
                            }
                            if (k >= p2.w!!.GetNumPoints()) {
                                p++
                                continue  // second portal is at the back of the first portal
                            }
                        }

                        // if the first portal is not completely at the back side of the second portal
                        if (side2 != PLANESIDE_BACK) {
                            // more accurate check
                            k = 0
                            while (k < p1.w!!.GetNumPoints()) {

                                // if more than an epsilon at the back side
                                if (p2.plane.Side(p1.w!![k].ToVec3(), ON_EPSILON) == PLANESIDE_BACK) {
                                    break
                                }
                                k++
                            }
                            if (k >= p1.w!!.GetNumPoints()) {
                                p++
                                continue  // first portal is at the front of the second portal
                            }
                        }

                        // the portal might be visible at the front
                        n = pvsPortals!!.indexOf(p2)
                        p1.mightSee!![n shr 3] = (p1.mightSee!![n shr 3].toInt() or (1 shl (n and 7))).toByte()
                        p++
                    }
                    j++
                }
                i++
            }

            // flood the front portal pvs for all portals
            i = 0
            while (i < numPortals) {
                p1 = pvsPortals!![i]!!
                FloodFrontPortalPVS_r(p1, p1.areaNum)
                i++
            }
        }

        private fun FloodPassagePVS_r(source: pvsPortal_t, portal: pvsPortal_t, prevStack: pvsStack_t): pvsStack_t {
            var i: Int
            var j: Int
            var n: Int
            var p: pvsPortal_t
            val area: pvsArea_t?
            var stack: pvsStack_t?
            var passage: pvsPassage_t?
            var more: Int
            area = pvsAreas!![portal.areaNum]!!
            stack = prevStack.next
            // if no next stack entry allocated
            if (null == stack) {
//		stack = reinterpret_cast<pvsStack_t*>(new byte[sizeof(pvsStack_t) + portalVisBytes]);
                stack = pvsStack_t()
                //		stack.mightSee = (reinterpret_cast<byte *>(stack)) + sizeof(pvsStack_t);TODO:check this..very importante
                stack.mightSee = ByteArray(portalVisBytes)
                stack.next = null
                prevStack.next = stack
            }

            // check all portals for flooding into other areas
            i = 0
            while (i < area.numPortals) {
                passage = portal.passages!![i]

                // if this passage is completely empty
                if (null == passage!!.canSee) {
                    i++
                    continue
                }
                p = area.portals!![i]!!
                n = pvsPortals!!.indexOf(p)

                // if this portal cannot be seen through our current portal/passage stack
                if (0 == prevStack.mightSee!![n shr 3].toInt() and (1 shl (n and 7))) {
                    i++
                    continue
                }

                // mark the portal as visible
                source.vis!![n shr 3] = (source.vis!![n shr 3].toInt() or (1 shl (n and 7))).toByte()

                // get byte arrays for direct operations
                val prevMightSeeArr = prevStack.mightSee!!
                val passageVisArr = passage.canSee!!
                val sourceVisArr = source.vis!!
                val mightSeeArr = stack.mightSee!!
                more = 0
                // use the portal PVS if it has been calculated
                if (p.done) {
                    val portalVisArr = p.vis!!
                    j = 0
                    while (j < portalVisBytes) {

                        // get new PVS which is decreased by going through this passage
                        val mByte = prevMightSeeArr[j].toInt() and passageVisArr[j].toInt() and portalVisArr[j].toInt()
                        // check if anything might be visible through this passage that wasn't yet visible
                        more = more or (mByte and sourceVisArr[j].toInt().inv())
                        // store new PVS
                        mightSeeArr[j] = mByte.toByte()
                        j++
                    }
                } else {
                    // the p.mightSee is implicitely stored in the passageVis
                    j = 0
                    while (j < portalVisBytes) {

                        // get new PVS which is decreased by going through this passage
                        val mByte = prevMightSeeArr[j].toInt() and passageVisArr[j].toInt()
                        // check if anything might be visible through this passage that wasn't yet visible
                        more = more or (mByte and sourceVisArr[j].toInt().inv())
                        // store new PVS
                        mightSeeArr[j] = mByte.toByte()
                        j++
                    }
                }

                // if nothing more can be seen
                if (0 == more) {
                    i++
                    continue
                }

                // go through the portal
                stack.next = FloodPassagePVS_r(source, p, stack)
                i++
            }
            return stack
        }

        private fun PassagePVS() {
            var i: Int
            var source: pvsPortal_t?
            var stack: pvsStack_t?
            var s: pvsStack_t?

            // create the passages
            CreatePassages()

            // allocate first stack entry
//	stack = reinterpret_cast<pvsStack_t*>(new byte[sizeof(pvsStack_t) + portalVisBytes]);
            stack = pvsStack_t()
            //	stack.mightSee = (reinterpret_cast<byte *>(stack)) + sizeof(pvsStack_t);
            stack.mightSee = ByteArray(portalVisBytes)
            stack.next = null

            // calculate portal PVS by flooding through the passages
            i = 0
            while (i < numPortals) {
                source = pvsPortals!![i]!!
                Arrays.fill(source.vis, 0, portalVisBytes, 0.toByte())
                //		memcpy( stack.mightSee, source.mightSee, portalVisBytes );
                System.arraycopy(source.mightSee, 0, stack.mightSee, 0, portalVisBytes)
                FloodPassagePVS_r(source, source, stack)
                source.done = true
                i++
            }

            // free the allocated stack
            s = stack
            while (s != null) {
                stack = stack!!.next
                s = stack
            }

            // destroy the passages
            DestroyPassages()
        }

        private fun AddPassageBoundaries(
            source: idWinding,
            pass: idWinding,
            flipClip: Boolean,
            bounds: Array<idPlane>,
            numBounds: CInt,
            maxBounds: Int
        ) {
            var i: Int
            var j: Int
            var k: Int
            var l: Int
            val v1 = idVec3()
            val v2 = idVec3()
            val normal = idVec3()
            var d: Float
            var dist: Float
            var flipTest: Boolean
            var front: Boolean
            val plane = idPlane()

            // check all combinations
            i = 0
            while (i < source.GetNumPoints()) {
                l = (i + 1) % source.GetNumPoints()
                v1.set(source[l].ToVec3().minus(source[i].ToVec3()))

                // find a vertex of pass that makes a plane that puts all of the
                // vertices of pass on the front side and all of the vertices of
                // source on the back side
                j = 0
                while (j < pass.GetNumPoints()) {
                    v2.set(pass[j].ToVec3().minus(source[i].ToVec3()))
                    normal.set(v1.Cross(v2))
                    if (normal.Normalize() < 0.01) {
                        j++
                        continue
                    }
                    dist = normal.times(pass[j].ToVec3())

                    //
                    // find out which side of the generated seperating plane has the
                    // source portal
                    //
                    flipTest = false
                    k = 0
                    while (k < source.GetNumPoints()) {
                        if (k == i || k == l) {
                            k++
                            continue
                        }
                        d = source[k].ToVec3().times(normal) - dist
                        if (d < -ON_EPSILON) {
                            // source is on the negative side, so we want all
                            // pass and target on the positive side
                            flipTest = false
                            break
                        } else if (d > ON_EPSILON) {
                            // source is on the positive side, so we want all
                            // pass and target on the negative side
                            flipTest = true
                            break
                        }
                        k++
                    }
                    if (k == source.GetNumPoints()) {
                        j++
                        continue  // planar with source portal
                    }

                    // flip the normal if the source portal is backwards
                    if (flipTest) {
                        normal.set(normal.unaryMinus())
                        dist = -dist
                    }

                    // if all of the pass portal points are now on the positive side,
                    // this is the seperating plane
                    front = false
                    k = 0
                    while (k < pass.GetNumPoints()) {
                        if (k == j) {
                            k++
                            continue
                        }
                        d = pass[k].ToVec3().times(normal) - dist
                        if (d < -ON_EPSILON) {
                            break
                        } else if (d > ON_EPSILON) {
                            front = true
                        }
                        k++
                    }
                    if (k < pass.GetNumPoints()) {
                        j++
                        continue  // points on negative side, not a seperating plane
                    }
                    if (!front) {
                        j++
                        continue  // planar with seperating plane
                    }

                    // flip the normal if we want the back side
                    if (flipClip) {
                        plane.SetNormal(normal.unaryMinus())
                        plane.SetDist(-dist)
                    } else {
                        plane.SetNormal(normal)
                        plane.SetDist(dist)
                    }

                    // check if the plane is already a passage boundary
                    k = 0
                    while (k < numBounds._val) {
                        if (plane.Compare(bounds[k], 0.001f, 0.01f)) {
                            break
                        }
                        k++
                    }
                    if (k < numBounds._val) {
                        break
                    }
                    if (numBounds._val >= maxBounds) {
                        Game_local.gameLocal.Warning("max passage boundaries.")
                        break
                    }
                    bounds[numBounds._val].set(plane)
                    numBounds.increment()
                    break
                    j++
                }
                i++
            }
        }

        private fun CreatePassages() {
            var i: Int
            var j: Int
            var l: Int
            var n: Int
            var front: Int
            var passageMemory: Int
            var byteNum: Int
            var bitNum: Int
            val numBounds = CInt()
            val sides = IntArray(MAX_PASSAGE_BOUNDS)
            val passageBounds: Array<idPlane> = idPlane.generateArray(MAX_PASSAGE_BOUNDS)
            var source: pvsPortal_t?
            var target: pvsPortal_t
            var p: pvsPortal_t?
            var area: pvsArea_t?
            var passage: pvsPassage_t?
            val winding = idFixedWinding()
            var canSee: Byte
            var mightSee: Byte
            var bit: Byte
            passageMemory = 0
            i = 0
            while (i < numPortals) {
                source = pvsPortals!![i]!!
                area = pvsAreas!![source.areaNum]!!
                source.passages = arrayOfNulls(area.numPortals)
                j = 0
                while (j < area.numPortals) {
                    target = area.portals!![j]!!
                    n = pvsPortals!!.indexOf(target)
                    source.passages!![j] = pvsPassage_t()
                    passage = source.passages!![j]

                    // if the source portal cannot see this portal
                    if (0 == (source.mightSee!![n shr 3].toInt() and (1 shl (n and 7)))) {
                        // not all portals in the area have to be visible because areas are not necesarily convex
                        // also no passage has to be created for the portal which is the opposite of the source
                        passage!!.canSee = null
                        j++
                        continue
                    }
                    passage!!.canSee = ByteArray(portalVisBytes)
                    passageMemory += portalVisBytes

                    // boundary plane normals point inwards
                    numBounds._val = 0
                    AddPassageBoundaries(source.w!!, target.w!!, false, passageBounds, numBounds, MAX_PASSAGE_BOUNDS)
                    AddPassageBoundaries(target.w!!, source.w!!, true, passageBounds, numBounds, MAX_PASSAGE_BOUNDS)

                    // get all portals visible through this passage
                    byteNum = 0
                    while (byteNum < portalVisBytes) {
                        canSee = 0
                        mightSee = (source.mightSee!![byteNum] and target.mightSee!![byteNum])

                        // go through eight portals at a time to speed things up
                        bitNum = 0
                        while (bitNum < 8) {
                            bit = (1 shl bitNum).toByte()
                            if (0 == (mightSee and bit).toInt()) {
                                bitNum++
                                continue
                            }
                            p = pvsPortals!![(byteNum shl 3) + bitNum]!!
                            if (p.areaNum == source.areaNum) {
                                bitNum++
                                continue
                            }
                            front = 0
                            l = 0
                            while (l < numBounds._val) {
                                sides[l] = p.bounds.PlaneSide(passageBounds[l])
                                // if completely at the back of the passage bounding plane
                                if (sides[l] == PLANESIDE_BACK) {
                                    break
                                }
                                // if completely at the front
                                if (sides[l] == PLANESIDE_FRONT) {
                                    front++
                                }
                                l++
                            }
                            // if completely outside the passage
                            if (l < numBounds._val) {
                                bitNum++
                                continue
                            }

                            // if not at the front of all bounding planes and thus not completely inside the passage
                            if (front != numBounds._val) {
                                winding.set(p.w!!)
                                l = 0
                                while (l < numBounds._val) {

                                    // only clip if the winding possibly crosses this plane
                                    if (sides[l] != PLANESIDE_CROSS) {
                                        l++
                                        continue
                                    }
                                    // clip away the part at the back of the bounding plane
                                    winding.ClipInPlace(passageBounds[l])
                                    // if completely clipped away
                                    if (0 == winding.GetNumPoints()) {
                                        break
                                    }
                                    l++
                                }
                                // if completely outside the passage
                                if (l < numBounds._val) {
                                    bitNum++
                                    continue
                                }
                            }
                            canSee = canSee or bit
                            bitNum++
                        }

                        // store results of all eight portals
                        passage.canSee!![byteNum] = canSee
                        byteNum++
                    }

                    // can always see the target portal
                    passage.canSee!![n shr 3] = (passage.canSee!![n shr 3].toInt() or (1 shl (n and 7))).toByte()
                    j++
                }
                i++
            }
            if (passageMemory < 1024) {
                Game_local.gameLocal.Printf("%5d bytes passage memory used to build PVS\n", passageMemory)
            } else {
                Game_local.gameLocal.Printf("%5d KB passage memory used to build PVS\n", passageMemory shr 10)
            }
        }

        private fun DestroyPassages() {
            var i: Int
            var j: Int
            var p: pvsPortal_t?
            var area: pvsArea_t?
            i = 0
            while (i < numPortals) {
                p = pvsPortals!![i]!!
                area = pvsAreas!![p.areaNum]!!
                j = 0
                while (j < area.numPortals) {
                    if (p.passages!![j]!!.canSee != null) {
//				delete[] p.passages!![j].canSee;
                        p.passages!![j]!!.canSee = null
                    }
                    j++
                }
                //		delete[] p.passages;
                p.passages = null
                i++
            }
        }

        private fun AreaPVSFromPortalPVS(): Int {
            var i: Int
            var j: Int
            var k: Int
            var areaNum: Int
            var totalVisibleAreas: Int
            var pvs: Int
            var portalPVS: ByteArray
            var area: pvsArea_t?
            totalVisibleAreas = 0
            if (0 == numPortals) {
                return totalVisibleAreas
            }
            Arrays.fill(areaPVS, 0, numAreas * areaVisBytes, 0.toByte())
            i = 0
            while (i < numAreas) {
                area = pvsAreas!![i]!!
                //                pvs = areaPVS + i * areaVisBytes;
                pvs = i * areaVisBytes

                // the area is visible to itself
                areaPVS!![pvs + (i shr 3)] = (areaPVS!![pvs + (i shr 3)].toInt() or (1 shl (i and 7))).toByte()
                if (0 == area.numPortals) {
                    i++
                    continue
                }

                // store the PVS of all portals in this area at the first portal
                j = 1
                while (j < area.numPortals) {
                    val vis0 = area.portals!![0]!!.vis!!
                    val visJ = area.portals!![j]!!.vis!!
                    k = 0
                    while (k < portalVisBytes) {
                        vis0[k] = (vis0[k].toInt() or visJ[k].toInt()).toByte()
                        k++
                    }
                    j++
                }

                // the portals of this area are always visible
                j = 0
                while (j < area.numPortals) {
                    k = pvsPortals!!.indexOf(area.portals!![j])
                    area.portals!![0]!!.vis!![k shr 3] =
                        (area.portals!![0]!!.vis!![k shr 3].toInt() or (1 shl (k and 7))).toByte()
                    j++
                }

                // set all areas to visible that can be seen from the portals of this area
                portalPVS = area.portals!![0]!!.vis!!
                j = 0
                while (j < numPortals) {

                    // if this portal is visible
                    if (portalPVS[j shr 3].toInt() and (1 shl (j and 7)) != 0) {
                        areaNum = pvsPortals!![j]!!.areaNum
                        areaPVS!![pvs + (areaNum shr 3)] =
                            (areaPVS!![pvs + (areaNum shr 3)].toInt() or (1 shl (areaNum and 7))).toByte()
                    }
                    j++
                }

                // count the number of visible areas
                j = 0
                while (j < numAreas) {
                    if (areaPVS!![pvs + (j shr 3)].toInt() and (1 shl (j and 7)) != 0) {
                        totalVisibleAreas++
                    }
                    j++
                }
                i++
            }
            return totalVisibleAreas
        }

        /*
         ================
         idPVS::GetConnectedAreas

         assumes the 'areas' array is initialized to false
         ================
         */
        private fun GetConnectedAreas(srcArea: Int, connectedAreas: BooleanArray) {
            var curArea: Int
            var nextArea: Int
            var queueStart: Int
            var queueEnd: Int
            var i: Int
            var n: Int
            var portal: exitPortal_t
            queueStart = -1
            queueEnd = 0
            connectedAreas[srcArea] = true
            curArea = srcArea
            while (queueStart < queueEnd) {
                n = Game_local.gameRenderWorld!!.NumPortalsInArea(curArea)
                i = 0
                while (i < n) {
                    portal = Game_local.gameRenderWorld!!.GetPortal(curArea, i)
                    if (portal.blockingBits and portalConnection_t.PS_BLOCK_VIEW.ordinal != 0) {
                        i++
                        continue
                    }

                    // area[1] is always the area the portal leads to
                    nextArea = portal.areas[1]

                    // if already visited this area
                    if (connectedAreas[nextArea]) {
                        i++
                        continue
                    }

                    // add area to queue
                    areaQueue!![queueEnd++] = nextArea
                    connectedAreas[nextArea] = true
                    i++
                }
                curArea = areaQueue!![++queueStart]
            }
        }

        private fun AllocCurrentPVS( /*unsigned*/
                                     h: Int
        ): pvsHandle_t {
            var i: Int
            val handle = pvsHandle_t()
            i = 0
            while (i < MAX_CURRENT_PVS) {
                if (currentPVS[i].handle.i == -1) {
                    currentPVS[i].handle.i = i
                    currentPVS[i].handle.h = h
                    return currentPVS[i].handle
                }
                i++
            }
            idGameLocal.Error("idPVS::AllocCurrentPVS: no free PVS left")
            handle.i = -1
            handle.h = 0
            return handle
        }

        companion object {
            const val MAX_PASSAGE_BOUNDS = 128
        }

        // ~idPVS( void );
        init {
            var i: Int
            numAreas = 0
            numPortals = 0
            i = 0
            currentPVS = Array(MAX_CURRENT_PVS) {
                pvsCurrent_t()
            }

            while (i < MAX_CURRENT_PVS) {
                currentPVS[i] = pvsCurrent_t()
                currentPVS[i].handle.i = -1
                currentPVS[i].handle.h = 0
                currentPVS[i].pvs = null
                i++
            }
        }
    }
}
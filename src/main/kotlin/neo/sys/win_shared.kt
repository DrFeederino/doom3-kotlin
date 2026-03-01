/*
 * Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
 * Translated to Kotlin by Dr. Feederino with support of Claude Code
 *
 * This file is part of the Doom 3 Kotlin project.
 * Original source: neo/sys/win32/win_shared.cpp
 *
 * NOTE: Differs from C++ — C++ uses Win32 API (timeGetTime, GlobalMemoryStatus,
 * VirtualLock/Unlock). Kotlin uses Java standard library equivalents.
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

package neo.sys

import com.sun.management.OperatingSystemMXBean
import java.io.File
import java.lang.management.ManagementFactory
import java.time.Instant

object win_shared {
    val sys_timeBase = Instant.now().toEpochMilli()

    /*
     ================
     Sys_Milliseconds
     ================
     */

    fun Sys_Milliseconds(): Int {
        return (Instant.now().toEpochMilli() - sys_timeBase).toInt()
    }

    /*
     ================
     Sys_GetSystemRam

     returns amount of physical memory in MB
     ================
     */
    fun Sys_GetSystemRam(): Int {
        val ram = (ManagementFactory.getOperatingSystemMXBean() as OperatingSystemMXBean).totalPhysicalMemorySize
        return (ram / 1000000).toInt()
    }

    /*
     ================
     Sys_GetDriveFreeSpace
     returns in megabytes
     ================
     */
    fun Sys_GetDriveFreeSpace(path: String): Long {
        return File(path).freeSpace / (1024L * 1024L)
    }

    /*
     ================
     Sys_GetVideoRam
     returns in megabytes
     ================
     */
    fun Sys_GetVideoRam(): Int {
        return 0
    }


    /*
     ================
     Sys_LockMemory
     ================
     */
    // NOTE: Differs from C++ — C++ uses VirtualLock to prevent pages from being
    // swapped to disk. JVM manages memory internally; no equivalent operation.
    fun Sys_LockMemory(ptr: Any, bytes: Int): Boolean {
        return true
    }

    // NOTE: Differs from C++ — C++ uses VirtualUnlock. No JVM equivalent.
    fun Sys_UnlockMemory(ptr: Any, bytes: Int): Boolean {
        return true
    }


    fun Sys_GetCurrentUser(): String {
        var s_userName: String = ""
        if (System.getProperty("user.name").also { s_userName = it }.isEmpty()) {
            s_userName = "player"
        }
        return s_userName
    }

}
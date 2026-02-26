package neo.sys

import com.sun.management.OperatingSystemMXBean
import neo.TempDump.TODO_Exception
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
    fun Sys_LockMemory(ptr: Any, bytes: Int): Boolean {
        throw TODO_Exception()
        //	return ( VirtualLock( ptr, (SIZE_T)bytes ) != FALSE );
    }

    /*
     ================
     Sys_UnlockMemory
     ================
     */
    fun Sys_UnlockMemory(ptr: Any, bytes: Int): Boolean {
        throw TODO_Exception()
        //	return ( VirtualUnlock( ptr, (SIZE_T)bytes ) != FALSE );
    }


    fun Sys_GetCurrentUser(): String {
        var s_userName: String = ""
        if (System.getProperty("user.name").also { s_userName = it }.isEmpty()) {
            s_userName = "player"
        }
        return s_userName
    }

}
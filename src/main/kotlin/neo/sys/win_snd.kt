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

import neo.Sound.snd_local
import neo.Sound.snd_local.idAudioHardware
import neo.Sound.snd_system.idSoundSystemLocal
import neo.TempDump.TODO_Exception
import neo.framework.Common
import neo.framework.ID_OPENAL
import neo.idlib.math.MIXBUFFER_SAMPLES
import org.lwjgl.openal.ALC
import java.util.logging.Level
import java.util.logging.Logger

object win_snd {
    /*
     ===============
     Sys_LoadOpenAL
     ===============
     */
    fun Sys_LoadOpenAL(): Boolean {
        return if (ID_OPENAL) {
            try {
                ALC.create()
            } catch (ex: UnsatisfiedLinkError) {
                Logger.getLogger(win_snd::class.java.name).log(Level.SEVERE, null, ex)
                Common.common.Warning("LoadLibrary %s failed.", idSoundSystemLocal.s_libOpenAL.GetString()!!)
                return false
            } catch (ex: IllegalStateException) {
                return "ALC has already been created." == ex.message
            }
            true
        } else {
            false
        }
    }

    /*
     ===============
     Sys_FreeOpenAL
     ===============
     */
    fun Sys_FreeOpenAL() {
        ALC.destroy()
    }

    class idAudioHardwareWIN32 : idAudioHardware() {
        private val bitsPerSample = 0
        private val blockAlign = 0// channels * bits per sample / 8: sound frame size
        private val bufferSize = 0 // allocate buffer handed over to DirectSound
        private val numSpeakers = 0

        /*
         ===============
         idAudioHardwareWIN32::SetPrimaryBufferFormat
         Set primary buffer to a specified format
         For example, to set the primary buffer format to 22kHz stereo, 16-bit
         then:   dwPrimaryChannels = 2
         dwPrimaryFreq     = 22050,
         dwPrimaryBitRate  = 16
         ===============
         */
        fun SetPrimaryBufferFormat(dwPrimaryFreq: Int, dwPrimaryBitRate: Int, dwSpeakers: Int) {

        }

        override fun GetNumberOfSpeakers(): Int {
            return numSpeakers
        }

        override fun GetMixBufferSize(): Int {
            return MIXBUFFER_SAMPLES * blockAlign
        }

        override fun Flush(): Boolean {
            return true
        }

        override fun Write(value: Boolean) {}
        override fun GetMixBuffer(): ShortArray {
            return ShortArray(128)
        }

        override fun Initialize(): Boolean {
            // Set primary buffer format
            SetPrimaryBufferFormat(
                snd_local.PRIMARYFREQ,
                16,
                idSoundSystemLocal.s_numberOfSpeakers.GetInteger()
            )
            return true
        }

        override fun Lock(pDSLockedBuffer: Any, dwDSLockedBufferSize: Long): Boolean {
            throw TODO_Exception()
        }

        override fun Unlock(pDSLockedBuffer: Any, dwDSLockedBufferSize: Long): Boolean {
            throw TODO_Exception()
        }

        override fun GetCurrentPosition(pdwCurrentWriteCursor: Long): Boolean {
            throw TODO_Exception()
        }
    }
}
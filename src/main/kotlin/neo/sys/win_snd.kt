/*
 * Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
 * Translated to Kotlin by Dr. Feederino with support of Claude Code
 *
 * This file is part of the Doom 3 Kotlin project.
 * Original source: neo/sys/win32/win_snd.cpp (DirectSound), neo/sys/snd_backend.cpp (OpenAL)
 *
 * NOTE: Differs from C++ — The original C++ uses DirectSound (Win32) or OpenAL
 * (dhewm3) for audio output. This Kotlin port uses OpenAL via LWJGL.
 * The idAudioHardwareWIN32 class retains the DirectSound API shape but its
 * Lock/Unlock/GetCurrentPosition methods are stubs.
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
        private val blockAlign // channels * bits per sample / 8: sound frame size
                = 0
        private val bufferSize // allocate buffer handed over to DirectSound
                = 0

        //        private LPDIRECTSOUND m_pDS;
        //        private LPDIRECTSOUNDBUFFER pDSBPrimary;
        //        private idAudioBufferWIN32 speakers;
        //
        private val numSpeakers = 0

        // ~idAudioHardwareWIN32();
        // public	boolean InitializeSpeakers( byte *buffer, int bufferSize, dword dwPrimaryFreq, dword dwPrimaryBitRate, dword dwSpeakers );
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
//    HRESULT             hr;
//
//    if( m_pDS == null ) {
//        return;
//	}
//
//	ulong cfgSpeakers;
//	m_pDS->GetSpeakerConfig( &cfgSpeakers );
//
//	DSCAPS dscaps;
//	dscaps.dwSize = sizeof(DSCAPS);
//    m_pDS->GetCaps(&dscaps);
//
//	if (dscaps.dwFlags & DSCAPS_EMULDRIVER) {
//		return;
//	}
//
//	// Get the primary buffer
//    DSBUFFERDESC dsbd;
//    ZeroMemory( &dsbd, sizeof(DSBUFFERDESC) );
//    dsbd.dwSize        = sizeof(DSBUFFERDESC);
//    dsbd.dwFlags       = DSBCAPS_PRIMARYBUFFER;
//    dsbd.dwBufferBytes = 0;
//    dsbd.lpwfxFormat   = NULL;
//
//	// Obtain write-primary cooperative level.
//	if( FAILED( hr = m_pDS->SetCooperativeLevel(win32.hWnd, DSSCL_PRIORITY ) ) ) {
//        DXTRACE_ERR( TEXT("SetPrimaryBufferFormat"), hr );
//		return;
//	}
//
//	if( FAILED( hr = m_pDS->CreateSoundBuffer( &dsbd, &pDSBPrimary, NULL ) ) ) {
//		return;
//	}
//
//	if ( dwSpeakers == 6 && (cfgSpeakers == DSSPEAKER_5POINT1 || cfgSpeakers == DSSPEAKER_SURROUND) ) {
//		WAVEFORMATEXTENSIBLE 	waveFormatPCMEx;
//		ZeroMemory( &waveFormatPCMEx, sizeof(WAVEFORMATEXTENSIBLE) );
//
// 		waveFormatPCMEx.Format.wFormatTag = WAVE_FORMAT_EXTENSIBLE;
//		waveFormatPCMEx.Format.nChannels = 6;
//		waveFormatPCMEx.Format.nSamplesPerSec = dwPrimaryFreq;
//		waveFormatPCMEx.Format.wBitsPerSample  = (WORD) dwPrimaryBitRate;
//		waveFormatPCMEx.Format.nBlockAlign = waveFormatPCMEx.Format.wBitsPerSample / 8 * waveFormatPCMEx.Format.nChannels;
//		waveFormatPCMEx.Format.nAvgBytesPerSec = waveFormatPCMEx.Format.nSamplesPerSec * waveFormatPCMEx.Format.nBlockAlign;
//		waveFormatPCMEx.dwChannelMask = KSAUDIO_SPEAKER_5POINT1;
//									 // SPEAKER_FRONT_LEFT | SPEAKER_FRONT_RIGHT |
//									 // SPEAKER_FRONT_CENTER | SPEAKER_LOW_FREQUENCY |
//									 // SPEAKER_BACK_LEFT  | SPEAKER_BACK_RIGHT
//		waveFormatPCMEx.SubFormat =  KSDATAFORMAT_SUBTYPE_PCM;  // Specify PCM
//		waveFormatPCMEx.Format.cbSize = sizeof(WAVEFORMATEXTENSIBLE);
//		waveFormatPCMEx.Samples.wValidBitsPerSample = 16;
//
//		if( FAILED( hr = pDSBPrimary->SetFormat((WAVEFORMATEX*)&waveFormatPCMEx) ) ) {
//	        DXTRACE_ERR( TEXT("SetPrimaryBufferFormat"), hr );
//			return;
//		}
//		numSpeakers = 6;		// force it to think 5.1
//		blockAlign = waveFormatPCMEx.Format.nBlockAlign;
//	} else {
//		if (dwSpeakers == 6) {
//			common->Printf("sound: hardware reported unable to use multisound, defaulted to stereo\n");
//		}
//		WAVEFORMATEX wfx;
//		ZeroMemory( &wfx, sizeof(WAVEFORMATEX) );
//		wfx.wFormatTag      = WAVE_FORMAT_PCM;
//		wfx.nChannels       = 2;
//		wfx.nSamplesPerSec  = dwPrimaryFreq;
//		wfx.wBitsPerSample  = (WORD) dwPrimaryBitRate;
//		wfx.nBlockAlign     = wfx.wBitsPerSample / 8 * wfx.nChannels;
//		wfx.nAvgBytesPerSec = wfx.nSamplesPerSec * wfx.nBlockAlign;
//		wfx.cbSize = sizeof(WAVEFORMATEX);
//
//		if( FAILED( hr = pDSBPrimary->SetFormat(&wfx) ) ) {
//			return;
//		}
//		numSpeakers = 2;		// force it to think stereo
//		blockAlign = wfx.nBlockAlign;
//	}
//
//	byte *speakerData;
//	bufferSize = MIXBUFFER_SAMPLES * sizeof(word) * numSpeakers * ROOM_SLICES_IN_BUFFER;
//	speakerData = (byte *)Mem_Alloc( bufferSize );
//	memset( speakerData, 0, bufferSize );
//
//	InitializeSpeakers( speakerData, bufferSize, dwPrimaryFreq, dwPrimaryBitRate, numSpeakers );
        }

        // public    int Create( idWaveFile* pWaveFile, idAudioBuffer** ppiab );
        // public    int Create( idAudioBuffer** ppSound, const char* strWaveFileName, dword dwCreationFlags = 0 );
        // public    int CreateFromMemory( idAudioBufferWIN32** ppSound, byte* pbData, ulong ulDataSize, waveformatextensible_t *pwfx, dword dwCreationFlags = 0 );
        override fun GetNumberOfSpeakers(): Int {
            return numSpeakers
        }

        override fun GetMixBufferSize(): Int {
            return MIXBUFFER_SAMPLES * blockAlign
        }

        // WIN32 driver doesn't support write API
        override fun Flush(): Boolean {
            return true
        }

        override fun Write(value: Boolean) {}
        override fun GetMixBuffer(): ShortArray {
            return ShortArray(128)
        }

        override fun Initialize(): Boolean {
//            throw new TODO_Exception();
            //            AudioInputStream  audioInputStream = AudioSystem.getAudioInputStream(null);
//            dataLine.
//
//            bufferSize = 0;
//            numSpeakers = 0;
//            blockAlign = 0;
//
//            SAFE_RELEASE(m_pDS);
//
//            // Create IDirectSound using the primary sound device
//            if (FAILED(hr = DirectSoundCreate(NULL,  & m_pDS, null))) {
//                return false;
//            }

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
            //	if (speakers) {
//		return speakers->Lock( pDSLockedBuffer, dwDSLockedBufferSize );
//	}
//	return false;
        }

        override fun Unlock(pDSLockedBuffer: Any, dwDSLockedBufferSize: Long): Boolean {
            throw TODO_Exception()
            //	if (speakers) {
//		return speakers->Unlock( pDSLockedBuffer, dwDSLockedBufferSize );
//	}
//	return false;
        }

        override fun GetCurrentPosition(pdwCurrentWriteCursor: Long): Boolean {
            throw TODO_Exception()
            //	if (speakers) {
//		return speakers->GetCurrentPosition( pdwCurrentWriteCursor );
//	}
//	return false;
        }
    }
}
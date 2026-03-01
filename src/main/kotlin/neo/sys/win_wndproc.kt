/*
 * Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
 * Translated to Kotlin by Dr. Feederino with support of Claude Code
 *
 * This file is part of the Doom 3 Kotlin project.
 * Original source: no direct C++ counterpart (Win32 message pump was in win_main.cpp)
 *
 * NOTE: This file contains scan code tables and a MapKey function from the
 * original Win32 WndProc. These are dead code in the GLFW-based port —
 * input mapping is handled by IN_DIMapKey in win_input.kt.
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

import neo.framework.KeyInput

object win_wndproc {
    //==========================================================================
    // Keep this in sync with the one in win_input.cpp
    // This one is used in the menu, the other one is used in game
    val s_scantokey /*[128]*/: IntArray =
        intArrayOf( //  0            1       2          3          4       5            6         7
            //  8            9       A          B          C       D            E         F
            0,
            27,
            '1'.code,
            '2'.code,
            '3'.code,
            '4'.code,
            '5'.code,
            '6'.code,
            '7'.code,
            '8'.code,
            '9'.code,
            '0'.code,
            '-'.code,
            '='.code,
            KeyInput.K_BACKSPACE,
            9,  // 0
            'q'.code,
            'w'.code,
            'e'.code,
            'r'.code,
            't'.code,
            'y'.code,
            'u'.code,
            'i'.code,
            'o'.code,
            'p'.code,
            '['.code,
            ']'.code,
            KeyInput.K_ENTER,
            KeyInput.K_CTRL,
            'a'.code,
            's'.code,  // 1
            'd'.code,
            'f'.code,
            'g'.code,
            'h'.code,
            'j'.code,
            'k'.code,
            'l'.code,
            ';'.code,
            '\''.code,
            '`'.code,
            KeyInput.K_SHIFT,
            '\\'.code,
            'z'.code,
            'x'.code,
            'c'.code,
            'v'.code,  // 2
            'b'.code,
            'n'.code,
            'm'.code,
            ','.code,
            '.'.code,
            '/'.code,
            KeyInput.K_SHIFT,
            KeyInput.K_KP_STAR,
            KeyInput.K_ALT,
            ' '.code,
            KeyInput.K_CAPSLOCK,
            KeyInput.K_F1,
            KeyInput.K_F2,
            KeyInput.K_F3,
            KeyInput.K_F4,
            KeyInput.K_F5,  // 3
            KeyInput.K_F6,
            KeyInput.K_F7,
            KeyInput.K_F8,
            KeyInput.K_F9,
            KeyInput.K_F10,
            KeyInput.K_PAUSE,
            KeyInput.K_SCROLL,
            KeyInput.K_HOME,
            KeyInput.K_UPARROW,
            KeyInput.K_PGUP,
            KeyInput.K_KP_MINUS,
            KeyInput.K_LEFTARROW,
            KeyInput.K_KP_5,
            KeyInput.K_RIGHTARROW,
            KeyInput.K_KP_PLUS,
            KeyInput.K_END,  // 4
            KeyInput.K_DOWNARROW,
            KeyInput.K_PGDN,
            KeyInput.K_INS,
            KeyInput.K_DEL,
            0,
            0,
            0,
            KeyInput.K_F11,
            KeyInput.K_F12,
            0,
            0,
            KeyInput.K_LWIN,
            KeyInput.K_RWIN,
            KeyInput.K_MENU,
            0,
            0,  // 5
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,  // 6
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0 // 7
        )
    var s_scantoshift /*[128]*/: IntArray =
        intArrayOf( //  0            1       2          3          4       5            6         7
            //  8            9       A          B          C       D            E         F
            0,
            27,
            '!'.code,
            '@'.code,
            '#'.code,
            '$'.code,
            '%'.code,
            '^'.code,
            '&'.code,
            '*'.code,
            '('.code,
            ')'.code,
            '_'.code,
            '+'.code,
            KeyInput.K_BACKSPACE,
            9,  // 0
            'Q'.code,
            'W'.code,
            'E'.code,
            'R'.code,
            'T'.code,
            'Y'.code,
            'U'.code,
            'I'.code,
            'O'.code,
            'P'.code,
            '{'.code,
            '}'.code,
            KeyInput.K_ENTER,
            KeyInput.K_CTRL,
            'A'.code,
            'S'.code,  // 1
            'D'.code,
            'F'.code,
            'G'.code,
            'H'.code,
            'J'.code,
            'K'.code,
            'L'.code,
            ':'.code,
            '|'.code,
            '~'.code,
            KeyInput.K_SHIFT,
            '\\'.code,
            'Z'.code,
            'X'.code,
            'C'.code,
            'V'.code,  // 2
            'B'.code,
            'N'.code,
            'M'.code,
            '<'.code,
            '>'.code,
            '?'.code,
            KeyInput.K_SHIFT,
            KeyInput.K_KP_STAR,
            KeyInput.K_ALT,
            ' '.code,
            KeyInput.K_CAPSLOCK,
            KeyInput.K_F1,
            KeyInput.K_F2,
            KeyInput.K_F3,
            KeyInput.K_F4,
            KeyInput.K_F5,  // 3
            KeyInput.K_F6,
            KeyInput.K_F7,
            KeyInput.K_F8,
            KeyInput.K_F9,
            KeyInput.K_F10,
            KeyInput.K_PAUSE,
            KeyInput.K_SCROLL,
            KeyInput.K_HOME,
            KeyInput.K_UPARROW,
            KeyInput.K_PGUP,
            KeyInput.K_KP_MINUS,
            KeyInput.K_LEFTARROW,
            KeyInput.K_KP_5,
            KeyInput.K_RIGHTARROW,
            KeyInput.K_KP_PLUS,
            KeyInput.K_END,  // 4
            KeyInput.K_DOWNARROW,
            KeyInput.K_PGDN,
            KeyInput.K_INS,
            KeyInput.K_DEL,
            0,
            0,
            0,
            KeyInput.K_F11,
            KeyInput.K_F12,
            0,
            0,
            KeyInput.K_LWIN,
            KeyInput.K_RWIN,
            KeyInput.K_MENU,
            0,
            0,  // 5
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,  // 6
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0 // 7
        )

    /*
     =======
     MapKey

     Map from windows to Doom keynums
     =======
     */
    @Deprecated("")
    fun MapKey(key: Int): Int {
        val result: Int
        val modified: Int
        val is_extended: Boolean
        modified = key shr 16 and 255
        if (modified > 127) {
            return 0
        }
        is_extended = key and (1 shl 24) != 0

        //Check for certain extended character codes.
        //The specific case we are testing is the numpad / is not being translated
        //properly for localized builds.
        if (is_extended) {
            when (modified) {
                0x35 -> return KeyInput.K_KP_SLASH
            }
        }
        val scanToKey = win_input.Sys_GetScanTable()
        result = scanToKey[modified].code

        // common->Printf( "Key: 0x%08x Modified: 0x%02x Extended: %s Result: 0x%02x\n", key, modified, (is_extended?"Y":"N"), result);
        if (is_extended) {
            when (result) {
                KeyInput.K_PAUSE -> return KeyInput.K_KP_NUMLOCK
                0x0D -> return KeyInput.K_KP_ENTER
                0x2F -> return KeyInput.K_KP_SLASH
                0xAF -> return KeyInput.K_KP_PLUS
                KeyInput.K_KP_STAR -> return KeyInput.K_PRINT_SCR
                KeyInput.K_ALT -> return KeyInput.K_RIGHT_ALT
            }
        } else {
            when (result) {
                KeyInput.K_HOME -> return KeyInput.K_KP_HOME
                KeyInput.K_UPARROW -> return KeyInput.K_KP_UPARROW
                KeyInput.K_PGUP -> return KeyInput.K_KP_PGUP
                KeyInput.K_LEFTARROW -> return KeyInput.K_KP_LEFTARROW
                KeyInput.K_RIGHTARROW -> return KeyInput.K_KP_RIGHTARROW
                KeyInput.K_END -> return KeyInput.K_KP_END
                KeyInput.K_DOWNARROW -> return KeyInput.K_KP_DOWNARROW
                KeyInput.K_PGDN -> return KeyInput.K_KP_PGDN
                KeyInput.K_INS -> return KeyInput.K_KP_INS
                KeyInput.K_DEL -> return KeyInput.K_KP_DEL
            }
        }
        return result
    }
}
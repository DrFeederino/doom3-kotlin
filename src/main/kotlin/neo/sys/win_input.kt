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

import neo.framework.Common
import neo.framework.KeyInput
import neo.idlib.Text.Str.idStr
import neo.idlib.containers.CBool
import neo.idlib.containers.CInt
import neo.idlib.idLib
import neo.sys.win_local.Win32Vars_t
import org.lwjgl.glfw.GLFW
import java.awt.event.InputEvent
import java.time.Instant

object win_input {
    const val CHAR_FIRSTREPEAT = 200
    const val CHAR_REPEAT = 100
    const val DINPUT_BUFFERSIZE = 256
    val polled_didod: Array<InputEvent?> =
        arrayOfNulls<InputEvent?>(DINPUT_BUFFERSIZE) // Receives buffered data
    val s_scantokey: CharArray =
        charArrayOf(
            0.toChar(),
            27.toChar(),
            '1',
            '2',
            '3',
            '4',
            '5',
            '6',
            '7',
            '8',
            '9',
            '0',
            '-',
            '=',
            KeyInput.K_BACKSPACE.toChar(),
            9.toChar(),  // 0
            'q',
            'w',
            'e',
            'r',
            't',
            'y',
            'u',
            'i',
            'o',
            'p',
            '[',
            ']',
            KeyInput.K_ENTER.toChar(),
            KeyInput.K_CTRL.toChar(),
            'a',
            's',  // 1
            'd',
            'f',
            'g',
            'h',
            'j',
            'k',
            'l',
            ';',
            '\'',
            '`',
            KeyInput.K_SHIFT.toChar(),
            '\\',
            'z',
            'x',
            'c',
            'v',  // 2
            'b',
            'n',
            'm',
            ',',
            '.',
            '/',
            KeyInput.K_SHIFT.toChar(),
            KeyInput.K_KP_STAR.toChar(),
            KeyInput.K_ALT.toChar(),
            ' ',
            KeyInput.K_CAPSLOCK.toChar(),
            KeyInput.K_F1.toChar(),
            KeyInput.K_F2.toChar(),
            KeyInput.K_F3.toChar(),
            KeyInput.K_F4.toChar(),
            KeyInput.K_F5.toChar(),  // 3
            KeyInput.K_F6.toChar(),
            KeyInput.K_F7.toChar(),
            KeyInput.K_F8.toChar(),
            KeyInput.K_F9.toChar(),
            KeyInput.K_F10.toChar(),
            KeyInput.K_PAUSE.toChar(),
            KeyInput.K_SCROLL.toChar(),
            KeyInput.K_HOME.toChar(),
            KeyInput.K_UPARROW.toChar(),
            KeyInput.K_PGUP.toChar(),
            KeyInput.K_KP_MINUS.toChar(),
            KeyInput.K_LEFTARROW.toChar(),
            KeyInput.K_KP_5.toChar(),
            KeyInput.K_RIGHTARROW.toChar(),
            KeyInput.K_KP_PLUS.toChar(),
            KeyInput.K_END.toChar(),  // 4
            KeyInput.K_DOWNARROW.toChar(),
            KeyInput.K_PGDN.toChar(),
            KeyInput.K_INS.toChar(),
            KeyInput.K_DEL.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            KeyInput.K_F11.toChar(),
            KeyInput.K_F12.toChar(),
            0.toChar(),
            0.toChar(),
            KeyInput.K_LWIN.toChar(),
            KeyInput.K_RWIN.toChar(),
            KeyInput.K_MENU.toChar(),
            0.toChar(),
            0.toChar(),  // 5
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),  // 6
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),  // 7
            // shifted
            0.toChar(),
            27.toChar(),
            '!',
            '@',
            '#',
            '$',
            '%',
            '^',
            '&',
            '*',
            '(',
            ')',
            '_',
            '+',
            KeyInput.K_BACKSPACE.toChar(),
            9.toChar(),  // 0
            'Q',
            'W',
            'E',
            'R',
            'T',
            'Y',
            'U',
            'I',
            'O',
            'P',
            '[',
            ']',
            KeyInput.K_ENTER.toChar(),
            KeyInput.K_CTRL.toChar(),
            'A',
            'S',  // 1
            'D',
            'F',
            'G',
            'H',
            'J',
            'K',
            'L',
            ';',
            '\'',
            '~',
            KeyInput.K_SHIFT.toChar(),
            '\\',
            'Z',
            'X',
            'C',
            'V',  // 2
            'B',
            'B',
            'M',
            ',',
            '.',
            '/',
            KeyInput.K_SHIFT.toChar(),
            KeyInput.K_KP_STAR.toChar(),
            KeyInput.K_ALT.toChar(),
            ' ',
            KeyInput.K_CAPSLOCK.toChar(),
            KeyInput.K_F1.toChar(),
            KeyInput.K_F2.toChar(),
            KeyInput.K_F3.toChar(),
            KeyInput.K_F4.toChar(),
            KeyInput.K_F5.toChar(),  // 3
            KeyInput.K_F6.toChar(),
            KeyInput.K_F7.toChar(),
            KeyInput.K_F8.toChar(),
            KeyInput.K_F9.toChar(),
            KeyInput.K_F10.toChar(),
            KeyInput.K_PAUSE.toChar(),
            KeyInput.K_SCROLL.toChar(),
            KeyInput.K_HOME.toChar(),
            KeyInput.K_UPARROW.toChar(),
            KeyInput.K_PGUP.toChar(),
            KeyInput.K_KP_MINUS.toChar(),
            KeyInput.K_LEFTARROW.toChar(),
            KeyInput.K_KP_5.toChar(),
            KeyInput.K_RIGHTARROW.toChar(),
            KeyInput.K_KP_PLUS.toChar(),
            KeyInput.K_END.toChar(),  // 4
            KeyInput.K_DOWNARROW.toChar(),
            KeyInput.K_PGDN.toChar(),
            KeyInput.K_INS.toChar(),
            KeyInput.K_DEL.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            KeyInput.K_F11.toChar(),
            KeyInput.K_F12.toChar(),
            0.toChar(),
            0.toChar(),
            KeyInput.K_LWIN.toChar(),
            KeyInput.K_RWIN.toChar(),
            KeyInput.K_MENU.toChar(),
            0.toChar(),
            0.toChar(),  // 5
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),  // 6
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar()// 7
        )
    val s_scantokey_french: CharArray =
        charArrayOf(
            0.toChar(),
            27.toChar(),
            '1',
            '2',
            '3',
            '4',
            '5',
            '6',
            '7',
            '8',
            '9',
            '0',
            ')',
            '=',
            KeyInput.K_BACKSPACE.toChar(),
            9.toChar(),  // 0
            'a',
            'z',
            'e',
            'r',
            't',
            'y',
            'u',
            'i',
            'o',
            'p',
            '^',
            '$',
            KeyInput.K_ENTER.toChar(),
            KeyInput.K_CTRL.toChar(),
            'q',
            's',  // 1
            'd',
            'f',
            'g',
            'h',
            'j',
            'k',
            'l',
            'm',
            'ù',
            '`',
            KeyInput.K_SHIFT.toChar(),
            '*',
            'w',
            'x',
            'c',
            'v',  // 2
            'b',
            'n',
            ',',
            ';',
            ':',
            '!',
            KeyInput.K_SHIFT.toChar(),
            KeyInput.K_KP_STAR.toChar(),
            KeyInput.K_ALT.toChar(),
            ' ',
            KeyInput.K_CAPSLOCK.toChar(),
            KeyInput.K_F1.toChar(),
            KeyInput.K_F2.toChar(),
            KeyInput.K_F3.toChar(),
            KeyInput.K_F4.toChar(),
            KeyInput.K_F5.toChar(),  // 3
            KeyInput.K_F6.toChar(),
            KeyInput.K_F7.toChar(),
            KeyInput.K_F8.toChar(),
            KeyInput.K_F9.toChar(),
            KeyInput.K_F10.toChar(),
            KeyInput.K_PAUSE.toChar(),
            KeyInput.K_SCROLL.toChar(),
            KeyInput.K_HOME.toChar(),
            KeyInput.K_UPARROW.toChar(),
            KeyInput.K_PGUP.toChar(),
            KeyInput.K_KP_MINUS.toChar(),
            KeyInput.K_LEFTARROW.toChar(),
            KeyInput.K_KP_5.toChar(),
            KeyInput.K_RIGHTARROW.toChar(),
            KeyInput.K_KP_PLUS.toChar(),
            KeyInput.K_END.toChar(),  // 4
            KeyInput.K_DOWNARROW.toChar(),
            KeyInput.K_PGDN.toChar(),
            KeyInput.K_INS.toChar(),
            KeyInput.K_DEL.toChar(),
            0.toChar(),
            0.toChar(),
            '<',
            KeyInput.K_F11.toChar(),
            KeyInput.K_F12.toChar(),
            0.toChar(),
            0.toChar(),
            KeyInput.K_LWIN.toChar(),
            KeyInput.K_RWIN.toChar(),
            KeyInput.K_MENU.toChar(),
            0.toChar(),
            0.toChar(),  // 5
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),  // 6
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),  // 7
            // shifted
            0.toChar(),
            27.toChar(),
            '&',
            'é',
            '\"',
            '\'',
            '(',
            '-',
            'è',
            '_',
            'ç',
            'à',
            '°',
            '+',
            KeyInput.K_BACKSPACE.toChar(),
            9.toChar(),  // 0
            'a',
            'z',
            'e',
            'r',
            't',
            'y',
            'u',
            'i',
            'o',
            'p',
            '^',
            '$',
            KeyInput.K_ENTER.toChar(),
            KeyInput.K_CTRL.toChar(),
            'q',
            's',  // 1
            'd',
            'f',
            'g',
            'h',
            'j',
            'k',
            'l',
            'm',
            'ù',
            0.toChar(),
            KeyInput.K_SHIFT.toChar(),
            '*',
            'w',
            'x',
            'c',
            'v',  // 2
            'b',
            'n',
            ',',
            ';',
            ':',
            '!',
            KeyInput.K_SHIFT.toChar(),
            KeyInput.K_KP_STAR.toChar(),
            KeyInput.K_ALT.toChar(),
            ' ',
            KeyInput.K_CAPSLOCK.toChar(),
            KeyInput.K_F1.toChar(),
            KeyInput.K_F2.toChar(),
            KeyInput.K_F3.toChar(),
            KeyInput.K_F4.toChar(),
            KeyInput.K_F5.toChar(),  // 3
            KeyInput.K_F6.toChar(),
            KeyInput.K_F7.toChar(),
            KeyInput.K_F8.toChar(),
            KeyInput.K_F9.toChar(),
            KeyInput.K_F10.toChar(),
            KeyInput.K_PAUSE.toChar(),
            KeyInput.K_SCROLL.toChar(),
            KeyInput.K_HOME.toChar(),
            KeyInput.K_UPARROW.toChar(),
            KeyInput.K_PGUP.toChar(),
            KeyInput.K_KP_MINUS.toChar(),
            KeyInput.K_LEFTARROW.toChar(),
            KeyInput.K_KP_5.toChar(),
            KeyInput.K_RIGHTARROW.toChar(),
            KeyInput.K_KP_PLUS.toChar(),
            KeyInput.K_END.toChar(),  // 4
            KeyInput.K_DOWNARROW.toChar(),
            KeyInput.K_PGDN.toChar(),
            KeyInput.K_INS.toChar(),
            KeyInput.K_DEL.toChar(),
            0.toChar(),
            0.toChar(),
            '<',
            KeyInput.K_F11.toChar(),
            KeyInput.K_F12.toChar(),
            0.toChar(),
            0.toChar(),
            KeyInput.K_LWIN.toChar(),
            KeyInput.K_RWIN.toChar(),
            KeyInput.K_MENU.toChar(),
            0.toChar(),
            0.toChar(),  // 5
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),  // 6
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar()
        )
    val s_scantokey_german: CharArray =
        charArrayOf(
            0.toChar(),
            27.toChar(),
            '1',
            '2',
            '3',
            '4',
            '5',
            '6',
            '7',
            '8',
            '9',
            '0',
            '?',
            '\'',
            KeyInput.K_BACKSPACE.toChar(),
            9.toChar(),  // 0
            'q',
            'w',
            'e',
            'r',
            't',
            'z',
            'u',
            'i',
            'o',
            'p',
            '=',
            '+',
            KeyInput.K_ENTER.toChar(),
            KeyInput.K_CTRL.toChar(),
            'a',
            's',  // 1
            'd',
            'f',
            'g',
            'h',
            'j',
            'k',
            'l',
            '[',
            ']',
            '`',
            KeyInput.K_SHIFT.toChar(),
            '#',
            'y',
            'x',
            'c',
            'v',  // 2
            'b',
            'n',
            'm',
            ',',
            '.',
            '-',
            KeyInput.K_SHIFT.toChar(),
            KeyInput.K_KP_STAR.toChar(),
            KeyInput.K_ALT.toChar(),
            ' ',
            KeyInput.K_CAPSLOCK.toChar(),
            KeyInput.K_F1.toChar(),
            KeyInput.K_F2.toChar(),
            KeyInput.K_F3.toChar(),
            KeyInput.K_F4.toChar(),
            KeyInput.K_F5.toChar(),  // 3
            KeyInput.K_F6.toChar(),
            KeyInput.K_F7.toChar(),
            KeyInput.K_F8.toChar(),
            KeyInput.K_F9.toChar(),
            KeyInput.K_F10.toChar(),
            KeyInput.K_PAUSE.toChar(),
            KeyInput.K_SCROLL.toChar(),
            KeyInput.K_HOME.toChar(),
            KeyInput.K_UPARROW.toChar(),
            KeyInput.K_PGUP.toChar(),
            KeyInput.K_KP_MINUS.toChar(),
            KeyInput.K_LEFTARROW.toChar(),
            KeyInput.K_KP_5.toChar(),
            KeyInput.K_RIGHTARROW.toChar(),
            KeyInput.K_KP_PLUS.toChar(),
            KeyInput.K_END.toChar(),  // 4
            KeyInput.K_DOWNARROW.toChar(),
            KeyInput.K_PGDN.toChar(),
            KeyInput.K_INS.toChar(),
            KeyInput.K_DEL.toChar(),
            0.toChar(),
            0.toChar(),
            '<',
            KeyInput.K_F11.toChar(),
            KeyInput.K_F12.toChar(),
            0.toChar(),
            0.toChar(),
            KeyInput.K_LWIN.toChar(),
            KeyInput.K_RWIN.toChar(),
            KeyInput.K_MENU.toChar(),
            0.toChar(),
            0.toChar(),  // 5
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),  // 6
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),  // 7
            // shifted
            0.toChar(),
            27.toChar(),
            '1',
            '2',
            '3',
            '4',
            '5',
            '6',
            '7',
            '8',
            '9',
            '0',
            '?',
            '\'',
            KeyInput.K_BACKSPACE.toChar(),
            9.toChar(),  // 0
            'q',
            'w',
            'e',
            'r',
            't',
            'z',
            'u',
            'i',
            'o',
            'p',
            '=',
            '+',
            KeyInput.K_ENTER.toChar(),
            KeyInput.K_CTRL.toChar(),
            'a',
            's',  // 1
            'd',
            'f',
            'g',
            'h',
            'j',
            'k',
            'l',
            '[',
            ']',
            '`',
            KeyInput.K_SHIFT.toChar(),
            '#',
            'y',
            'x',
            'c',
            'v',  // 2
            'b',
            'n',
            'm',
            ',',
            '.',
            '-',
            KeyInput.K_SHIFT.toChar(),
            KeyInput.K_KP_STAR.toChar(),
            KeyInput.K_ALT.toChar(),
            ' ',
            KeyInput.K_CAPSLOCK.toChar(),
            KeyInput.K_F1.toChar(),
            KeyInput.K_F2.toChar(),
            KeyInput.K_F3.toChar(),
            KeyInput.K_F4.toChar(),
            KeyInput.K_F5.toChar(),  // 3
            KeyInput.K_F6.toChar(),
            KeyInput.K_F7.toChar(),
            KeyInput.K_F8.toChar(),
            KeyInput.K_F9.toChar(),
            KeyInput.K_F10.toChar(),
            KeyInput.K_PAUSE.toChar(),
            KeyInput.K_SCROLL.toChar(),
            KeyInput.K_HOME.toChar(),
            KeyInput.K_UPARROW.toChar(),
            KeyInput.K_PGUP.toChar(),
            KeyInput.K_KP_MINUS.toChar(),
            KeyInput.K_LEFTARROW.toChar(),
            KeyInput.K_KP_5.toChar(),
            KeyInput.K_RIGHTARROW.toChar(),
            KeyInput.K_KP_PLUS.toChar(),
            KeyInput.K_END.toChar(),  // 4
            KeyInput.K_DOWNARROW.toChar(),
            KeyInput.K_PGDN.toChar(),
            KeyInput.K_INS.toChar(),
            KeyInput.K_DEL.toChar(),
            0.toChar(),
            0.toChar(),
            '<',
            KeyInput.K_F11.toChar(),
            KeyInput.K_F12.toChar(),
            0.toChar(),
            0.toChar(),
            KeyInput.K_LWIN.toChar(),
            KeyInput.K_RWIN.toChar(),
            KeyInput.K_MENU.toChar(),
            0.toChar(),
            0.toChar(),  // 5
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),  // 6
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar()
        )
    val s_scantokey_italian: CharArray =
        charArrayOf(
            0.toChar(),
            27.toChar(),
            '1',
            '2',
            '3',
            '4',
            '5',
            '6',
            '7',
            '8',
            '9',
            '0',
            '\'',
            'ì',
            KeyInput.K_BACKSPACE.toChar(),
            9.toChar(),  // 0
            'q',
            'w',
            'e',
            'r',
            't',
            'y',
            'u',
            'i',
            'o',
            'p',
            'è',
            '+',
            KeyInput.K_ENTER.toChar(),
            KeyInput.K_CTRL.toChar(),
            'a',
            's',  // 1
            'd',
            'f',
            'g',
            'h',
            'j',
            'k',
            'l',
            'ò',
            'à',
            '\\',
            KeyInput.K_SHIFT.toChar(),
            'ù',
            'z',
            'x',
            'c',
            'v',  // 2
            'b',
            'n',
            'm',
            ',',
            '.',
            '-',
            KeyInput.K_SHIFT.toChar(),
            KeyInput.K_KP_STAR.toChar(),
            KeyInput.K_ALT.toChar(),
            ' ',
            KeyInput.K_CAPSLOCK.toChar(),
            KeyInput.K_F1.toChar(),
            KeyInput.K_F2.toChar(),
            KeyInput.K_F3.toChar(),
            KeyInput.K_F4.toChar(),
            KeyInput.K_F5.toChar(),  // 3
            KeyInput.K_F6.toChar(),
            KeyInput.K_F7.toChar(),
            KeyInput.K_F8.toChar(),
            KeyInput.K_F9.toChar(),
            KeyInput.K_F10.toChar(),
            KeyInput.K_PAUSE.toChar(),
            KeyInput.K_SCROLL.toChar(),
            KeyInput.K_HOME.toChar(),
            KeyInput.K_UPARROW.toChar(),
            KeyInput.K_PGUP.toChar(),
            KeyInput.K_KP_MINUS.toChar(),
            KeyInput.K_LEFTARROW.toChar(),
            KeyInput.K_KP_5.toChar(),
            KeyInput.K_RIGHTARROW.toChar(),
            KeyInput.K_KP_PLUS.toChar(),
            KeyInput.K_END.toChar(),  // 4
            KeyInput.K_DOWNARROW.toChar(),
            KeyInput.K_PGDN.toChar(),
            KeyInput.K_INS.toChar(),
            KeyInput.K_DEL.toChar(),
            0.toChar(),
            0.toChar(),
            '<',
            KeyInput.K_F11.toChar(),
            KeyInput.K_F12.toChar(),
            0.toChar(),
            0.toChar(),
            KeyInput.K_LWIN.toChar(),
            KeyInput.K_RWIN.toChar(),
            KeyInput.K_MENU.toChar(),
            0.toChar(),
            0.toChar(),  // 5
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),  // 6
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),  // 7
            // shifted
            0.toChar(),
            27.toChar(),
            '!',
            '\"',
            '£',
            '$',
            '%',
            '&',
            '/',
            '(',
            ')',
            '=',
            '?',
            '^',
            KeyInput.K_BACKSPACE.toChar(),
            9.toChar(),  // 0
            'q',
            'w',
            'e',
            'r',
            't',
            'y',
            'u',
            'i',
            'o',
            'p',
            'é',
            '*',
            KeyInput.K_ENTER.toChar(),
            KeyInput.K_CTRL.toChar(),
            'a',
            's',  // 1
            'd',
            'f',
            'g',
            'h',
            'j',
            'k',
            'l',
            'ç',
            '°',
            '|',
            KeyInput.K_SHIFT.toChar(),
            '§',
            'z',
            'x',
            'c',
            'v',  // 2
            'b',
            'n',
            'm',
            ',',
            '.',
            '-',
            KeyInput.K_SHIFT.toChar(),
            KeyInput.K_KP_STAR.toChar(),
            KeyInput.K_ALT.toChar(),
            ' ',
            KeyInput.K_CAPSLOCK.toChar(),
            KeyInput.K_F1.toChar(),
            KeyInput.K_F2.toChar(),
            KeyInput.K_F3.toChar(),
            KeyInput.K_F4.toChar(),
            KeyInput.K_F5.toChar(),  // 3
            KeyInput.K_F6.toChar(),
            KeyInput.K_F7.toChar(),
            KeyInput.K_F8.toChar(),
            KeyInput.K_F9.toChar(),
            KeyInput.K_F10.toChar(),
            KeyInput.K_PAUSE.toChar(),
            KeyInput.K_SCROLL.toChar(),
            KeyInput.K_HOME.toChar(),
            KeyInput.K_UPARROW.toChar(),
            KeyInput.K_PGUP.toChar(),
            KeyInput.K_KP_MINUS.toChar(),
            KeyInput.K_LEFTARROW.toChar(),
            KeyInput.K_KP_5.toChar(),
            KeyInput.K_RIGHTARROW.toChar(),
            KeyInput.K_KP_PLUS.toChar(),
            KeyInput.K_END.toChar(),  // 4
            KeyInput.K_DOWNARROW.toChar(),
            KeyInput.K_PGDN.toChar(),
            KeyInput.K_INS.toChar(),
            KeyInput.K_DEL.toChar(),
            0.toChar(),
            0.toChar(),
            '<',
            KeyInput.K_F11.toChar(),
            KeyInput.K_F12.toChar(),
            0.toChar(),
            0.toChar(),
            KeyInput.K_LWIN.toChar(),
            KeyInput.K_RWIN.toChar(),
            KeyInput.K_MENU.toChar(),
            0.toChar(),
            0.toChar(),  // 5
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),  // 6
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar()
        )
    val s_scantokey_spanish: CharArray =
        charArrayOf(
            0.toChar(),
            27.toChar(),
            '1',
            '2',
            '3',
            '4',
            '5',
            '6',
            '7',
            '8',
            '9',
            '0',
            '\'',
            '¡',
            KeyInput.K_BACKSPACE.toChar(),
            9.toChar(),  // 0
            'q',
            'w',
            'e',
            'r',
            't',
            'y',
            'u',
            'i',
            'o',
            'p',
            '`',
            '+',
            KeyInput.K_ENTER.toChar(),
            KeyInput.K_CTRL.toChar(),
            'a',
            's',  // 1
            'd',
            'f',
            'g',
            'h',
            'j',
            'k',
            'l',
            'ñ',
            '´',
            'º',
            KeyInput.K_SHIFT.toChar(),
            'ç',
            'z',
            'x',
            'c',
            'v',  // 2
            'b',
            'n',
            'm',
            ',',
            '.',
            '-',
            KeyInput.K_SHIFT.toChar(),
            KeyInput.K_KP_STAR.toChar(),
            KeyInput.K_ALT.toChar(),
            ' ',
            KeyInput.K_CAPSLOCK.toChar(),
            KeyInput.K_F1.toChar(),
            KeyInput.K_F2.toChar(),
            KeyInput.K_F3.toChar(),
            KeyInput.K_F4.toChar(),
            KeyInput.K_F5.toChar(),  // 3
            KeyInput.K_F6.toChar(),
            KeyInput.K_F7.toChar(),
            KeyInput.K_F8.toChar(),
            KeyInput.K_F9.toChar(),
            KeyInput.K_F10.toChar(),
            KeyInput.K_PAUSE.toChar(),
            KeyInput.K_SCROLL.toChar(),
            KeyInput.K_HOME.toChar(),
            KeyInput.K_UPARROW.toChar(),
            KeyInput.K_PGUP.toChar(),
            KeyInput.K_KP_MINUS.toChar(),
            KeyInput.K_LEFTARROW.toChar(),
            KeyInput.K_KP_5.toChar(),
            KeyInput.K_RIGHTARROW.toChar(),
            KeyInput.K_KP_PLUS.toChar(),
            KeyInput.K_END.toChar(),  // 4
            KeyInput.K_DOWNARROW.toChar(),
            KeyInput.K_PGDN.toChar(),
            KeyInput.K_INS.toChar(),
            KeyInput.K_DEL.toChar(),
            0.toChar(),
            0.toChar(),
            '<',
            KeyInput.K_F11.toChar(),
            KeyInput.K_F12.toChar(),
            0.toChar(),
            0.toChar(),
            KeyInput.K_LWIN.toChar(),
            KeyInput.K_RWIN.toChar(),
            KeyInput.K_MENU.toChar(),
            0.toChar(),
            0.toChar(),  // 5
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),  // 6
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),  // 7
            // shifted
            0.toChar(),
            27.toChar(),
            '!',
            '\"',
            '·',
            '$',
            '%',
            '&',
            '/',
            '(',
            ')',
            '=',
            '?',
            '¿',
            KeyInput.K_BACKSPACE.toChar(),
            9.toChar(),  // 0
            'q',
            'w',
            'e',
            'r',
            't',
            'y',
            'u',
            'i',
            'o',
            'p',
            '^',
            '*',
            KeyInput.K_ENTER.toChar(),
            KeyInput.K_CTRL.toChar(),
            'a',
            's',  // 1
            'd',
            'f',
            'g',
            'h',
            'j',
            'k',
            'l',
            'Ñ',
            '¨',
            'ª',
            KeyInput.K_SHIFT.toChar(),
            'Ç',
            'z',
            'x',
            'c',
            'v',  // 2
            'b',
            'n',
            'm',
            ',',
            '.',
            '-',
            KeyInput.K_SHIFT.toChar(),
            KeyInput.K_KP_STAR.toChar(),
            KeyInput.K_ALT.toChar(),
            ' ',
            KeyInput.K_CAPSLOCK.toChar(),
            KeyInput.K_F1.toChar(),
            KeyInput.K_F2.toChar(),
            KeyInput.K_F3.toChar(),
            KeyInput.K_F4.toChar(),
            KeyInput.K_F5.toChar(),  // 3
            KeyInput.K_F6.toChar(),
            KeyInput.K_F7.toChar(),
            KeyInput.K_F8.toChar(),
            KeyInput.K_F9.toChar(),
            KeyInput.K_F10.toChar(),
            KeyInput.K_PAUSE.toChar(),
            KeyInput.K_SCROLL.toChar(),
            KeyInput.K_HOME.toChar(),
            KeyInput.K_UPARROW.toChar(),
            KeyInput.K_PGUP.toChar(),
            KeyInput.K_KP_MINUS.toChar(),
            KeyInput.K_LEFTARROW.toChar(),
            KeyInput.K_KP_5.toChar(),
            KeyInput.K_RIGHTARROW.toChar(),
            KeyInput.K_KP_PLUS.toChar(),
            KeyInput.K_END.toChar(),  // 4
            KeyInput.K_DOWNARROW.toChar(),
            KeyInput.K_PGDN.toChar(),
            KeyInput.K_INS.toChar(),
            KeyInput.K_DEL.toChar(),
            0.toChar(),
            0.toChar(),
            '<',
            KeyInput.K_F11.toChar(),
            KeyInput.K_F12.toChar(),
            0.toChar(),
            0.toChar(),
            KeyInput.K_LWIN.toChar(),
            KeyInput.K_RWIN.toChar(),
            KeyInput.K_MENU.toChar(),
            0.toChar(),
            0.toChar(),  // 5
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),  // 6
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar(),
            0.toChar()
        )
    val START_TIME = Instant.now().toEpochMilli()
    var diFetch = 0
    var keyScanTable = s_scantokey

    // this should be part of the scantables and the scan tables should be 512 bytes
    // (256 scan codes, shifted and unshifted).  Changing everything to use 512 byte
    // scan tables now might introduce bugs in tested code.  Since we only need to fix
    // the right-alt case for non-US keyboards, we're just using a special-case table
    // for it.  Eventually, the tables above should be fixed to handle all possible
    // scan codes instead of just the first 128.
    var rightAltKey = KeyInput.K_ALT
    var toggleFetch: Array<ByteArray> = Array(2) { ByteArray(256) }

    /*
     ============================================================

     DIRECT INPUT KEYBOARD CONTROL

     ============================================================
     */
    fun IN_StartupKeyboard(): Boolean {
        return false
    }

    /*
     =======
     MapKey

     Map from windows to quake keynums

     FIXME: scan code tables should include the upper 128 scan codes instead
     of having to special-case them here.  The current code makes it difficult
     to special-case conversions for non-US keyboards.  Currently the only
     special-case is for right alt.
     =======
     */
    fun IN_DIMapKey(key: Int, scancode: Int, mods: Int): Int {
        // GLFW maps all special/non-printable keys starting from 256
        if (key >= 256) {
            return when (key) {
                GLFW.GLFW_KEY_ESCAPE -> KeyInput.K_ESCAPE
                GLFW.GLFW_KEY_ENTER -> KeyInput.K_ENTER
                GLFW.GLFW_KEY_TAB -> KeyInput.K_TAB
                GLFW.GLFW_KEY_BACKSPACE -> KeyInput.K_BACKSPACE

                GLFW.GLFW_KEY_HOME -> KeyInput.K_HOME
                GLFW.GLFW_KEY_UP -> KeyInput.K_UPARROW
                GLFW.GLFW_KEY_PAGE_UP -> KeyInput.K_PGUP
                GLFW.GLFW_KEY_LEFT -> KeyInput.K_LEFTARROW
                GLFW.GLFW_KEY_RIGHT -> KeyInput.K_RIGHTARROW
                GLFW.GLFW_KEY_END -> KeyInput.K_END
                GLFW.GLFW_KEY_DOWN -> KeyInput.K_DOWNARROW
                GLFW.GLFW_KEY_PAGE_DOWN -> KeyInput.K_PGDN
                GLFW.GLFW_KEY_INSERT -> KeyInput.K_INS
                GLFW.GLFW_KEY_DELETE -> KeyInput.K_DEL
                GLFW.GLFW_KEY_LEFT_SHIFT -> KeyInput.K_SHIFT
                GLFW.GLFW_KEY_RIGHT_SHIFT -> KeyInput.K_RIGHT_SHIFT
                GLFW.GLFW_KEY_LEFT_CONTROL -> KeyInput.K_CTRL
                GLFW.GLFW_KEY_RIGHT_CONTROL -> KeyInput.K_RIGHT_CTRL  // was K_CTRL — RIGHT_CTRL must be distinct so bindings & IsDown() symmetry both work
                GLFW.GLFW_KEY_LEFT_ALT -> KeyInput.K_ALT
                GLFW.GLFW_KEY_RIGHT_ALT -> rightAltKey
                GLFW.GLFW_KEY_KP_ENTER -> KeyInput.K_KP_ENTER
                GLFW.GLFW_KEY_KP_EQUAL -> KeyInput.K_KP_EQUALS
                GLFW.GLFW_KEY_PAUSE -> KeyInput.K_PAUSE
                GLFW.GLFW_KEY_KP_DIVIDE -> KeyInput.K_KP_SLASH
                GLFW.GLFW_KEY_LEFT_SUPER -> KeyInput.K_LWIN
                GLFW.GLFW_KEY_RIGHT_SUPER -> KeyInput.K_RWIN
                GLFW.GLFW_KEY_MENU -> KeyInput.K_MENU
                GLFW.GLFW_KEY_PRINT_SCREEN -> KeyInput.K_PRINT_SCR
                GLFW.GLFW_KEY_CAPS_LOCK -> KeyInput.K_CAPSLOCK
                GLFW.GLFW_KEY_SCROLL_LOCK -> KeyInput.K_SCROLL
                GLFW.GLFW_KEY_F1 -> KeyInput.K_F1
                GLFW.GLFW_KEY_F2 -> KeyInput.K_F2
                GLFW.GLFW_KEY_F3 -> KeyInput.K_F3
                GLFW.GLFW_KEY_F4 -> KeyInput.K_F4
                GLFW.GLFW_KEY_F5 -> KeyInput.K_F5
                GLFW.GLFW_KEY_F6 -> KeyInput.K_F6
                GLFW.GLFW_KEY_F7 -> KeyInput.K_F7
                GLFW.GLFW_KEY_F8 -> KeyInput.K_F8
                GLFW.GLFW_KEY_F9 -> KeyInput.K_F9
                GLFW.GLFW_KEY_F10 -> KeyInput.K_F10
                GLFW.GLFW_KEY_F11 -> KeyInput.K_F11
                GLFW.GLFW_KEY_F12 -> KeyInput.K_F12
                GLFW.GLFW_KEY_KP_7 -> KeyInput.K_KP_HOME
                GLFW.GLFW_KEY_KP_8 -> KeyInput.K_KP_UPARROW
                GLFW.GLFW_KEY_KP_9 -> KeyInput.K_KP_PGUP
                GLFW.GLFW_KEY_KP_4 -> KeyInput.K_KP_LEFTARROW
                GLFW.GLFW_KEY_KP_5 -> KeyInput.K_KP_5
                GLFW.GLFW_KEY_KP_6 -> KeyInput.K_KP_RIGHTARROW
                GLFW.GLFW_KEY_KP_1 -> KeyInput.K_KP_END
                GLFW.GLFW_KEY_KP_2 -> KeyInput.K_KP_DOWNARROW
                GLFW.GLFW_KEY_KP_3 -> KeyInput.K_KP_PGDN
                GLFW.GLFW_KEY_KP_0 -> KeyInput.K_KP_INS
                GLFW.GLFW_KEY_KP_DECIMAL -> KeyInput.K_KP_DEL
                GLFW.GLFW_KEY_KP_SUBTRACT -> KeyInput.K_KP_MINUS
                GLFW.GLFW_KEY_KP_ADD -> KeyInput.K_KP_PLUS
                GLFW.GLFW_KEY_NUM_LOCK -> KeyInput.K_KP_NUMLOCK
                GLFW.GLFW_KEY_KP_MULTIPLY -> KeyInput.K_KP_STAR
                else -> 0
            }
        }

        // For standard printable keys (< 256), GLFW provides the key code as the
        // Unicode codepoint of the character (e.g. GLFW_KEY_A = 65 = 'A').
        // Doom 3 expects lowercase ASCII as key numbers for bindable keys.
        // The old s_scantokey table was designed for Windows DirectInput scancodes,
        // which do NOT match GLFW scancodes — using it produces wrong mappings.
        // Instead, just lowercase the GLFW key code, which works on all platforms.
        return if (key in 32..126) {
            Char(key).lowercaseChar().code
        } else 0
    }

    private fun getShiftedScancode(key: Int, scancode: Int, mods: Int): Int {
        var shiftedCode = scancode
        if (isShiftableKey(key)) {
            if (GLFW.GLFW_MOD_CAPS_LOCK and mods != 0 && isShiftableLetter(key)) shiftedCode += 128
            if (GLFW.GLFW_MOD_SHIFT and mods != 0) shiftedCode += 128
        }
        return shiftedCode % 256
    }

    private fun isShiftableKey(key: Int): Boolean {
        return (key == GLFW.GLFW_KEY_APOSTROPHE || key == GLFW.GLFW_KEY_COMMA || key == GLFW.GLFW_KEY_MINUS || key == GLFW.GLFW_KEY_PERIOD || key == GLFW.GLFW_KEY_SLASH || key == GLFW.GLFW_KEY_0 || key == GLFW.GLFW_KEY_1 || key == GLFW.GLFW_KEY_2 || key == GLFW.GLFW_KEY_3 || key == GLFW.GLFW_KEY_4 || key == GLFW.GLFW_KEY_5 || key == GLFW.GLFW_KEY_6 || key == GLFW.GLFW_KEY_7 || key == GLFW.GLFW_KEY_8 || key == GLFW.GLFW_KEY_9 || key == GLFW.GLFW_KEY_SEMICOLON || key == GLFW.GLFW_KEY_EQUAL || isShiftableLetter(
            key
        ) || key == GLFW.GLFW_KEY_LEFT_BRACKET || key == GLFW.GLFW_KEY_BACKSLASH || key == GLFW.GLFW_KEY_RIGHT_BRACKET || key == GLFW.GLFW_KEY_GRAVE_ACCENT || key == GLFW.GLFW_KEY_WORLD_1 || key == GLFW.GLFW_KEY_WORLD_2)
    }

    /*
     ============================================================

     DIRECT INPUT MOUSE CONTROL

     ============================================================
     */
    private fun isShiftableLetter(key: Int): Boolean {
        return key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z
    }

    /**
     * Computes the actual typed character for SE_CHAR events, taking shift and
     * caps lock state into account. GLFW key codes for printable keys are
     * uppercase ASCII (e.g. GLFW_KEY_A = 65 = 'A'). This function applies
     * the standard US keyboard shift mapping to produce the character the user
     * intended to type.
     */
    private fun getTypedChar(key: Int, mods: Int): Int {
        val shift = (mods and GLFW.GLFW_MOD_SHIFT) != 0
        val caps = (mods and GLFW.GLFW_MOD_CAPS_LOCK) != 0

        // Letters: GLFW_KEY_A..GLFW_KEY_Z are 65..90 (uppercase ASCII)
        if (key in GLFW.GLFW_KEY_A..GLFW.GLFW_KEY_Z) {
            val upper = shift xor caps  // shift XOR caps = uppercase
            return if (upper) key else (key + 32)  // 'A'=65 -> 'a'=97
        }

        // Non-letter printable keys: only shift matters (caps lock doesn't affect them)
        if (shift) {
            return when (key) {
                GLFW.GLFW_KEY_GRAVE_ACCENT -> '~'.code   // ` -> ~
                GLFW.GLFW_KEY_1 -> '!'.code
                GLFW.GLFW_KEY_2 -> '@'.code
                GLFW.GLFW_KEY_3 -> '#'.code
                GLFW.GLFW_KEY_4 -> '$'.code
                GLFW.GLFW_KEY_5 -> '%'.code
                GLFW.GLFW_KEY_6 -> '^'.code
                GLFW.GLFW_KEY_7 -> '&'.code
                GLFW.GLFW_KEY_8 -> '*'.code
                GLFW.GLFW_KEY_9 -> '('.code
                GLFW.GLFW_KEY_0 -> ')'.code
                GLFW.GLFW_KEY_MINUS -> '_'.code           // - -> _
                GLFW.GLFW_KEY_EQUAL -> '+'.code           // = -> +
                GLFW.GLFW_KEY_LEFT_BRACKET -> '{'.code    // [ -> {
                GLFW.GLFW_KEY_RIGHT_BRACKET -> '}'.code   // ] -> }
                GLFW.GLFW_KEY_BACKSLASH -> '|'.code       // \ -> |
                GLFW.GLFW_KEY_SEMICOLON -> ':'.code       // ; -> :
                GLFW.GLFW_KEY_APOSTROPHE -> '"'.code      // ' -> "
                GLFW.GLFW_KEY_COMMA -> '<'.code           // , -> <
                GLFW.GLFW_KEY_PERIOD -> '>'.code          // . -> >
                GLFW.GLFW_KEY_SLASH -> '?'.code           // / -> ?
                GLFW.GLFW_KEY_SPACE -> ' '.code
                else -> key  // fallback: return key as-is
            }
        }

        // Unshifted: GLFW key codes for digits and punctuation are already
        // the correct ASCII values (e.g. GLFW_KEY_0 = 48 = '0', GLFW_KEY_SPACE = 32)
        return when {
            key in 32..126 -> key
            else -> 0
        }
    }

    /*
     ==========================
     IN_DeactivateKeyboard
     ==========================
     */
    fun IN_DeactivateKeyboard() {
    }

    /*
     ========================
     IN_InitDIMouse
     ========================
     */
    fun IN_InitDIMouse(): Boolean {
        return false
    }

    /*
     ==========================
     IN_DeactivateMouse
     ==========================
     */
    fun IN_DeactivateMouse() {
    }

    /*
     ===========
     Sys_ShutdownInput
     ===========
     */
    fun Sys_ShutdownInput() {
        IN_DeactivateMouse()
        IN_DeactivateKeyboard()
    }

    /*
     ===========
     Sys_InitInput
     ===========
     */
    fun Sys_InitInput() {
        Common.common.Printf("\n------- Input Initialization -------\n")
        if (Win32Vars_t.in_mouse.GetBool()) {
            IN_InitDIMouse()
            // don't grab the mouse on initialization
            Sys_GrabMouseCursor(false)
        } else {
            Common.common.Printf("Mouse control not active.\n")
        }
        IN_StartupKeyboard()
        Common.common.Printf("------------------------------------\n")
        Win32Vars_t.in_mouse.ClearModified()
    }

    /*
     ===========
     Sys_InitScanTable
     ===========
     */
    fun Sys_InitScanTable() {
        val lang = idStr(idLib.cvarSystem.GetCVarString("sys_lang"))
        if (lang.Length() == 0) {
            lang.set("english")
        }
        if (lang.Icmp("english") == 0) {
            keyScanTable = s_scantokey
            // the only reason that english right alt binds as K_ALT is so that
            // users who were using right-alt before the patch don't suddenly find
            // that only left-alt is working.
            rightAltKey = KeyInput.K_ALT
        } else if (lang.Icmp("spanish") == 0) {
            keyScanTable = s_scantokey_spanish
            rightAltKey = KeyInput.K_RIGHT_ALT
        } else if (lang.Icmp("french") == 0) {
            keyScanTable = s_scantokey_french
            rightAltKey = KeyInput.K_RIGHT_ALT
        } else if (lang.Icmp("german") == 0) {
            keyScanTable = s_scantokey_german
            rightAltKey = KeyInput.K_RIGHT_ALT
        } else if (lang.Icmp("italian") == 0) {
            keyScanTable = s_scantokey_italian
            rightAltKey = KeyInput.K_RIGHT_ALT
        }
    }

    /*
     ==================
     Sys_GetScanTable
     ==================
     */
    fun Sys_GetScanTable(): CharArray {
        return keyScanTable
    }

    /*
     ===============
     Sys_GetConsoleKey
     ===============
     */

    fun Sys_GetConsoleKey(shifted: Boolean): Char {
        return keyScanTable[41 + if (shifted) 128 else 0]
    }

    fun Sys_GrabMouseCursor(grabIt: Boolean) {
    }

    /*
     ====================
     Sys_PollKeyboardInputEvents
     ====================
     */
    fun Sys_PollKeyboardInputEvents(): Int {
        return -1
    }

    fun Sys_ReturnKeyboardInputEvent(n: Int, ch: CInt, state: CBool): Int {
        return 0
    }

    /*
     ====================
     Sys_PollKeyboardInputEvents
     ====================
     */
    fun Sys_ReturnKeyboardInputEvent(ch: IntArray, action: Int, key: Int, scancode: Int, mods: Int): Int {
        ch[0] = IN_DIMapKey(key, scancode, mods)
        when (ch[0]) {
            KeyInput.K_BACKSPACE -> {
                // SE_KEY covers bindings and key-state tracking for all actions.
                win_main.Sys_QueEvent(
                    Instant.now().toEpochMilli(), sysEventType_t.SE_KEY, ch[0], action, 0, null
                )
                // SE_CHAR on press/repeat drives the console/UI delete-character logic.
                if (action != GLFW.GLFW_RELEASE) {
                    win_main.Sys_QueEvent(
                        Instant.now().toEpochMilli(), sysEventType_t.SE_CHAR, ch[0], action, 0, null
                    )
                }
            }

            KeyInput.K_PRINT_SCR -> {
                if (action != GLFW.GLFW_RELEASE) {
                    // don't queue printscreen keys.  Since windows doesn't send us key
                    // down events for this, we handle queueing them with DirectInput
                    win_main.Sys_QueEvent(
                        GetTickCount(), sysEventType_t.SE_KEY, ch[0], action, 0, null
                    ) //TODO:enable this
                }
                // for windows, add a keydown event for print screen here, since
                // windows doesn't send keydown events to the WndProc for this key.
                // ctrl and alt are handled here to get around windows sending ctrl and
                // alt messages when the right-alt is pressed on non-US 102 keyboards.
            }

            KeyInput.K_CTRL, KeyInput.K_ALT, KeyInput.K_RIGHT_ALT -> win_main.Sys_QueEvent(
                GetTickCount(), sysEventType_t.SE_KEY, ch[0], action, 0, null
            )

            else -> {
                // Always queue SE_KEY so binding execution and key-state tracking
                // (keys[n].down) work correctly for every key and every action.
                win_main.Sys_QueEvent(Instant.now().toEpochMilli(), sysEventType_t.SE_KEY, ch[0], action, 0, null)
                // Additionally queue SE_CHAR for printable ASCII on press/repeat so
                // the console and UI text-fields receive the typed characters.
                // Console-toggle keys (grave / tilde) are intentionally excluded —
                // they open/close the console via SE_KEY and must not echo as text.
                if (action != GLFW.GLFW_RELEASE
                    && ch[0] in 32..126
                    && ch[0] != '`'.code
                    && ch[0] != '~'.code
                ) {
                    // Compute the actual typed character including shift/caps state.
                    // ch[0] is always lowercase (Doom keynum), but SE_CHAR needs the
                    // real character the user intended to type.
                    val typedChar = getTypedChar(key, mods)
                    if (typedChar != 0) {
                        win_main.Sys_QueEvent(
                            Instant.now().toEpochMilli(),
                            sysEventType_t.SE_CHAR,
                            typedChar,
                            0,
                            0,
                            null
                        )
                    }
                }
            }
        }
        return ch[0]
    }

    private fun GetTickCount(): Long {
        return Instant.now().toEpochMilli() - START_TIME
    }

    fun Sys_EndKeyboardInputEvents() {}
    fun Sys_QueMouseEvents(dwElements: Int) {
    }

    fun Sys_EndMouseInputEvents() {}

    fun Sys_MapCharForKey(key: Int): Char {
        return (key and 0xFF).toChar()
    }
}
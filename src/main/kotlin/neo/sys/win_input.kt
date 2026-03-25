/*
 * Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
 * Translated to Kotlin by Dr. Feederino with support of Claude Code
 *
 * This file is part of the Doom 3 Kotlin project.
 * Original source: neo/sys/win32/win_input.cpp, neo/sys/events.cpp
 *
 * NOTE: Differs from C++ — Uses LWJGL/GLFW callbacks for input instead of
 * DirectInput (Win32) or SDL. Input callbacks are set up in win_glimp.kt.
 *
 * Doom 3 Source Code is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package neo.sys

import neo.TempDump.TODO_Exception
import neo.framework.Common
import neo.framework.KeyInput
import neo.framework.MACOS_X
import neo.idlib.Text.Str.idStr
import neo.idlib.containers.CBool
import neo.idlib.containers.CInt
import neo.idlib.idLib
import neo.sys.sys_public.sysEventType_t
import neo.sys.win_local.Win32Vars_t
import org.lwjgl.glfw.GLFW
import java.awt.event.InputEvent
import java.time.Instant

object win_input {
    const val CHAR_FIRSTREPEAT = 200
    const val CHAR_REPEAT = 100
    const val DINPUT_BUFFERSIZE = 256

    // class MYDATA {
    // long  lX;                   // X axis goes here
    // long  lY;                   // Y axis goes here
    // long  lZ;                   // Z axis goes here
    // byte  bButtonA;             // One button goes here
    // byte  bButtonB;             // Another button goes here
    // byte  bButtonC;             // Another button goes here
    // byte  bButtonD;             // Another button goes here
    // } ;
    // static DIOBJECTDATAFORMAT rgodf[] = {
    // { &GUID_XAxis,    FIELD_OFFSET(MYDATA, lX),       DIDFT_AXIS | DIDFT_ANYINSTANCE,   0,},
    // { &GUID_YAxis,    FIELD_OFFSET(MYDATA, lY),       DIDFT_AXIS | DIDFT_ANYINSTANCE,   0,},
    // { &GUID_ZAxis,    FIELD_OFFSET(MYDATA, lZ),       0x80000000 | DIDFT_AXIS | DIDFT_ANYINSTANCE,   0,},
    // { 0,              FIELD_OFFSET(MYDATA, bButtonA), DIDFT_BUTTON | DIDFT_ANYINSTANCE, 0,},
    // { 0,              FIELD_OFFSET(MYDATA, bButtonB), DIDFT_BUTTON | DIDFT_ANYINSTANCE, 0,},
    // { 0,              FIELD_OFFSET(MYDATA, bButtonC), 0x80000000 | DIDFT_BUTTON | DIDFT_ANYINSTANCE, 0,},
    // { 0,              FIELD_OFFSET(MYDATA, bButtonD), 0x80000000 | DIDFT_BUTTON | DIDFT_ANYINSTANCE, 0,},
    // };
    //==========================================================================
    val   /*DIDEVICEOBJECTDATA*/polled_didod: Array<InputEvent?> =
        arrayOfNulls<InputEvent?>(DINPUT_BUFFERSIZE) // Receives buffered data
    val s_scantokey /*[256]*/: CharArray =
        charArrayOf( //  0            1       2          3          4       5            6         7
            //  8            9       A          B          C       D            E         F
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
    val s_scantokey_french /*[256]*/: CharArray =
        charArrayOf( //  0            1       2          3          4       5            6         7
            //  8            9       A          B          C       D            E         F
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
            0 // 7
                .toChar()
        )
    val s_scantokey_german /*[256]*/: CharArray =
        charArrayOf( //  0            1       2          3          4       5            6         7
            //  8            9       A          B          C       D            E         F
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
            0 // 7
                .toChar()
        )
    val s_scantokey_italian /*[256]*/: CharArray =
        charArrayOf( //  0            1       2          3          4       5            6         7
            //  8            9       A          B          C       D            E         F
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
            0 // 7
                .toChar()
        )
    val s_scantokey_spanish /*[256]*/: CharArray =
        charArrayOf( //  0            1       2          3          4       5            6         7
            //  8            9       A          B          C       D            E         F
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
            0 // 7
                .toChar()
        )
    private val START_TIME = Instant.now().toEpochMilli()
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
    private const val B1 = false

    /*
     ============================================================

     DIRECT INPUT KEYBOARD CONTROL

     ============================================================
     */
    fun IN_StartupKeyboard(): Boolean {

//        try {
//            Keyboard.create();
//
//            if (Keyboard.isCreated()) {
//                common.Printf("keyboard: DirectInput initialized.\n");
//                return true;
//            }
//        } catch (LWJGLException ex) {
//            common.Printf("keyboard: couldn't find a keyboard device\n");
//        }
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
                GLFW.GLFW_KEY_RIGHT_ALT -> rightAltKey
                GLFW.GLFW_KEY_RIGHT_CONTROL -> KeyInput.K_CTRL
                GLFW.GLFW_KEY_KP_ENTER -> KeyInput.K_KP_ENTER
                GLFW.GLFW_KEY_KP_EQUAL -> KeyInput.K_KP_EQUALS
                GLFW.GLFW_KEY_PAUSE -> KeyInput.K_PAUSE
                GLFW.GLFW_KEY_KP_DIVIDE -> KeyInput.K_KP_SLASH
                GLFW.GLFW_KEY_LEFT_SUPER -> KeyInput.K_LWIN
                GLFW.GLFW_KEY_RIGHT_SUPER -> KeyInput.K_RWIN
                GLFW.GLFW_KEY_MENU -> KeyInput.K_MENU
                GLFW.GLFW_KEY_PRINT_SCREEN -> KeyInput.K_PRINT_SCR
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

        // For standard keys (< 256), fall back to thy OS-specific ASCII translations
        return if (MACOS_X) {
            Char(key).lowercaseChar().code //a small hack to make controls work
        } else if (scancode > 256) 0 else keyScanTable[getShiftedScancode(key, scancode, mods)].code
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

    /*
     ==========================
     IN_DeactivateKeyboard
     ==========================
     */
    fun IN_DeactivateKeyboard() {
//        if (Keyboard.isCreated()) {
//            Keyboard.destroy();
//        }
    }

    /*
     ========================
     IN_InitDirectInput
     ========================
     */
    fun IN_InitDirectInput() {
        throw TODO_Exception()
        //    HRESULT		hr;
//
//	common->Printf( "Initializing DirectInput...\n" );
//
//	if ( win32.g_pdi != NULL ) {
//		win32.g_pdi->Release();			// if the previous window was destroyed we need to do this
//		win32.g_pdi = NULL;
//	}
//
//    // Register with the DirectInput subsystem and get a pointer
//    // to a IDirectInput interface we can use.
//    // Create the base DirectInput object
//	if ( FAILED( hr = DirectInput8Create( GetModuleHandle(NULL), DIRECTINPUT_VERSION, IID_IDirectInput8, (void**)&win32.g_pdi, NULL ) ) ) {
//		common->Printf ("DirectInputCreate failed\n");
//    }
    }

    /*
     ========================
     IN_InitDIMouse
     ========================
     */
    fun IN_InitDIMouse(): Boolean {
//        try {
//            Mouse.create();
//            Mouse.setClipMouseCoordinatesToWindow(true);
//            Mouse.setCursorPosition(Display.getWidth() / 2, Display.getHeight() / 2);
//
//            if (Mouse.isCreated()) {
//                common.Printf("mouse: DirectInput initialized.\n");
//                return true;
//            }
//        } catch (LWJGLException ex) {//TODO:expand this.
//            common.Printf("mouse: Couldn't open DI mouse device\n");
//        }
        return false
    }

    /*
     ==========================
     IN_ActivateMouse
     ==========================
     */
    fun IN_ActivateMouse() {
        throw TODO_Exception()
        //	int i;
//	HRESULT hr;
//
//	if ( !win32.in_mouse.GetBool() || win32.mouseGrabbed || !win32.g_pMouse ) {
//		return;
//	}
//
//	win32.mouseGrabbed = true;
//	for ( i = 0; i < 10; i++ ) {
//		if ( ::ShowCursor( false ) < 0 ) {
//			break;
//		}
//	}
//
//	// we may fail to reacquire if the window has been recreated
//	hr = win32.g_pMouse->Acquire();
//	if (FAILED(hr)) {
//		return;
//	}
//
//	// set the cooperativity level.
//	hr = win32.g_pMouse->SetCooperativeLevel( win32.hWnd, DISCL_EXCLUSIVE | DISCL_FOREGROUND);
    }

    /*
     ==========================
     IN_DeactivateMouse
     ==========================
     */
    fun IN_DeactivateMouse() {
//        if (Mouse.isCreated()) {
//            Mouse.destroy();
//        }
    }

    /*
     ==========================
     IN_DeactivateMouseIfWindowed
     ==========================
     */
    fun IN_DeactivateMouseIfWindowed() {
        throw TODO_Exception()
        //	if ( !win32.cdsFullscreen ) {
//		IN_DeactivateMouse();
//	}
    }

    /*
     ============================================================

     MOUSE CONTROL

     ============================================================
     *//*
     ===========
     Sys_ShutdownInput
     ===========
     */

    fun Sys_ShutdownInput() {
        IN_DeactivateMouse()
        IN_DeactivateKeyboard()
        if (win_local.win32.g_pKeyboard != null) {
//		win32.g_pKeyboard->Release();
            win_local.win32.g_pKeyboard = null
        }
        if (win_local.win32.g_pMouse != null) {
//		win32.g_pMouse->Release();
            win_local.win32.g_pMouse = null
        }

//    if ( win32.g_pdi ) {//TODO:not entirely sure what this is, yet!
//		win32.g_pdi->Release();
//		win32.g_pdi = NULL;
//	}
    }

    /*
     ===========
     Sys_InitInput
     ===========
     */

    fun Sys_InitInput() {
        Common.common.Printf("\n------- Input Initialization -------\n")
        //        IN_InitDirectInput();
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

    //=====================================================================================
    //#if 1
    // I tried doing the full-state get to address a keyboard problem on one system,
    // but it didn't make any difference
    /*
     ==================
     IN_Frame

     Called every frame, even if not generating commands
     ==================
     */
    fun IN_Frame() {
        throw TODO_Exception()
        //	bool	shouldGrab = true;
//
//	if ( !win32.in_mouse.GetBool() ) {
//		shouldGrab = false;
//	}
//	// if fullscreen, we always want the mouse
//	if ( !win32.cdsFullscreen ) {
//		if ( win32.mouseReleased ) {
//			shouldGrab = false;
//		}
//		if ( win32.movingWindow ) {
//			shouldGrab = false;
//		}
//		if ( !win32.activeApp ) {
//			shouldGrab = false;
//		}
//	}
//
//	if ( shouldGrab != win32.mouseGrabbed ) {
//		if ( win32.mouseGrabbed ) {
//			IN_DeactivateMouse();
//		} else {
//			IN_ActivateMouse();
//
//#if 0	// if we can't reacquire, try reinitializing
//			if ( !IN_InitDIMouse() ) {
//				win32.in_mouse.SetBool( false );
//				return;
//			}
//#endif
//		}
//	}
    }


    fun Sys_GrabMouseCursor(grabIt: Boolean) {
//        if (Mouse.isGrabbed() == grabIt) {//otherwise resetMouse in setGrabbed will keep erasing our mouse data.
//            Mouse.setGrabbed(grabIt);
//        }
//#ifndef	ID_DEDICATED
//	win32.mouseReleased = !grabIt;
//	if ( !grabIt ) {
//		// release it right now
//		IN_Frame();
//	}
//#endif
    }

    /*
     ====================
     Sys_PollKeyboardInputEvents
     ====================
     */
    fun Sys_PollKeyboardInputEvents(): Int {
//        return Keyboard.getNumKeyboardEvents();
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
                val character = Char(ch[0])
                if (action == GLFW.GLFW_RELEASE && (character in '0'..'9' || character == Char(127)) && ch[0] != '~'.code && ch[0] != '`'.code && ch[0] < 128) {
                    win_main.Sys_QueEvent(
                        Instant.now().toEpochMilli(), sysEventType_t.SE_CHAR, ch[0], action, 0, null
                    )
                } else {
                    win_main.Sys_QueEvent(Instant.now().toEpochMilli(), sysEventType_t.SE_KEY, ch[0], action, 0, null)
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
        throw TODO_Exception()
        //	int i, value;
//
//	for( i = 0; i < dwElements; i++ ) {
//		if ( polled_didod[i].dwOfs >= DIMOFS_BUTTON0 && polled_didod[i].dwOfs <= DIMOFS_BUTTON7 ) {
//			value = (polled_didod[i].dwData & 0x80) == 0x80;
//			Sys_QueEvent( polled_didod[i].dwTimeStamp, SE_KEY, K_MOUSE1 + ( polled_didod[i].dwOfs - DIMOFS_BUTTON0 ), value, 0, NULL );
//		} else {
//			switch (polled_didod[i].dwOfs) {
//			case DIMOFS_X:
//				value = polled_didod[i].dwData;
//				Sys_QueEvent( polled_didod[i].dwTimeStamp, SE_MOUSE, value, 0, 0, null );
//				break;
//			case DIMOFS_Y:
//				value = polled_didod[i].dwData;
//				Sys_QueEvent( polled_didod[i].dwTimeStamp, SE_MOUSE, 0, value, 0, null );
//				break;
//			case DIMOFS_Z:
//				value = ( (int) polled_didod[i].dwData ) / WHEEL_DELTA;
//				int key = value < 0 ? K_MWHEELDOWN : K_MWHEELUP;
//				value = abs( value );
//				while( value-- > 0 ) {
//					Sys_QueEvent( polled_didod[i].dwTimeStamp, SE_KEY, key, true, 0, null );
//					Sys_QueEvent( polled_didod[i].dwTimeStamp, SE_KEY, key, false, 0, null );
//				}
//				break;
//			}
//		}
//	}
    }

    //=====================================================================================
    fun Sys_PollMouseInputEvents(): Int {
        throw TODO_Exception()
        //        DWORD				dwElements;
//	HRESULT				hr;
//
//	if ( !Mouse.isCreated() || !Mouse.isGrabbed() ) {
////	if ( !win32.g_pMouse || !win32.mouseGrabbed ) {
//		return 0;
//	}
//
//    dwElements = DINPUT_BUFFERSIZE;
//    hr = win32.g_pMouse.GetDeviceData( sizeof(DIDEVICEOBJECTDATA), polled_didod, &dwElements, 0 );
//
//    if( hr != DI_OK ) {
//        hr = win32.g_pMouse.Acquire();
//		// clear the garbage
//		if (!FAILED(hr)) {
//			win32.g_pMouse.GetDeviceData( sizeof(DIDEVICEOBJECTDATA), polled_didod, &dwElements, 0 );
//		}
//    }
//
//    if( FAILED(hr) ) {
//        return 0;
//	}
//
//	Sys_QueMouseEvents( dwElements );
//
//	return dwElements;
    }

    fun Sys_ReturnMouseInputEvent(n: Int, action: CInt, value: CInt) {}

    @Deprecated("")
    fun Sys_ReturnMouseInputEvent(action: IntArray, value: IntArray) {

//        final long dwTimeStamp = Mouse.getEventNanoseconds();
//
//        while (Mouse.next()) {
//            final int x, y, w;
//            if ((x = Mouse.getDX()) != 0) {
//                value[0] = x;
//                action[0] = etoi(M_DELTAX);
//                Sys_QueEvent(dwTimeStamp, SE_MOUSE, value[0], 0, 0, null);
//            }
//            if ((y = Mouse.getDY()) != 0) {
//                value[0] = -y;//TODO:negative a la ogl?
//                action[0] = etoi(M_DELTAY);
//                Sys_QueEvent(dwTimeStamp, SE_MOUSE, 0, value[0], 0, null);
//            }
//            if ((w = Mouse.getDWheel()) != 0) {
//                // mouse wheel actions are impulses, without a specific up / down
//                int wheelValue = value[0] = w;//(int) polled_didod[n].dwData ) / WHEEL_DELTA;
//                final int key = value[0] < 0 ? K_MWHEELDOWN : K_MWHEELUP;
//                action[0] = etoi(M_DELTAZ);
//
//                while (wheelValue-- > 0) {
//                    Sys_QueEvent(dwTimeStamp, SE_KEY, key, btoi(true), 0, null);
//                    Sys_QueEvent(dwTimeStamp, SE_KEY, key, btoi(false), 0, null);
//                }
//            }
//            if (Mouse.getEventButtonState()) {//TODO:find out what Mouse.next() does exactly.
//                final int diaction = Mouse.getEventButton();
//                value[0] = Mouse.isButtonDown(diaction) ? 0x80 : 0;// (polled_didod[n].dwData & 0x80) == 0x80;
//                action[0] = etoi(M_ACTION1) + diaction;//- DIMOFS_BUTTON0 );
//                Sys_QueEvent(dwTimeStamp, SE_KEY, K_MOUSE1 + diaction, value[0], 0, null);
//                B1 = true;
//            } else if (B1) {
//                Sys_QueEvent(dwTimeStamp, SE_KEY, K_MOUSE1, value[0] = 0, 0, null);
//                B1 = false;
//            }
//        }
    }

    fun Sys_EndMouseInputEvents() {}
    fun Sys_MapCharForKey(key: Int): Char {
        return (key and 0xFF).toChar()
    }
}
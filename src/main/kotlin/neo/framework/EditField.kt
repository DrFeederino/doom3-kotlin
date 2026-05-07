/*
 * Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
 * Translated to Kotlin by Dr. Feederino with support of Claude Code
 *
 * This file is part of the Doom 3 Kotlin project.
 * Original source: neo/framework/EditField.cpp, neo/framework/EditField.h
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
package neo.framework

import neo.Renderer.Material
import neo.Renderer.RenderSystem
import neo.framework.KeyInput.idKeyInput
import neo.idlib.CmdArgs
import neo.idlib.Text.Str
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.ctos
import neo.idlib.Text.strLen
import neo.idlib.colorWhite
import neo.idlib.idException
import neo.sys.win_main

object EditField {
    /*
     ===============================================================================

     Edit field

     ===============================================================================
     */
    const val MAX_EDIT_LINE = 256
    var globalAutoComplete: autoComplete_s = autoComplete_s()

    class autoComplete_s {
        var completionString: CharArray = CharArray(MAX_EDIT_LINE)
        var currentMatch: CharArray = CharArray(MAX_EDIT_LINE)
        var findMatchIndex = 0
        var length = 0
        var matchCount = 0
        var matchIndex = 0
        var valid = false
    } /*autoComplete_t*/

    class idEditField {
        private var autoComplete: autoComplete_s = autoComplete_s()
        private val buffer: CharArray = CharArray(MAX_EDIT_LINE)
        private var cursor = 0
        private var scroll = 0
        private var widthInChars = 0

        /*
         ===============
         idEditField::Clear
         ===============
         */
        fun Clear() {
            buffer[0] = Char(0)
            cursor = 0
            scroll = 0
            autoComplete.length = 0
            autoComplete.valid = false
        }

        fun SetWidthInChars(w: Int) {
            assert(w <= MAX_EDIT_LINE)
            widthInChars = w
        }

        fun SetCursor(c: Int) {
            assert(c <= MAX_EDIT_LINE)
            cursor = c
        }

        fun GetCursor(): Int {
            return cursor
        }

        fun ClearAutoComplete() {
            if (autoComplete.length > 0 && autoComplete.length <= strLen(buffer)) {
                buffer[autoComplete.length] = '\u0000'
                if (cursor > autoComplete.length) {
                    cursor = autoComplete.length
                }
            }
            autoComplete.length = 0
            autoComplete.valid = false
        }

        fun GetAutoCompleteLength(): Int {
            return autoComplete.length
        }

        @Throws(idException::class)
        fun AutoComplete() {
            val completionArgString = CharArray(MAX_EDIT_LINE)
            val args = CmdArgs.idCmdArgs()
            val findMatches = FindMatches
            val findIndexMatch = FindIndexMatch
            val printMatches = PrintMatches
            if (!autoComplete.valid) {
                args.TokenizeString(ctos(buffer), false)
                idStr.Copynz(autoComplete.completionString, args.Argv(0), autoComplete.completionString.size)
                idStr.Copynz(completionArgString, args.Args(), completionArgString.size)
                autoComplete.matchCount = 0
                autoComplete.matchIndex = 0
                autoComplete.currentMatch[0] = Char(0)
                if (strLen(autoComplete.completionString) == 0) {
                    return
                }
                globalAutoComplete = autoComplete
                CmdSystem.cmdSystem.CommandCompletion(findMatches)
                CVarSystem.cvarSystem.CommandCompletion(findMatches)
                autoComplete = globalAutoComplete
                if (autoComplete.matchCount == 0) {
                    return  // no matches
                }

                // when there's only one match or there's an argument
                if (autoComplete.matchCount == 1 || completionArgString[0] != '\u0000') {

                    /// try completing arguments
                    idStr.Append(autoComplete.completionString, autoComplete.completionString.size, " ")
                    idStr.Append(
                        autoComplete.completionString,
                        autoComplete.completionString.size,
                        ctos(completionArgString)
                    )
                    autoComplete.matchCount = 0
                    globalAutoComplete = autoComplete
                    CmdSystem.cmdSystem.ArgCompletion(ctos(autoComplete.completionString), findMatches)
                    CVarSystem.cvarSystem.ArgCompletion(ctos(autoComplete.completionString), findMatches)
                    autoComplete = globalAutoComplete
                    idStr.snPrintf(buffer, buffer.size, "%s", ctos(autoComplete.currentMatch))
                    if (autoComplete.matchCount == 0) {
                        // no argument matches
                        idStr.Append(buffer, buffer.size, " ")
                        idStr.Append(buffer, buffer.size, ctos(completionArgString))
                        SetCursor(strLen(buffer))
                        return
                    }
                } else {

                    // multiple matches, complete to shortest
                    idStr.snPrintf(buffer, buffer.size, "%s", ctos(autoComplete.currentMatch))
                    if (strLen(completionArgString) != 0) {
                        idStr.Append(buffer, buffer.size, " ")
                        idStr.Append(buffer, buffer.size, ctos(completionArgString))
                    }
                }
                autoComplete.length = strLen(buffer)
                autoComplete.valid = autoComplete.matchCount != 1
                SetCursor(autoComplete.length)
                Common.common.Printf("]%s\n", ctos(buffer))

                // run through again, printing matches
                globalAutoComplete = autoComplete
                CmdSystem.cmdSystem.CommandCompletion(printMatches)
                CmdSystem.cmdSystem.ArgCompletion(ctos(autoComplete.completionString), printMatches)
                CVarSystem.cvarSystem.CommandCompletion(PrintCvarMatches)
                CVarSystem.cvarSystem.ArgCompletion(ctos(autoComplete.completionString), printMatches)
            } else if (autoComplete.matchCount != 1) {

                // get the next match and show instead
                autoComplete.matchIndex++
                if (autoComplete.matchIndex == autoComplete.matchCount) {
                    autoComplete.matchIndex = 0
                }
                autoComplete.findMatchIndex = 0
                globalAutoComplete = autoComplete
                CmdSystem.cmdSystem.CommandCompletion(findIndexMatch)
                CmdSystem.cmdSystem.ArgCompletion(ctos(autoComplete.completionString), findIndexMatch)
                CVarSystem.cvarSystem.CommandCompletion(findIndexMatch)
                CVarSystem.cvarSystem.ArgCompletion(ctos(autoComplete.completionString), findIndexMatch)
                autoComplete = globalAutoComplete

                // and print it
                idStr.snPrintf(buffer, buffer.size, "%s", ctos(autoComplete.currentMatch))
                if (autoComplete.length > strLen(buffer)) {
                    autoComplete.length = strLen(buffer)
                }
                SetCursor(autoComplete.length)
            }
        }

        fun CharEvent(ch: Int) {
            val len: Int
            if (ch == 'v' - 'a' + 1) {    // ctrl-v is paste
                Paste()
                return
            }
            if (ch == 'c' - 'a' + 1) {    // ctrl-c clears the field
                Clear()
                return
            }
            len = strLen(buffer)
            if (ch == 'h' - 'a' + 1 || ch == KeyInput.K_BACKSPACE) {    // ctrl-h is backspace
                if (cursor > 0) {
//			memmove( buffer + cursor - 1, buffer + cursor, len + 1 - cursor );
                    System.arraycopy(buffer, cursor, buffer, cursor - 1, len + 1 - cursor)
                    cursor--
                    if (cursor < scroll) {
                        scroll--
                    }
                }
                return
            }
            if (ch == 'a' - 'a' + 1) {    // ctrl-a is home
                cursor = 0
                scroll = 0
                return
            }
            if (ch == 'e' - 'a' + 1) {    // ctrl-e is end
                cursor = len
                scroll = cursor - widthInChars
                return
            }

            //
            // ignore any other non printable chars
            //
            if (ch < 32) {
                return
            }
            if (idKeyInput.GetOverstrikeMode()) {
                if (cursor == MAX_EDIT_LINE - 1) {
                    return
                }
                buffer[cursor] = ch.toChar()
                cursor++
            } else {    // insert mode
                if (len == MAX_EDIT_LINE - 1) {
                    return  // all full
                }
                //		memmove( buffer + cursor + 1, buffer + cursor, len + 1 - cursor );
                System.arraycopy(buffer, cursor, buffer, cursor + 1, len + 1 - cursor)
                buffer[cursor] = ch.toChar()
                cursor++
            }
            if (cursor >= widthInChars) {
                scroll++
            }
            if (cursor == len + 1) {
                buffer[cursor] = Char(0)
            }
        }

        fun KeyDownEvent(key: Int) {
            val len: Int

            // shift-insert is paste
            if ((key == KeyInput.K_INS || key == KeyInput.K_KP_INS) && idKeyInput.IsDown(KeyInput.K_SHIFT)) {
                ClearAutoComplete()
                Paste()
                return
            }
            len = strLen(buffer)
            if (key == KeyInput.K_DEL) {
                if (autoComplete.length != 0) {
                    ClearAutoComplete()
                } else if (cursor < len) {
//			memmove( buffer + cursor, buffer + cursor + 1, len - cursor );
                    System.arraycopy(buffer, cursor + 1, buffer, cursor, len - cursor)
                }
                return
            }
            if (key == KeyInput.K_RIGHTARROW) {
                if (idKeyInput.IsDown(KeyInput.K_CTRL)) {
                    // skip to next word
                    while (cursor < len && buffer[cursor] != ' ') {
                        cursor++
                    }
                    while (cursor < len && buffer[cursor] == ' ') {
                        cursor++
                    }
                } else {
                    cursor++
                }
                if (cursor > len) {
                    cursor = len
                }
                if (cursor >= scroll + widthInChars) {
                    scroll = cursor - widthInChars + 1
                }
                if (autoComplete.length > 0) {
                    autoComplete.length = cursor
                }
                return
            }
            if (key == KeyInput.K_LEFTARROW) {
                if (idKeyInput.IsDown(KeyInput.K_CTRL)) {
                    // skip to previous word
                    while (cursor > 0 && buffer[cursor - 1] == ' ') {
                        cursor--
                    }
                    while (cursor > 0 && buffer[cursor - 1] != ' ') {
                        cursor--
                    }
                } else {
                    cursor--
                }
                if (cursor < 0) {
                    cursor = 0
                }
                if (cursor < scroll) {
                    scroll = cursor
                }
                if (autoComplete.length != 0) {
                    autoComplete.length = cursor
                }
                return
            }
            if (key == KeyInput.K_HOME || Char(key).lowercaseChar() == 'a' && idKeyInput.IsDown(KeyInput.K_CTRL)) {
                cursor = 0
                scroll = 0
                if (autoComplete.length != 0) {
                    autoComplete.length = cursor
                    autoComplete.valid = false
                }
                return
            }
            if (key == KeyInput.K_END || Char(key).lowercaseChar() == 'e' && idKeyInput.IsDown(KeyInput.K_CTRL)) {
                cursor = len
                if (cursor >= scroll + widthInChars) {
                    scroll = cursor - widthInChars + 1
                }
                if (autoComplete.length != 0) {
                    autoComplete.length = cursor
                    autoComplete.valid = false
                }
                return
            }
            if (key == KeyInput.K_INS) {
                idKeyInput.SetOverstrikeMode(!idKeyInput.GetOverstrikeMode())
                return
            }

            // clear autocompletion buffer on normal key input
            if (key != KeyInput.K_CAPSLOCK && key != KeyInput.K_ALT && key != KeyInput.K_CTRL && key != KeyInput.K_SHIFT
                && key != KeyInput.K_RIGHT_CTRL && key != KeyInput.K_RIGHT_SHIFT
            ) {
                ClearAutoComplete()
            }
        }

        fun Paste() {
            val cbd: String?
            val pasteLen: Int
            var i: Int
            cbd = win_main.Sys_GetClipboardData()
            if (null == cbd) {
                return
            }

            // send as if typed, so insert / overstrike works properly
            pasteLen = cbd.length
            i = 0
            while (i < pasteLen) {
                CharEvent(cbd[i].code)
                i++
            }
        }

        fun GetBuffer(): CharArray {
            return buffer
        }

        @Throws(idException::class)
        fun Draw(x: Int, y: Int, width: Int, showCursor: Boolean, shader: Material.idMaterial?) {
            val len: Int
            var drawLen: Int
            var prestep: Int
            val cursorChar: Int
            val str = CharArray(MAX_EDIT_LINE)
            val size: Int
            size = RenderSystem.SMALLCHAR_WIDTH
            drawLen = widthInChars
            len = strLen(buffer) + 1

            // guarantee that cursor will be visible
            if (len <= drawLen) {
                prestep = 0
            } else {
                if (scroll + drawLen > len) {
                    scroll = len - drawLen
                    if (scroll < 0) {
                        scroll = 0
                    }
                }
                prestep = scroll

                // Skip color code
                if (idStr.IsColor(ctos(buffer).substring(prestep))) {
                    prestep += 2
                }
                if (prestep > 0 && idStr.IsColor(ctos(buffer).substring(prestep - 1))) {
                    prestep++
                }
            }
            if (prestep + drawLen > len) {
                drawLen = len - prestep
            }

            // extract <drawLen> characters from the field at <prestep>
            if (drawLen >= MAX_EDIT_LINE) {
                Common.common.Error("drawLen >= MAX_EDIT_LINE")
            }

//	memcpy( str, buffer + prestep, drawLen );
            System.arraycopy(buffer, prestep, str, 0, drawLen)
            str[drawLen] = Char(0)

            // draw it
            RenderSystem.renderSystem.DrawSmallStringExt(x, y, str, colorWhite, false, shader)

            // draw the cursor
            if (!showCursor) {
                return
            }
            if (Common.com_ticNumber shr 4 and 1 == 1) {
                return  // off blink
            }
            cursorChar = if (idKeyInput.GetOverstrikeMode()) {
                11
            } else {
                10
            }

            // Move the cursor back to account for color codes
            val strString = ctos(str)
            var i = 0
            while (i < cursor) {
                if (i < strString.length - 1 && idStr.IsColor(strString.substring(i))) {
                    i++
                    prestep += 2
                }
                i++
            }
            RenderSystem.renderSystem.DrawSmallChar(x + (cursor - prestep) * size, y, cursorChar, shader)
        }

        fun SetBuffer(buf: String) {
            Clear()
            idStr.Copynz(buffer, buf, buffer.size)
            SetCursor(strLen(buffer))
        }

        //
        //
        init {
            Clear()
        }
    }

    /*
     ===============
     FindMatches
     ===============
     */
    internal val FindMatches: (String) -> Unit = { s ->
        if (idStr.Icmpn(
                s,
                ctos(globalAutoComplete.completionString),
                strLen(globalAutoComplete.completionString)
            ) == 0
        ) {
            globalAutoComplete.matchCount++
            if (globalAutoComplete.matchCount == 1) {
                idStr.Copynz(
                    globalAutoComplete.currentMatch,
                    s,
                    globalAutoComplete.currentMatch.size
                )
            } else {
                // cut currentMatch to the amount common with s
                var i = 0
                while (i < s.length) {
                    if (globalAutoComplete.currentMatch[i].lowercaseChar() != s[i].lowercaseChar()) {
                        globalAutoComplete.currentMatch[i] = Char(0)
                        break
                    }
                    i++
                }
                globalAutoComplete.currentMatch[i] = Char(0)
            }
        }
    }

    /*
     ===============
     FindIndexMatch
     ===============
     */
    internal val FindIndexMatch: (String) -> Unit = { s ->
        val completionStr = ctos(globalAutoComplete.completionString)
        if (idStr.Icmpn(s, completionStr, completionStr.length) == 0) {
            if (globalAutoComplete.findMatchIndex == globalAutoComplete.matchIndex) {
                idStr.Copynz(
                    globalAutoComplete.currentMatch,
                    s,
                    globalAutoComplete.currentMatch.size
                )
            }
            globalAutoComplete.findMatchIndex++
        }
    }

    /*
     ===============
     PrintMatches
     ===============
     */
    internal val PrintMatches: (String) -> Unit = { s ->
        val currentMatch = ctos(globalAutoComplete.currentMatch)
        if (idStr.Icmpn(s, currentMatch, currentMatch.length) == 0) {
            Common.common.Printf("    %s\n", s)
        }
    }

    /*
     ===============
     PrintCvarMatches
     ===============
     */
    internal val PrintCvarMatches: (String) -> Unit = { s ->
        val currentMatch = ctos(globalAutoComplete.currentMatch)
        if (idStr.Icmpn(s, currentMatch, currentMatch.length) == 0) {
            Common.common.Printf(
                """    %s${Str.S_COLOR_WHITE} = "%s"
""", s, CVarSystem.cvarSystem.GetCVarString(s)
            )
        }
    }
}
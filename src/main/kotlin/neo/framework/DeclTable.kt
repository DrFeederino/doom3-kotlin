/*
 * Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
 * Translated to Kotlin by Dr. Feederino with support of Claude Code
 *
 * This file is part of the Doom 3 Kotlin project.
 * Original source: neo/framework/DeclTable.h, neo/framework/DeclTable.cpp
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
 *
 * You should have received a copy of the GNU General Public License
 * along with Doom 3 Source Code. If not, see <http://www.gnu.org/licenses/>.
 */

package neo.framework

import neo.framework.DeclManager.idDecl
import neo.idlib.Text.Lexer.idLexer
import neo.idlib.Text.Token.idToken
import neo.idlib.containers.List
import neo.idlib.idException
import neo.idlib.math.idMath

class DeclTable {
    /*
     ===============================================================================

     tables are used to map a floating point input value to a floating point
     output value, with optional wrap / clamp and interpolation

     ===============================================================================
     */
    class idDeclTable : idDecl() {
        private var clamp = false
        private var snap = false
        private val values: List.idFloatList = List.idFloatList()

        /*
         =================
         idDeclTable::Size
         =================
         */
        override fun Size(): Long {
            return values.Allocated().toLong()
        }

        /*
         =================
         idDeclTable::DefaultDefinition
         =================
         */
        override fun DefaultDefinition(): String {
            return "{ { 0 } }"
        }

        /*
         =================
         idDeclTable::Parse
         =================
         */
        @Throws(idException::class)
        override fun Parse(text: String, textLength: Int): Boolean {
            val src = idLexer()
            val token = idToken()
            var v: Float

            src.LoadMemory(text, textLength, GetFileName(), GetLineNum())
            src.SetFlags(DeclManager.DECL_LEXER_FLAGS)
            src.SkipUntilString("{")

            snap = false
            clamp = false
            values.Clear()

            while (true) {
                if (!src.ReadToken(token)) {
                    break
                }

                if (token.toString() == "}") {
                    break
                }

                if (token.Icmp("snap") == 0) {
                    snap = true
                } else if (token.Icmp("clamp") == 0) {
                    clamp = true
                } else if (token.Icmp("{") == 0) {

                    while (true) {
                        val errorFlag = BooleanArray(1)

                        v = src.ParseFloat(errorFlag)
                        if (errorFlag[0]) { // we got something non-numeric
                            MakeDefault()
                            return false
                        }

                        values.Append(v)

                        src.ReadToken(token)
                        if (token.toString() == "}") {
                            break
                        }
                        if (token.toString() == ",") {
                            continue
                        }
                        src.Warning("expected comma or brace")
                        MakeDefault()
                        return false
                    }

                } else {
                    src.Warning("unknown token '%s'", token.toString())
                    MakeDefault()
                    return false
                }
            }

            // copy the 0 element to the end, so lerping doesn't
            // need to worry about the wrap case
            val val0: Float = values[0] // template bug requires this to not be in the Append()?
            values.Append(val0)

            return true
        }

        /*
         =================
         idDeclTable::FreeData
         =================
         */
        override fun FreeData() {
            snap = false
            clamp = false
            values.Clear()
        }

        /*
         =================
         idDeclTable::TableLookup
         =================
         */
        fun TableLookup(index: Float): Float {
            var index = index
            var iIndex: Int
            val iFrac: Float

            val domain = values.Num() - 1

            if (domain <= 1) {
                return 1.0f
            }

            if (clamp) {
                index *= (domain - 1).toFloat()
                if (index >= domain - 1) {
                    return values[domain - 1]
                } else if (index <= 0) {
                    return values[0]
                }
                iIndex = idMath.Ftoi(index)
                iFrac = index - iIndex
            } else {
                index *= domain.toFloat()

                if (index < 0) {
                    index += domain * idMath.Ceil(-index / domain)
                }

                iIndex = idMath.FtoiFast(idMath.Floor(index))
                iFrac = index - iIndex
                iIndex = iIndex % domain
            }

            if (!snap) { // we duplicated the 0 index at the end at creation time, so we
                // don't need to worry about wrapping the filter
                return values[iIndex] * (1.0f - iFrac) + values[iIndex + 1] * iFrac
            }

            return values[iIndex]
        }
    }
}

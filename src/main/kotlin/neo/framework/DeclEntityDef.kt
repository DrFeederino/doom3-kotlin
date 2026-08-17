/*
 * Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
 * Translated to Kotlin by Dr. Feederino with support of Claude Code
 *
 * This file is part of the Doom 3 Kotlin project.
 * Original source: neo/framework/DeclEntityDef.h
 *                  neo/framework/DeclEntityDef.cpp
 *
 * Doom 3 GPL Source Code
 * This file is part of the Doom 3 GPL Source Code ("Doom 3 Source Code").
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

import neo.Game.Game_local
import neo.framework.DeclManager.declType_t
import neo.framework.DeclManager.idDecl
import neo.idlib.Dict_h.idDict
import neo.idlib.Dict_h.idKeyValue
import neo.idlib.Text.Lexer.idLexer
import neo.idlib.Text.Token
import neo.idlib.Text.Token.idToken
import neo.idlib.containers.List.idList
import neo.idlib.idException

class DeclEntityDef {
    /*
     ===============================================================================

     idDeclEntityDef

     ===============================================================================
     */
    class idDeclEntityDef : idDecl() {
        var dict: idDict = idDict()

        /*
         =================
         idDeclEntityDef::Size
         =================
         */ // NOTE: The C++ version overrides Size() to return sizeof(idDeclEntityDef) + dict.Allocated().
        // In the Kotlin architecture, idDecl does not expose Size() as an overridable method —
        // Size() is defined on idDeclBase and implemented by idDeclLocal, which does not delegate
        // to the idDecl subclass. This means dict.Allocated() is never included in the reported
        // size. This is a structural limitation of the Kotlin port's decl architecture.

        /*
         ================
         idDeclEntityDef::DefaultDefinition
         ================
         */
        override fun DefaultDefinition(): String {
            return "{\n\t\"DEFAULTED\"\t\"1\"\n}"
        }

        /*
         ================
         idDeclEntityDef::Parse
         ================
         */
        @Throws(idException::class)
        override fun Parse(text: String, textLength: Int): Boolean {
            val src = idLexer()
            val token = idToken()
            val token2 = idToken()
            src.LoadMemory(text, textLength, GetFileName(), GetLineNum())
            src.SetFlags(DeclManager.DECL_LEXER_FLAGS)
            src.SkipUntilString("{")
            while (true) {
                if (!src.ReadToken(token)) {
                    break
                }
                if (0 == token.Icmp("}")) {
                    break
                }
                if (token.type != Token.TT_STRING) {
                    src.Warning("Expected quoted string, but found '%s'", token.toString())
                    MakeDefault()
                    return false
                }
                if (!src.ReadToken(token2)) {
                    src.Warning("Unexpected end of file")
                    MakeDefault()
                    return false
                }
                if (dict.FindKey(token.toString()) != null) {
                    src.Warning("'%s' already defined", token.toString())
                }
                dict.Set(token, token2)
            }

            // we always automatically set a "classname" key to our name
            dict.Set("classname", GetName())

            // "inherit" keys will cause all values from another entityDef to be copied into this one
            // if they don't conflict.  We can't have circular recursions, because each entityDef will
            // never be parsed more than once

            // find all of the dicts first, because copying inherited values will modify the dict
            val defList = idList<idDeclEntityDef>()
            while (true) {
                val kv: idKeyValue? = dict.MatchPrefix("inherit", null)
                if (null == kv) {
                    break
                }
                val copy = DeclManager.declManager.FindType(
                    declType_t.DECL_ENTITYDEF, kv.GetValue(), false
                ) as idDeclEntityDef?
                if (null == copy) {
                    src.Warning("Unknown entityDef '%s' inherited by '%s'", kv.GetValue(), GetName())
                } else {
                    defList.Append(copy)
                }

                // delete this key/value pair
                dict.Delete(kv.GetKey().toString())
            }

            // now copy over the inherited key / value pairs
            for (i in 0 until defList.Num()) {
                dict.SetDefaults(defList[i].dict)
            }

            // precache all referenced media
            // do this as long as we arent in modview
            // DG: ... and only if we currently have a loaded/loading map
            if (0 == Common.com_editors and (Common.EDITOR_RADIANT or Common.EDITOR_AAS) && Session.session.GetCurrentMapName()
                    .isNotEmpty()
            ) {
                Game_local.game.CacheDictionaryMedia(dict)
            }
            return true
        }

        /*
         ================
         idDeclEntityDef::FreeData
         ================
         */
        override fun FreeData() {
            dict.Clear()
        }

        /*
         ================
         idDeclEntityDef::Print

         Dumps all key/value pairs, including inherited ones
         ================
         */
        @Throws(idException::class)
        override fun Print() {
            dict.Print()
        }
    }
}
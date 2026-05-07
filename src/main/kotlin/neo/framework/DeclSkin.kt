/*
===========================================================================

Doom 3 GPL Source Code
Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
Translated to Kotlin by Dr. Feederino with support of Claude Code

This file is part of the Doom 3 GPL Source Code ("Doom 3 Source Code").
Original source: neo/framework/DeclSkin.h, neo/framework/DeclSkin.cpp

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

In addition, the Doom 3 Source Code is also subject to certain additional terms.
You should have received a copy of these additional terms immediately following
the terms and conditions of the GNU General Public License which accompanied
the Doom 3 Source Code.  If not, please request a copy in writing from
id Software at the address below.

If you have questions concerning this license or the applicable additional terms,
you may contact in writing id Software LLC, c/o ZeniMax Media Inc., Suite 120,
Rockville, Maryland 20850 USA.

===========================================================================
*/
package neo.framework

import neo.Renderer.Material
import neo.framework.DeclManager.declType_t
import neo.framework.DeclManager.idDecl
import neo.idlib.Text.Lexer.idLexer
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Token.idToken
import neo.idlib.containers.List.idList
import neo.idlib.containers.idStrList
import neo.idlib.idException

class DeclSkin {
    /*
    ===============================================================================

        idDeclSkin

    ===============================================================================
    */

    internal class skinMapping_t {
        var from // 0 == any unmatched shader
                : Material.idMaterial? = null
        var to: Material.idMaterial? = null
    }

    class idDeclSkin : idDecl() {
        private val associatedModels: idStrList = idStrList()
        private val mappings: idList<skinMapping_t> = idList()

        /*
        ================
        idDeclSkin::SetDefaultText
        ================
        */
        @Throws(idException::class)
        override fun SetDefaultText(): Boolean {
            // if there exists a material with the same name
            return if (DeclManager.declManager.FindType(declType_t.DECL_MATERIAL, GetName(), false) != null) {
                val generated = StringBuffer(2048)
                // FIX: Was using trimIndent() raw string which produced an extra trailing newline
                idStr.snPrintf(
                    generated, generated.capacity(),
                    "skin %s // IMPLICITLY GENERATED\n{\n_default %s\n}\n",
                    GetName(), GetName()
                )
                SetText(generated.toString())
                true
            } else {
                false
            }
        }

        /*
        ================
        idDeclSkin::DefaultDefinition
        ================
        */
        override fun DefaultDefinition(): String {
            return """{
	"*"	"_default"
}"""
        }

        /*
        ================
        idDeclSkin::Parse
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
            associatedModels.clear()
            while (true) {
                if (!src.ReadToken(token)) {
                    break
                }
                if (0 == token.Icmp("}")) {
                    break
                }
                if (!src.ReadToken(token2)) {
                    src.Warning("Unexpected end of file")
                    MakeDefault()
                    return false
                }
                if (0 == token.Icmp("model")) {
                    associatedModels.add(token2.toString())
                    continue
                }
                val map = skinMapping_t()
                if (0 == token.Icmp("*")) {
                    // wildcard
                    map.from = null
                } else {
                    map.from = DeclManager.declManager.FindMaterial(token)
                }
                map.to = DeclManager.declManager.FindMaterial(token2)
                mappings.Append(map)
            }
            return false
        }

        /*
        ================
        idDeclSkin::FreeData
        ================
        */
        override fun FreeData() {
            mappings.Clear()
        }

        /*
        ===============
        idDeclSkin::RemapShaderBySkin
        ===============
        */
        fun RemapShaderBySkin(shader: Material.idMaterial?): Material.idMaterial? {
            if (null == shader) {
                return null
            }

            // never remap surfaces that were originally nodraw, like collision hulls
            if (!shader.IsDrawn()) {
                return shader
            }

            for (i in 0 until mappings.Num()) {
                val map = mappings[i]

                // NULL = wildcard match
                // FIX: Was using == (structural equality); C++ compares pointers (reference identity)
                if (map.from == null || map.from === shader) {
                    return map.to
                }
            }

            // didn't find a match or wildcard, so stay the same
            return shader
        }

        /*
        ================
        idDeclSkin::GetNumModelAssociations
        ================
        */
        // model associations are just for the preview dialog in the editor
        fun GetNumModelAssociations(): Int {
            return associatedModels.size()
        }

        /*
        ================
        idDeclSkin::GetAssociatedModel
        ================
        */
        fun GetAssociatedModel(index: Int): String {
            return if (index >= 0 && index < associatedModels.size()) {
                associatedModels[index].toString()
            } else ""
        }

        // NOTE: Kotlin-only — SERiAL interface for serialization (not in original C++)
    }
}

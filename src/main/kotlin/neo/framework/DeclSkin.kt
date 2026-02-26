package neo.framework

import neo.Renderer.Material
import neo.TempDump.SERiAL
import neo.framework.DeclManager.declType_t
import neo.framework.DeclManager.idDecl
import neo.idlib.Text.Lexer.idLexer
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Token.idToken
import neo.idlib.containers.List.idList
import neo.idlib.containers.idStrList
import neo.idlib.idException
import java.nio.ByteBuffer

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

    class idDeclSkin : idDecl(), SERiAL {
        private val associatedModels: idStrList = idStrList()
        private val mappings: idList<skinMapping_t> = idList()

        //
        //
        @Throws(idException::class)
        override fun SetDefaultText(): Boolean {
            // if there exists a material with the same name
            return if (DeclManager.declManager.FindType(declType_t.DECL_MATERIAL, GetName(), false) != null) {
                val generated = StringBuffer(2048)
                idStr.snPrintf(
                    generated, generated.capacity(),
                    """
                        skin %s // IMPLICITLY GENERATED
                        {
                        _default %s
                        }
                        
                        """.trimIndent(), GetName(), GetName()
                )
                SetText(generated.toString())
                true
            } else {
                false
            }
        }

        override fun DefaultDefinition(): String {
            return """{
	"*"	"_default"
}"""
        }

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

        override fun FreeData() {
            mappings.Clear()
        }

        fun RemapShaderBySkin(shader: Material.idMaterial?): Material.idMaterial? {
            var i: Int
            if (null == shader) {
                return null
            }

            // never remap surfaces that were originally nodraw, like collision hulls
            if (!shader.IsDrawn()) {
                return shader
            }
            i = 0
            while (i < mappings.Num()) {
                val map = mappings[i]

                // null = wildcard match
                if (map.from == null || map.from == shader) {
                    return map.to
                }
                i++
            }

            // didn't find a match or wildcard, so stay the same
            return shader
        }

        // model associations are just for the preview dialog in the editor
        fun GetNumModelAssociations(): Int {
            return associatedModels.size()
        }

        fun GetAssociatedModel(index: Int): String {
            return if (index >= 0 && index < associatedModels.size()) {
                associatedModels[index].toString()
            } else ""
        }

//        fun oSet(skin: idDeclSkin) {
//            mappings.set(skin.mappings)
//            associatedModels.set(skin.associatedModels)
//        }

        override fun AllocBuffer(): ByteBuffer {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun Read(buffer: ByteBuffer) {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun Write(): ByteBuffer {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }
    }
}
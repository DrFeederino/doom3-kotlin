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
        override fun DefaultDefinition(): String {
            return """{
	"DEFAULTED"	"1"
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
            // never be parsed mroe than once
            // find all of the dicts first, because copying inherited values will modify the dict
            val defList = idList<idDeclEntityDef>()
            while (true) {
                val kv: idKeyValue?
                kv = dict.MatchPrefix("inherit", null)
                if (null == kv) {
                    break
                }
                val copy =  /*static_cast<const idDeclEntityDef *>*/
                    DeclManager.declManager.FindType(
                        declType_t.DECL_ENTITYDEF,
                        kv.GetValue(),
                        false
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
            if (0 == Common.com_editors and (Common.EDITOR_RADIANT or Common.EDITOR_AAS)) {
                Game_local.game.CacheDictionaryMedia(dict)
            }
            return true
        }

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
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
        private val values: List.idList<Float> = List.idList()

        //
        //
        override fun DefaultDefinition(): String {
            return "{ { 0 } }"
        }

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
                        if (errorFlag[0]) {
                            // we got something non-numeric
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
            val `val`: Float = values[0] // template bug requires this to not be in the Append()?
            values.Append(`val`)
            return true
        }

        override fun FreeData() {
            snap = false
            clamp = false
            values.Clear()
        }

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
            return if (!snap) {
                // we duplicated the 0 index at the end at creation time, so we
                // don't need to worry about wrapping the filter
                values[iIndex] * (1.0f - iFrac) + values[iIndex + 1] * iFrac
            } else values[iIndex]
        }
    }
}
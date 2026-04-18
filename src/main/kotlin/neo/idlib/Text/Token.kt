package neo.idlib.Text

import neo.idlib.Text.Str.idStr
import neo.idlib.math.idMath

object Token {
    const val TT_BINARY = 0x00010 // binary number
    const val TT_DECIMAL = 0x00002 // decimal number
    const val TT_DOUBLE_PRECISION = 0x00200 // double
    const val TT_EXTENDED_PRECISION = 0x00400 // long double
    const val TT_FLOAT = 0x00080 // floating point number
    const val TT_HEX = 0x00004 // hexadecimal number
    const val TT_INDEFINITE = 0x01000 // indefinite 1.#IND
    const val TT_INFINITE = 0x00800 // infinite 1.#INF

    //
    // number sub types
    const val TT_INTEGER = 0x00001 // integer
    const val TT_IPADDRESS = 0x04000 // ip address
    const val TT_IPPORT = 0x08000 // ip port
    const val TT_LITERAL = 2 // literal
    const val TT_LONG = 0x00020 // long int
    const val TT_NAME = 4 // name
    const val TT_NAN = 0x02000 // NaN
    const val TT_NUMBER = 3 // number
    const val TT_OCTAL = 0x00008 // octal number
    const val TT_PUNCTUATION = 5 // punctuation
    const val TT_SINGLE_PRECISION = 0x00100 // float

    /*
     ===============================================================================

     idToken is a token read from a file or memory with idLexer or idParser

     ===============================================================================
     */
    // token types
    const val TT_STRING = 1 // string
    const val TT_UNSIGNED = 0x00040 // unsigned int
    const val TT_VALUESVALID = 0x10000 // set if intvalue and floatvalue are valid

    // string sub type is the length of the string
    // literal sub type is the ASCII code
    // punctuation sub type is the punctuation id
    // name sub type is the length of the name
    class idToken : idStr {
        //	friend class idParser;
        //	friend class idLexer;
        var flags // token flags, used for recursive defines
                = 0
        var line // line in script the token was on
                = 0
        var linesCrossed // number of lines crossed in white space before token
                = 0
        var subtype // token sub type
                = 0


        var type // token type
                = 0
        var floatValue // floating point value
                = 0.0f

        //
        var intValue // integer value
                : Long = 0L
        var next // next token in chain, only used by idParser
                : idToken? = null
        var whiteSpaceEnd_p // end of white space before token, only used by idLexer
                = 0
        var whiteSpaceStart_p // start of white space before token, only used by idLexer
                = 0

        //
        //
        constructor()
        constructor(token: idToken) {
            this.set(token)
        }

        // double value of TT_NUMBER
        fun GetDoubleValue(): Float {
            if (type != TT_NUMBER) {
                return 0.0f
            }
            if (0 == (subtype and TT_VALUESVALID)) {
                NumberValue()
            }
            return floatValue
        }

        // float value of TT_NUMBER
        fun GetFloatValue(): Float {
            return GetDoubleValue()
        }

        fun GetUnsignedLongValue(): Long {        // unsigned long value of TT_NUMBER
            if (type != TT_NUMBER) {
                return 0
            }
            if (0 == subtype and TT_VALUESVALID) {
                NumberValue()
            }
            return intValue
        }

        fun GetIntValue(): Int {                // int value of TT_NUMBER
            return GetUnsignedLongValue().toInt()
        }

        fun WhiteSpaceBeforeToken(): Boolean { // returns length of whitespace before token
            return whiteSpaceEnd_p > whiteSpaceStart_p
        }

        fun ClearTokenWhiteSpace() {        // forget whitespace before token
            whiteSpaceStart_p = 0
            whiteSpaceEnd_p = 0
            linesCrossed = 0
        }

        //
        fun NumberValue() {                // calculate values for a TT_NUMBER
            var c: Int
            val p: String = data
            var pIndex = 0
            assert(type == TT_NUMBER)
            floatValue = 0.0f
            intValue = 0
            // floating point number
            if (subtype and TT_FLOAT != 0) {
                if (subtype and (TT_INFINITE or TT_INDEFINITE or TT_NAN) != 0) {
                    if (subtype and TT_INFINITE != 0) {            // 1.#INF
                        val inf = 0x7f800000
                        floatValue = inf.toFloat()
                    } else if (subtype and TT_INDEFINITE != 0) {    // 1.#IND
                        val ind = -0x400000
                        floatValue = ind.toFloat()
                    } else if (subtype and TT_NAN != 0) {            // 1.#QNAN
                        val nan = 0x7fc00000
                        floatValue = nan.toFloat()
                    }
                } else {
                    floatValue = data.toFloat()
                }
                intValue = idMath.Ftol(floatValue)
            } else if (subtype and TT_DECIMAL != 0) {
                while (pIndex < p.length) {
                    intValue = intValue * 10 + (p[pIndex] - '0')
                    pIndex++
                }
                floatValue = intValue.toFloat()
            } else if (subtype and TT_IPADDRESS != 0) {
                c = 0
                while ( /*p[pIndex] &&*/p[pIndex] != ':') {
                    if (p[pIndex] == '.') {
                        while (c != 3) {
                            intValue = intValue * 10
                            c++
                        }
                        c = 0
                    } else {
                        intValue = intValue * 10 + (p[pIndex] - '0')
                        c++
                    }
                    pIndex++
                }
                while (c != 3) {
                    intValue = intValue * 10
                    c++
                }
                floatValue = intValue.toFloat()
            } else if (subtype and TT_OCTAL != 0) {
                // step over the first zero
                pIndex += 1
                while (pIndex < p.length) {
                    intValue = (intValue shl 3) + (p[pIndex] - '0')
                    pIndex++
                }
                floatValue = intValue.toFloat()
            } else if (subtype and TT_HEX != 0) {
                // step over the leading 0x or 0X
                pIndex += 2
                while (pIndex < p.length) {
                    intValue = intValue shl 4
                    intValue += if (p[pIndex] in 'a'..'f') {
                        (p[pIndex] - 'a' + 10)
                    } else if (p[pIndex] in 'A'..'F') {
                        (p[pIndex] - 'A' + 10)
                    } else {
                        (p[pIndex] - '0')
                    }
                    pIndex++
                }
                floatValue = intValue.toFloat()
            } else if (subtype and TT_BINARY != 0) {
                // step over the leading 0b or 0B
                pIndex += 2
                while (pIndex < p.length) {
                    intValue = (intValue shl 1) + (p[pIndex] - '0')
                    pIndex++
                }
                floatValue = intValue.toFloat()
            }
            subtype = subtype or TT_VALUESVALID
        }

        // append character without adding trailing zero
        fun AppendDirty(a: Char) {
            EnsureAlloced(len + 2, true)
            if (_sb == null) {
                _sb = StringBuilder(data)
            } else if (!_dirty) {
                _sb!!.clear()
                _sb!!.append(data)
            }
            _sb!!.append(a)
            _dirty = true
            len++
        }

        fun set(token: idToken): idToken {
            type = token.type
            subtype = token.subtype
            line = token.line
            linesCrossed = token.linesCrossed
            flags = token.flags
            intValue = token.intValue
            floatValue = token.floatValue
            whiteSpaceStart_p = token.whiteSpaceStart_p
            whiteSpaceEnd_p = token.whiteSpaceEnd_p
            next = token.next
            super.set(token)
            return this
        }


        override fun equals(obj: Any?): Boolean {
            if (obj == null) {
                return false
            }
            //idStr
            if (javaClass != obj.javaClass) {
                return super.equals(obj)
            }
            val other = obj as idToken
            return data == other.data
        }
    }
}
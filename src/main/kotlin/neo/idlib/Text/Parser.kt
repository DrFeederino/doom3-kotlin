package neo.idlib.Text

import neo.idlib.Text.Lexer.idLexer
import neo.idlib.Text.Lexer.punctuation_t
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Token.idToken
import neo.idlib.containers.CFloat
import neo.idlib.containers.CInt
import neo.idlib.idException
import neo.idlib.idLib
import neo.sys.PATHSEPERATOR_STR
import java.nio.CharBuffer
import java.util.*
import kotlin.math.abs

object Parser {
    const val BUILTIN_DATE = 3
    const val BUILTIN_FILE = 2

    //
    const val BUILTIN_LINE = 1
    const val BUILTIN_STDC = 5
    const val BUILTIN_TIME = 4
    const val DEFINEHASHSIZE = 2048
    const val DEFINE_FIXED = 0x0001
    const val INDENT_ELIF = 0x0004
    const val INDENT_ELSE = 0x0002

    //
    const val INDENT_IF = 0x0001
    const val INDENT_IFDEF = 0x0008
    const val INDENT_IFNDEF = 0x0010

    //
    //
    //
    const val MAX_DEFINEPARMS = 128

    //
    const val TOKEN_FL_RECURSIVE_DEFINE = 1

    //
    //    static define_t[] globaldefines;
    //    
    //    
    /*
     ================
     PC_NameHash
     ================
     */
    fun PC_NameHash(name: String): Int {
        return PC_NameHash(name.toCharArray())
    }

    fun PC_NameHash(name: CharArray): Int {
        var hash: Int
        var i: Int
        hash = 0
        i = 0
        while (i < name.size && name[i] != '\u0000') {
            hash += name[i].code * (119 + i)
            i++
        }
        hash = hash xor (hash shr 10) xor (hash shr 20) and DEFINEHASHSIZE - 1
        return hash
    }

    // macro definitions
    internal class define_s {
        var builtin // > 0 if builtin define
                = 0
        var flags // define flags
                = 0
        var hashnext // next define in the hash chain
                : define_s? = null
        var name // define name
                : String = ""
        var next // next defined macro in a list
                : define_s? = null
        var numparms // number of define parameters
                = 0
        var parms // define parameters
                : idToken? = null
        var tokens // macro tokens (possibly containing parm tokens)
                : idToken? = null
    }

    // indents used for conditional compilation directives:
    // #if, #else, #elif, #ifdef, #ifndef
    internal class indent_s {
        var next // next indent on the indent stack
                : indent_s? = null
        var script // script the indent was in
                : idLexer = idLexer()
        var skip // true if skipping current indent
                = 0
        var type // indent type
                = 0
    }

    class idParser {
        private var OSPath // true if the file was loaded from an OS path
                : Boolean
        private var definehash // hash chain with defines
                : Array<define_s?> = arrayOfNulls(DEFINEHASHSIZE)
        private var defines // list with macro definitions
                : Array<define_s?>?
        private val filename // file name of the script
                : idStr = idStr()
        private var flags // flags used for script parsing
                : Int
        private val includepath // path to include files
                : idStr = idStr()
        private var indentstack // stack with indents
                : indent_s?
        private var loaded // set when a source file is loaded from file or memory
                : Boolean
        private var marker_p: String?
        private var punctuations // punctuations to use
                : Array<punctuation_t>?
        private var scriptstack // stack with scripts of the source
                : idLexer?

        //
        //
        private var skip // > 0 if skipping conditional code
                = 0
        private var tokens // tokens to read first
                : idToken?
        private val _tempToken = idToken()

        // constructor
        constructor() {
            loaded = false
            OSPath = false
            punctuations = null
            flags = 0
            scriptstack = null
            indentstack = null
            definehash = arrayOfNulls(DEFINEHASHSIZE)
            defines = null
            tokens = null
            marker_p = null
        }

        constructor(flags: Int) {
            loaded = false
            OSPath = false
            punctuations = null
            this.flags = flags
            scriptstack = null
            indentstack = null
            definehash = arrayOfNulls(DEFINEHASHSIZE)
            defines = null
            tokens = null
            marker_p = null
        }

        constructor(filename: String) {
            loaded = false
            OSPath = true
            punctuations = null
            flags = 0
            scriptstack = null
            indentstack = null
            definehash = arrayOfNulls(DEFINEHASHSIZE)
            defines = null
            tokens = null
            marker_p = null
            LoadFile(filename, false)
        }

        constructor(filename: String, flags: Int) {
            loaded = false
            OSPath = true
            punctuations = null
            this.flags = flags
            scriptstack = null
            indentstack = null
            definehash = arrayOfNulls(DEFINEHASHSIZE)
            defines = null
            tokens = null
            marker_p = null
            LoadFile(filename, false)
        }

        constructor(filename: String, flags: Int, OSPath: Boolean) {
            loaded = false
            this.OSPath = true
            punctuations = null
            this.flags = flags
            scriptstack = null
            indentstack = null
            definehash = arrayOfNulls(DEFINEHASHSIZE)
            defines = null
            tokens = null
            marker_p = null
            LoadFile(filename, OSPath)
        }

        //					// destructor
        //public					~idParser();
        constructor(ptr: String, length: Int, name: String) {
            loaded = false
            OSPath = true
            punctuations = null
            flags = 0
            scriptstack = null
            indentstack = null
            definehash = arrayOfNulls(DEFINEHASHSIZE)
            defines = null
            tokens = null
            marker_p = null
            LoadMemory(ptr, length, name)
        }

        constructor(ptr: String, length: Int, name: String, flags: Int) {
            loaded = false
            OSPath = true
            punctuations = null
            this.flags = flags
            scriptstack = null
            indentstack = null
            definehash = arrayOfNulls(DEFINEHASHSIZE)
            defines = null
            tokens = null
            marker_p = null
            LoadMemory(ptr, length, name)
        }

        @Throws(idException::class)
        fun LoadFile(filename: idStr): Boolean {
            return LoadFile(filename.toString())
        }

        // load a source file

        @Throws(idException::class)
        fun LoadFile(filename: String, OSPath: Boolean = false): Boolean {
            val script: idLexer
            if (loaded) {
                idLib.common.FatalError("idParser::loadFile: another source already loaded")
                return false
            }
            script = idLexer(filename, 0, OSPath)
            if (!script.IsLoaded()) {
//		delete script;
                //script = null
                return false
            }
            script.SetFlags(flags)
            script.SetPunctuations(punctuations)
            script.next = null
            this.OSPath = OSPath
            this.filename.set(filename)
            scriptstack = script
            tokens = null
            indentstack = null
            skip = 0
            loaded = true
            if (definehash.isEmpty()) {
                defines = null
                //definehash = Array(DEFINEHASHSIZE) { define_s() } // Mem_ClearedAlloc(DEFINEHASHSIZE);
                AddGlobalDefinesToSource()
            }
            return true
        }

        // load a source from the given memory with the given length
        // NOTE: the ptr is expected to point at a valid C string: ptr[length] == '\0'
        @Throws(idException::class)
        fun LoadMemory(ptr: CharBuffer, length: Int, name: String): Boolean {
            val script: idLexer
            if (loaded) {
                idLib.common.FatalError("idParser.loadMemory: another source already loaded")
                return false
            }
            script = idLexer(ptr, length, name)
            if (!script.IsLoaded()) {
//		delete script;
                return false
            }
            script.SetFlags(flags)
            script.SetPunctuations(punctuations)
            script.next = null
            filename.set(name)
            scriptstack = script
            tokens = null
            indentstack = null
            skip = 0
            loaded = true
            if (definehash.isEmpty()) {
                defines = null
                definehash = arrayOfNulls(DEFINEHASHSIZE) // Mem_ClearedAlloc(DEFINEHASHSIZE);
                AddGlobalDefinesToSource()
            }
            return true
        }

        @Throws(idException::class)
        fun LoadMemory(ptr: String, length: Int, name: String): Boolean {
            return LoadMemory(CharBuffer.wrap(ptr), length, name)
        }

        // free the current source

        fun FreeSource(keepDefines: Boolean = false /*= false*/) {
            var script: idLexer?
            var token: idToken?
            var define: define_s?
            var indent: indent_s?
            var i: Int

            // free all the scripts
            while (scriptstack != null) {
                script = scriptstack
                scriptstack = scriptstack!!.next
                //		delete script;
            }
            // free all the tokens
            while (tokens != null) {
                token = tokens
                tokens = tokens!!.next
                //		delete token;
            }
            // free all indents
            while (indentstack != null) {
                indent = indentstack
                indentstack = indentstack!!.next
                //                Mem_Free(indent);
            }
            if (!keepDefines) {
                // free hash table
                if (definehash.isEmpty()) {
                    // free defines
                    i = 0
                    while (i < DEFINEHASHSIZE) {
                        while (definehash[i] != null) {
                            define = definehash[i]!!
                            definehash[i] = definehash[i]!!.hashnext
                            FreeDefine(define)
                        }
                        i++
                    }
                    defines = null
                    //                    Mem_Free(this.definehash);
                    definehash = arrayOfNulls(DEFINEHASHSIZE)
                }
            }
            loaded = false
        }

        // returns true if a source is loaded
        fun IsLoaded(): Boolean {
            return loaded
        }

        // read a token from the source
        @Throws(idException::class)
        fun ReadToken(token: idToken): Boolean {
            var define: define_s?
            while (true) {
                if (!ReadSourceToken(token)) {
                    return false
                }
                // check for precompiler directives
                if (token.type == Token.TT_PUNCTUATION
                    && token[0] == '#' && (token.Length() == 1 || token[1] == '\u0000')
                ) {
                    // read the precompiler directive
                    if (!ReadDirective()) {
                        return false
                    }
                    continue
                }
                // if skipping source because of conditional compilation
                if (skip != 0) {
                    continue
                }
                // recursively concatenate strings that are behind each other still resolving defines
                if (token.type == Token.TT_STRING && (scriptstack!!.GetFlags() and Lexer.LEXFL_NOSTRINGCONCAT) == 0) {
                    val newtoken = idToken()
                    if (ReadToken(newtoken)) {
                        if (newtoken.type == Token.TT_STRING) {
                            token.Append(newtoken.data)
                        } else {
                            UnreadSourceToken(newtoken)
                        }
                    }
                }
                //
                if (0 == scriptstack!!.GetFlags() and Lexer.LEXFL_NODOLLARPRECOMPILE) {
                    // check for special precompiler directives
                    if (token.type == Token.TT_PUNCTUATION
                        && token[0] == '$' && (token.Length() == 1 || token[1] == '\u0000')
                    ) {
                        // read the precompiler directive
                        if (ReadDollarDirective()) {
                            continue
                        }
                    }
                }
                // if the token is a name
                if (token.type == Token.TT_NAME && 0 == token.flags and TOKEN_FL_RECURSIVE_DEFINE) {
                    // check if the name is a define macro
                    define = FindHashedDefine(definehash, token.toString())
                    // if it is a define macro
                    if (define != null) {
                        // expand the defined macro
                        if (!ExpandDefineIntoSource(token, define)) {
                            return false
                        }
                        continue
                    }
                }
                // found a token
                return true
            }
        }

        // expect a certain token, reads the token when available
        @Throws(idException::class)
        fun ExpectTokenString(string: String): Boolean {
            val token = _tempToken
            if (!ReadToken(token)) {
                this.Error("couldn't find expected '%s'", string)
                return false
            }
            if (token.toString() != string) {
                this.Error("expected '%s' but found '%s'", string, token)
                return false
            }
            return true
        }

        // expect a certain token type
        @Throws(idException::class)
        fun ExpectTokenType(type: Int, subtype: Int, token: idToken): Boolean {
            var str: String
            if (!ReadToken(token)) {
                this.Error("couldn't read expected token")
                return false
            }
            if (token.type != type) {
                str = when (type) {
                    Token.TT_STRING -> "string"
                    Token.TT_LITERAL -> "literal"
                    Token.TT_NUMBER -> "number"
                    Token.TT_NAME -> "name"
                    Token.TT_PUNCTUATION -> "punctuation"
                    else -> "unknown type"
                }
                this.Error("expected a %s but found '%s'", str, token)
                return false
            }
            if (token.type == Token.TT_NUMBER) {
                if (token.subtype and subtype != subtype) {
//                    str.Clear();
                    str = ""
                    if (subtype and Token.TT_DECIMAL != 0) {
                        str = "decimal "
                    }
                    if (subtype and Token.TT_HEX != 0) {
                        str = "hex "
                    }
                    if (subtype and Token.TT_OCTAL != 0) {
                        str = "octal "
                    }
                    if (subtype and Token.TT_BINARY != 0) {
                        str = "binary "
                    }
                    if (subtype and Token.TT_UNSIGNED != 0) {
                        str += "unsigned "
                    }
                    if (subtype and Token.TT_LONG != 0) {
                        str += "long "
                    }
                    if (subtype and Token.TT_FLOAT != 0) {
                        str += "float "
                    }
                    if (subtype and Token.TT_INTEGER != 0) {
                        str += "integer "
                    }
                    str.trim { it <= ' ' } //StripTrailing(' ');
                    this.Error("expected %s but found '%s'", str.toCharArray(), token)
                    return false
                }
            } else if (token.type == Token.TT_PUNCTUATION) {
                if (subtype < 0) {
                    this.Error("BUG: wrong punctuation subtype")
                    return false
                }
                if (token.subtype != subtype) {
                    this.Error(
                        "expected '%s' but found '%s'",
                        scriptstack!!.GetPunctuationFromId(subtype),
                        token.toString()
                    )
                    return false
                }
            }
            return true
        }

        // expect a token
        @Throws(idException::class)
        fun ExpectAnyToken(token: idToken): Boolean {
            return if (!ReadToken(token)) {
                this.Error("couldn't read expected token")
                false
            } else {
                true
            }
        }

        // returns true if the next token equals the given string and removes the token from the source
        @Throws(idException::class)
        fun CheckTokenString(string: String): Boolean {
            val tok = _tempToken
            if (!ReadToken(tok)) {
                return false
            }
            //if the token is available
            if (tok.toString() == string) {
                return true
            }
            UnreadSourceToken(tok)
            return false
        }

        // returns true if the next token equals the given type and removes the token from the source
        @Throws(idException::class)
        fun CheckTokenType(type: Int, subtype: Int, token: idToken): Boolean {
            val tok = _tempToken
            if (!ReadToken(tok)) {
                return false
            }
            //if the type matches
            if (tok.type == type && tok.subtype and subtype == subtype) {
                token.set(tok)
                return true
            }
            UnreadSourceToken(tok)
            return false
        }

        // returns true if the next token equals the given string but does not remove the token from the source
        @Throws(idException::class)
        fun PeekTokenString(string: String): Boolean {
            val tok = idToken()
            if (!ReadToken(tok)) {
                return false
            }
            UnreadSourceToken(tok)

            // if the token is available
            return tok.toString() == string
        }

        // returns true if the next token equals the given type but does not remove the token from the source
        @Throws(idException::class)
        fun PeekTokenType(type: Int, subtype: Int, token: idToken): Boolean {
            val tok = idToken()
            if (!ReadToken(tok)) {
                return false
            }
            UnreadSourceToken(tok)

            // if the type matches
            if (tok.type == type && tok.subtype and subtype == subtype) {
                token.set(tok)
                return true
            }
            return false
        }

        // skip tokens until the given token string is read
        @Throws(idException::class)
        fun SkipUntilString(string: String): Boolean {
            val token = _tempToken
            while (ReadToken(token)) {
                if (token.toString() == string) {
                    return true
                }
            }
            return false
        }

        // skip the rest of the current line
        @Throws(idException::class)
        fun SkipRestOfLine(): Boolean {
            val token = idToken()
            while (ReadToken(token)) {
                if (token.linesCrossed != 0) {
                    UnreadSourceToken(token)
                    return true
                }
            }
            return false
        }

        /*
         =================
         idParser::SkipBracedSection

         Skips until a matching close brace is found.
         Internal brace depths are properly skipped.
         =================
         */
        // skip the braced section

        @Throws(idException::class)
        fun SkipBracedSection(parseFirstBrace: Boolean = true /*= true*/): Boolean {
            val token = idToken()
            var depth: Int
            depth = if (parseFirstBrace) 0 else 1
            do {
                if (!ReadToken(token)) {
                    return false
                }
                if (token.type == Token.TT_PUNCTUATION) {
                    if (token.toString() == "{") {
                        depth++
                    } else if (token.toString() == "}") {
                        depth--
                    }
                }
            } while (depth != 0)
            return true
        }

        /*
         =================
         idParser::ParseBracedSection

         The next token should be an open brace.
         Parses until a matching close brace is found.
         Internal brace depths are properly skipped.
         =================
         */
        // parse a braced section into a string
        @Throws(idException::class)
        fun ParseBracedSection(out: idStr, tabs: Int /*= -1*/): String {
            var tabs = tabs
            val token = idToken()
            var i: Int
            var depth: Int
            val doTabs = tabs >= 0
            out.Empty()
            if (!ExpectTokenString("{")) {
                return out.toString()
            }
            out.set("{")
            depth = 1
            do {
                if (!ReadToken(token)) {
                    Error("missing closing brace")
                    return out.toString()
                }

                // if the token is on a new line
                i = 0
                while (i < token.linesCrossed) {
                    out.Append("\r\n")
                    i++
                }
                if (doTabs && token.linesCrossed != 0) {
                    i = tabs
                    if (token.toString() == "}" && i > 0) {
                        i--
                    }
                    while (i-- > 0) {
                        out.Append("\t")
                    }
                }
                if (token.type == Token.TT_PUNCTUATION) {
                    if (token.toString() == "{") {
                        depth++
                        if (doTabs) {
                            tabs++
                        }
                    } else if (token.toString() == "}") {
                        depth--
                        if (doTabs) {
                            tabs--
                        }
                    }
                }
                if (token.type == Token.TT_STRING) {
                    out.Append("\"" + token + "\"")
                } else {
                    out.Append(token)
                }
                out.Append(" ")
            } while (depth != 0)
            return out.toString()
        }

        /*
         =================
         idParser::ParseBracedSectionExact

         The next token should be an open brace.
         Parses until a matching close brace is found.
         Maintains the exact formating of the braced section

         * TODO:FIXME: what about precompilation ?
         =================
         */
        // parse a braced section into a string, maintaining indents and newlines
        @Throws(idException::class)
        fun ParseBracedSectionExact(out: idStr, tabs: Int /*= -1*/): String {
            return scriptstack!!.ParseBracedSectionExact(out, tabs)
        }

        // parse the rest of the line
        @Throws(idException::class)
        fun ParseRestOfLine(out: idStr): String {
            val token = idToken()
            out.Empty()
            while (ReadToken(token)) {
                if (token.linesCrossed != 0) {
                    UnreadSourceToken(token)
                    break
                }
                if (out.Length() != 0) {
                    out.Append(" ")
                }
                out.Append(token)
            }
            return out.toString()
        }

        // unread the given token
        fun UnreadToken(token: idToken) {
            UnreadSourceToken(token)
        }

        // read a token only if on the current line
        @Throws(idException::class)
        fun ReadTokenOnLine(token: idToken): Boolean {
            val tok = idToken()
            if (!ReadToken(tok)) {
                return false
            }
            // if no lines were crossed before this token
            if (0 == tok.linesCrossed) {
                token.set(tok)
                return true
            }
            //
            UnreadSourceToken(tok)
            return false
        }

        // read a signed integer
        @Throws(idException::class)
        fun ParseInt(): Int {
            val token = _tempToken
            if (!ReadToken(token)) {
                this.Error("couldn't read expected integer")
                return 0
            }
            if (token.type == Token.TT_PUNCTUATION && token.toString() == "-") {
                ExpectTokenType(Token.TT_NUMBER, Token.TT_INTEGER, token)
                return -token.GetIntValue()
            } else if (token.type != Token.TT_NUMBER || token.subtype == Token.TT_FLOAT) {
                this.Error("expected integer value, found '%s'", token)
            }
            return token.GetIntValue()
        }

        // read a boolean
        @Throws(idException::class)
        fun ParseBool(): Boolean {
            val token = _tempToken
            if (!ExpectTokenType(Token.TT_NUMBER, 0, token)) {
                this.Error("couldn't read expected boolean")
                return false
            }
            return token.GetIntValue() != 0
        }

        // read a floating point number
        @Throws(idException::class)
        fun ParseFloat(): Float {
            val token = _tempToken
            if (!ReadToken(token)) {
                this.Error("couldn't read expected floating point number")
                return 0.0f
            }
            if (token.type == Token.TT_PUNCTUATION && token.toString() == "-") {
                ExpectTokenType(Token.TT_NUMBER, 0, token)
                return -token.GetFloatValue()
            } else if (token.type != Token.TT_NUMBER) {
                this.Error("expected float value, found '%s'", token)
            }
            return token.GetFloatValue()
        }

        // parse matrices with floats
        @Throws(idException::class)
        fun Parse1DMatrix(x: Int, m: FloatArray): Boolean {
            var i: Int
            if (!ExpectTokenString("(")) {
                return false
            }
            i = 0
            while (i < x) {
                m[i] = ParseFloat()
                i++
            }
            return ExpectTokenString(")")
        }

        @Throws(idException::class)
        fun Parse2DMatrix(y: Int, x: Int, m: FloatArray): Boolean {
            var i: Int
            if (!ExpectTokenString("(")) {
                return false
            }
            i = 0
            while (i < y) {
                val tempM = FloatArray(m.size - i * x)
                System.arraycopy(m, i * x, tempM, 0, tempM.size)
                if (!Parse1DMatrix(x, tempM)) {
                    System.arraycopy(tempM, 0, m, i * x, tempM.size)
                    return false
                }
                System.arraycopy(tempM, 0, m, i * x, tempM.size)
                i++
            }
            return ExpectTokenString(")")
        }

        @Throws(idException::class)
        fun Parse3DMatrix(z: Int, y: Int, x: Int, m: FloatArray): Boolean {
            var i: Int
            if (!ExpectTokenString("(")) {
                return false
            }
            i = 0
            while (i < z) {
                val tempM = FloatArray(m.size - i * x * y)
                System.arraycopy(m, i * x * y, tempM, 0, tempM.size)
                if (!Parse2DMatrix(y, x, tempM)) {
                    System.arraycopy(tempM, 0, m, i * x * y, tempM.size)
                    return false
                }
                System.arraycopy(tempM, 0, m, i * x * y, tempM.size)
                i++
            }
            return ExpectTokenString(")")
        }

        // get the white space before the last read token
        fun GetLastWhiteSpace(whiteSpace: idStr): Int {
            if (scriptstack != null) {
                scriptstack!!.GetLastWhiteSpace(whiteSpace)
            } else {
                whiteSpace.Clear()
            }
            return whiteSpace.Length()
        }

        // Set a marker in the source file (there is only one marker)
        fun SetMarker() {
            marker_p = null
        }

        /*
         ================
         idParser::GetStringFromMarker

         * TODO:FIXME: this is very bad code, the script isn't even garrenteed to still be around
         ================
         */
        // Get the string from the marker to the current position
        @Throws(idException::class)
        fun GetStringFromMarker(out: idStr, clean: Boolean /*= false*/) {
            val p: Int //marker
            //            int save;
            if (marker_p == null) {
                marker_p = String(scriptstack!!.buffer)
            }
            p = if (tokens != null) {
                tokens!!.whiteSpaceStart_p
            } else {
                scriptstack!!.script_p
            }

            // Set the end character to NULL to give us a complete string
//            save = p;
//            p = 0;
            // If cleaning then reparse
            if (clean) {
                val temp = idParser(marker_p!!, p, "temp", flags) //TODO:check whether this substringing works
                val token = idToken()
                while (temp.ReadToken(token)) {
                    out.plusAssign(token)
                }
            } else {
                out.set(marker_p)
            }

            // restore the character we set to NULL
//            p = save;
        }

        // add a define to the source
        @Throws(idException::class)
        fun AddDefine(string: String): Boolean {
            val define: define_s?
            define = DefineFromString(string)
            if (null == define) {
                return false
            }
            AddDefineToHash(define, definehash)
            return true
        }

        // add builtin defines
        fun AddBuiltinDefines() {
            var i: Int
            var define: define_s

            class builtin(val string: String?, val id: Int)

            val builtins = arrayOf(
                builtin("__LINE__", BUILTIN_LINE),
                builtin("__FILE__", BUILTIN_DATE),
                builtin("__TIME__", BUILTIN_TIME),
                builtin("__STDC__", BUILTIN_STDC),
                builtin(null, 0)
            )
            i = 0
            while (builtins[i].string != null) {

//		define = (define_t *) Mem_Alloc(sizeof(define_t) + strlen(builtin[i].string) + 1);
                define = define_s()
                define.name = builtins[i].string!!
                //		strcpy(define.name, builtin[i].string);
                define.flags = DEFINE_FIXED
                define.builtin = builtins[i].id
                define.numparms = 0
                define.parms = null
                define.tokens = null
                // add the define to the source
                AddDefineToHash(define, definehash)
                i++
            }
        }

        // set the source include path
        fun SetIncludePath(path: String) {
            includepath.set(path)
            // add trailing path seperator
            if (includepath[includepath.Length() - 1] != '\\'
                && includepath[includepath.Length() - 1] != '/'
            ) {
                includepath.Append(PATHSEPERATOR_STR)
            }
        }

        // set the punctuation set
        fun SetPunctuations(p: Array<punctuation_t>?) {
            punctuations = p
        }

        // returns a pointer to the punctuation with the given id
        fun GetPunctuationFromId(id: Int): String {
            var i: Int
            if (null == punctuations) {
                val lex = idLexer()
                return lex.GetPunctuationFromId(id)
            }
            i = 0
            while (punctuations!![i].p != null) {
                if (punctuations!![i].n == id) {
                    return punctuations!![i].p!!
                }
                i++
            }
            return "unknown punctuation"
        }

        // get the id for the given punctuation
        fun GetPunctuationId(p: String): Int {
            var i: Int
            if (null == punctuations) {
                val lex = idLexer()
                return lex.GetPunctuationId(p)
            }
            i = 0
            while (punctuations!![i].p != null) {
                if (punctuations!![i].p == p) {
                    return punctuations!![i].n
                }
                i++
            }
            return 0
        }

        // set lexer flags
        fun SetFlags(flags: Int) {
            var s: idLexer?
            this.flags = flags
            s = scriptstack
            while (s != null) {
                s.SetFlags(flags)
                s = s.next
            }
        }

        // get lexer flags
        fun GetFlags(): Int {
            return flags
        }

        // returns the current filename
        fun GetFileName(): idStr? {
            return if (scriptstack != null) {
                scriptstack!!.GetFileName()
            } else {
                null
            }
        }

        // get current offset in current script
        fun GetFileOffset(): Int {
            return if (scriptstack != null) {
                scriptstack!!.GetFileOffset()
            } else {
                0
            }
        }

        // get file time for current script
        fun  /*ID_TIME_T*/GetFileTime(): Long {
            return if (scriptstack != null) {
                scriptstack!!.GetFileTime()
            } else {
                0
            }
        }

        // returns the current line number
        fun GetLineNum(): Int {
            return if (scriptstack != null) {
                scriptstack!!.GetLineNum()
            } else {
                0
            }
        }

        // print an error message
        @Throws(idException::class)
        fun Error(fmt: String, vararg args: Any?) {
//	char text[MAX_STRING_CHARS];
//            char text[MAX_STRING_CHARS];
//            va_list ap;
//
//            va_start(ap, str);
//            vsprintf(text, str, ap);
//            va_end(ap);
            if (scriptstack != null) {
                val text = String.format(fmt, *args)
                scriptstack!!.Error(text)
            }
        }

        @Deprecated("")
        @Throws(idException::class)
        fun Error(str: String, chr: CharArray, vararg chrs: CharArray) {
            this.Error(str)
            this.Error(ctos(chr))
            for (charoal in chrs) {
                this.Error(ctos(charoal))
            }
        }

        // print a warning message
        @Throws(idException::class)
        fun Warning(fmt: String, vararg args: Any) {
//            char text[MAX_STRING_CHARS];
//            va_list ap;
//
//            va_start(ap, str);
//            vsprintf(text, str, ap);
//            va_end(ap);
            if (scriptstack != null) {
                val text = String.format(fmt, *args)
                scriptstack!!.Warning(text)
            }
        }

        @Deprecated("")
        @Throws(idException::class)
        fun Warning(str: String, chr: CharArray, vararg chrs: CharArray) {
            this.Warning(str)
            this.Warning(ctos(chr))
            for (charoal in chrs) {
                this.Warning(ctos(charoal))
            }
        }

        private fun PushIndent(type: Int, skip: Int) {
            val indent: indent_s

//	indent = (indent_t *) Mem_Alloc(sizeof(indent_t));
            indent = indent_s()
            indent.type = type
            indent.script = scriptstack!!
            indent.skip = if (skip != 0) 1 else 0 //TODO:booleanize?
            this.skip += indent.skip
            indent.next = indentstack
            indentstack = indent
        }

        private fun PopIndent(type: CInt, skip: CInt) {
            val indent: indent_s?
            type._val = 0
            skip._val = 0
            indent = indentstack
            if (null == indent) {
                return
            }

            // must be an indent from the current script
            if (indentstack!!.script !== scriptstack) {
                return
            }
            type._val = indent.type
            skip._val = indent.skip
            indentstack = indentstack!!.next
            this.skip -= indent.skip
            //	Mem_Free( indent );
        }

        @Throws(idException::class)
        private fun PushScript(script: idLexer) {
            var s: idLexer?
            s = scriptstack
            while (s != null) {
                if (0 == idStr.Icmp(s.GetFileName(), script.GetFileName())) {
                    this.Warning("'%s' recursively included", script.GetFileName())
                    return
                }
                s = s.next
            }
            //push the script on the script stack
            script.next = scriptstack
            scriptstack = script
        }

        @Throws(idException::class)
        private fun ReadSourceToken(token: idToken): Boolean {
            val t: idToken?
            var script: idLexer?
            val type = CInt()
            val skip = CInt()
            var changedScript: Int
            if (scriptstack == null) {
                idLib.common.FatalError("idParser::ReadSourceToken: not loaded")
                return false
            }
            changedScript = 0
            // if there's no token already available
            while (tokens == null) {
                // if there's a token to read from the script
                if (scriptstack!!.ReadToken(token)) {
                    token.linesCrossed += changedScript

                    // set the marker based on the start of the token read in
                    if (!marker_p.isNullOrEmpty()) {
                        marker_p = "" //token.whiteSpaceEnd_p;//TODO:does marker_p do anythning???
                    }
                    return true
                }
                // if at the end of the script
                if (scriptstack!!.EndOfFile()) {
                    // remove all indents of the script
                    while (indentstack != null && indentstack!!.script === scriptstack) {
                        this.Warning("missing #endif")
                        PopIndent(type, skip)
                    }
                    changedScript = 1
                }
                // if this was the initial script
                if (scriptstack!!.next == null) {
                    return false
                }
                // remove the script and return to the previous one
                script = scriptstack
                scriptstack = scriptstack!!.next
                //		delete script;
            }
            // copy the already available token
            token.set(tokens!!)
            // remove the token from the source
            t = tokens
            tokens = tokens!!.next
            //	delete t;
            return true
        }

        /*
         ================
         idParser::ReadLine

         reads a token from the current line, continues reading on the next
         line only if a backslash '\' is found
         ================
         */
        @Throws(idException::class)
        private fun ReadLine(token: idToken): Boolean {
            var crossline: Int
            crossline = 0
            do {
                if (!ReadSourceToken(token)) {
                    return false
                }
                if (token.linesCrossed > crossline) {
                    UnreadSourceToken(token)
                    return false
                }
                crossline = 1
            } while (token.toString() == "\\")
            return true
        }

        private fun UnreadSourceToken(token: idToken): Boolean {
            val t: idToken
            t = idToken(token)
            t.next = tokens
            tokens = t
            return true
        }

        @Throws(idException::class)
        private fun ReadDefineParms(define: define_s, parms: Array<idToken?>, maxparms: Int): Boolean {
            var newdefine: define_s?
            val token = idToken()
            var t: idToken
            var last: idToken?
            var i: Int
            var done: Int
            var lastcomma: Int
            var numparms: Int
            var indent: Int
            if (!ReadSourceToken(token)) {
                this.Error("define '%s' missing parameters", define.name)
                return false
            }
            if (define.numparms > maxparms) {
                this.Error("define with more than %d parameters", "" + maxparms)
                return false
            }
            i = 0
            while (i < define.numparms) {
                parms[i] = null
                i++
            }
            // if no leading "("
            if (token.toString() != "(") {
                UnreadSourceToken(token)
                this.Error("define '%s' missing parameters", define.name)
                return false
            }
            // read the define parameters
            done = 0
            numparms = 0
            indent = 1
            while (0 == done) {
                if (numparms >= maxparms) {
                    this.Error("define '%s' with too many parameters", define.name)
                    return false
                }
                parms[numparms] = null
                lastcomma = 1
                last = null
                while (0 == done) {
                    if (!ReadSourceToken(token)) {
                        this.Error("define '%s' incomplete", define.name)
                        return false
                    }
                    if (token.toString() == ",") {
                        if (indent <= 1) {
                            if (lastcomma != 0) {
                                this.Warning("too many comma's")
                            }
                            if (numparms >= define.numparms) {
                                this.Warning("too many define parameters")
                            }
                            lastcomma = 1
                            break
                        }
                    } else if (token.toString() == "(") {
                        indent++
                    } else if (token.toString() == ")") {
                        indent--
                        if (indent <= 0) {
                            if (null == parms[define.numparms - 1]) {
                                this.Warning("too few define parameters")
                            }
                            done = 1
                            break
                        }
                    } else if (token.type == Token.TT_NAME) {
                        newdefine = FindHashedDefine(definehash, token.toString())
                        if (newdefine != null) {
                            if (!ExpandDefineIntoSource(token, newdefine)) {
                                return false
                            }
                            continue
                        }
                    }
                    lastcomma = 0
                    if (numparms < define.numparms) {
                        t = idToken(token)
                        t.next = null
                        if (last != null) {
                            last.next = t
                        } else {
                            parms[numparms] = t
                        }
                        last = t
                    }
                }
                numparms++
            }
            return true
        }

        private fun StringizeTokens(tokens: Array<idToken?>, token: idToken): Boolean {
            token.type = Token.TT_STRING
            token.whiteSpaceStart_p = 0
            token.whiteSpaceEnd_p = 0
            val sb = StringBuilder()
            var t: idToken? = tokens[0]
            while (t != null) {
                sb.append(t.data)
                t = t.next
            }
            token.data = sb.toString()
            token.len = token.data.length
            return true
        }

        private fun MergeTokens(t1: idToken, t2: idToken): Boolean {
            // merging of a name with a name or number
            if (t1.type == Token.TT_NAME && (t2.type == Token.TT_NAME || t2.type == Token.TT_NUMBER && t2.subtype and Token.TT_FLOAT == 0)) {
                t1.Append(t2.data)
                return true
            }
            // merging of two strings
            if (t1.type == Token.TT_STRING && t2.type == Token.TT_STRING) {
                t1.Append(t2.data)
                return true
            }
            // merging of two numbers
            if (t1.type == Token.TT_NUMBER && t2.type == Token.TT_NUMBER && t1.subtype and (Token.TT_HEX or Token.TT_BINARY) == 0 && t2.subtype and (Token.TT_HEX or Token.TT_BINARY) == 0 && (t1.subtype and Token.TT_FLOAT == 0
                        || t2.subtype and Token.TT_FLOAT == 0)
            ) {
                t1.Append(t2.data)
                return true
            }
            return false
        }

        @Throws(idException::class)
        private fun ExpandBuiltinDefine(
            defToken: idToken,
            define: define_s,
            firstToken: Array<idToken?>,
            lastToken: Array<idToken?>
        ): Boolean {
            val token: idToken
            /*ID_TIME_T*/
            val curtime: String
            val buf: String //[MAX_STRING_CHARS];
            token = idToken(defToken)
            when (define.builtin) {
                BUILTIN_LINE -> {
                    buf = String.format("%d", defToken.line)
                    token.set(buf)
                    token.intValue = defToken.line.toLong()
                    token.floatValue = defToken.line.toFloat()
                    token.type = Token.TT_NUMBER
                    token.subtype = Token.TT_DECIMAL or Token.TT_INTEGER or Token.TT_VALUESVALID
                    token.line = defToken.line
                    token.linesCrossed = defToken.linesCrossed
                    token.flags = 0
                    firstToken[0] = token
                    lastToken[0] = token
                }

                BUILTIN_FILE -> {
                    token.set(scriptstack!!.GetFileName())
                    token.type = Token.TT_NAME
                    token.subtype = token.Length()
                    token.line = defToken.line
                    token.linesCrossed = defToken.linesCrossed
                    token.flags = 0
                    firstToken[0] = token
                    lastToken[0] = token
                }

                BUILTIN_DATE -> {

//                    t = System.currentTimeMillis();
//                    curtime = ctime( & t);
                    curtime = Date().toString()
                    token.set("\"")
                    token.Append(curtime + 4)
                    token[7] = '\u0000'
                    token.Append(curtime + 20)
                    token[10] = '\u0000'
                    token.Append("\"")
                    //			free(curtime);
                    token.type = Token.TT_STRING
                    token.subtype = token.Length()
                    token.line = defToken.line
                    token.linesCrossed = defToken.linesCrossed
                    token.flags = 0
                    firstToken[0] = token
                    lastToken[0] = token
                }

                BUILTIN_TIME -> {

//                    t = System.currentTimeMillis();
//                    curtime = ctime( & t);
                    curtime = Date().toString()
                    token.set("\"")
                    token.Append(curtime + 11)
                    token[8] = '\u0000'
                    token.Append("\"")
                    //			free(curtime);
                    token.type = Token.TT_STRING
                    token.subtype = token.Length()
                    token.line = defToken.line
                    token.linesCrossed = defToken.linesCrossed
                    token.flags = 0
                    firstToken[0] = token
                    lastToken[0] = token
                }

                BUILTIN_STDC -> {
                    run { this.Warning("__STDC__ not supported\n") }
                    run {
                        firstToken[0] = null
                        lastToken[0] = null
                    }
                }

                else -> {
                    firstToken[0] = null
                    lastToken[0] = null
                }
            }
            return true
        }

        @Throws(idException::class)
        private fun ExpandDefine(
            deftoken: idToken,
            define: define_s,
            firstToken: Array<idToken?>,
            lastToken: Array<idToken?>
        ): Boolean {
            val parms = arrayOfNulls<idToken?>(MAX_DEFINEPARMS)
            var dt: idToken?
            var pt: idToken?
            var t: idToken?
            var t1: idToken?
            var t2: idToken?
            var first: idToken?
            var last: idToken?
            var nextpt: idToken?
            val token = idToken()
            var parmnum: Int
            var i: Int

            // if it is a builtin define
            if (define.builtin != 0) {
                return ExpandBuiltinDefine(deftoken, define, firstToken, lastToken)
            }
            // if the define has parameters
            if (define.numparms != 0) {
                if (!ReadDefineParms(define, parms, MAX_DEFINEPARMS)) {
                    return false
                }
                //#ifdef DEBUG_EVAL
//		for ( i = 0; i < define.numparms; i++ ) {
//			Log_Write("define parms %d:", i);
//			for ( pt = parms[i]; pt; pt = pt.next ) {
//				Log_Write( "%s", pt.c_str() );
//			}
//		}
//#endif //DEBUG_EVAL
            }
            // empty list at first
            first = null
            last = null
            // create a list with tokens of the expanded define
            dt = define.tokens
            while (dt != null) {
                parmnum = -1
                // if the token is a name, it could be a define parameter
                if (dt.type == Token.TT_NAME) {
                    parmnum = FindDefineParm(define, dt.toString())
                }
                // if it is a define parameter
                if (parmnum >= 0) {
                    pt = parms[parmnum]
                    while (pt != null) {
                        t = idToken(pt)
                        //add the token to the list
                        t.next = null
                        if (last != null) {
                            last.next = t
                        } else {
                            first = t
                        }
                        last = t
                        pt = pt.next
                    }
                } else {
                    // if stringizing operator
                    if (dt.toString() == "#") {
                        // the stringizing operator must be followed by a define parameter
                        parmnum = if (dt.next != null) {
                            FindDefineParm(define, dt.next.toString())
                        } else {
                            -1
                        }
                        if (parmnum >= 0) {
                            // step over the stringizing operator
                            dt = dt.next
                            // stringize the define parameter tokens
                            if (!StringizeTokens(parms.copyOfRange(parmnum, parms.size), token)) {
                                this.Error("can't stringize tokens")
                                return false
                            }
                            t = idToken(token)
                            t.line = deftoken.line
                        } else {
                            this.Warning("stringizing operator without define parameter")
                            dt = dt.next
                            continue
                        }
                    } else {
                        t = idToken(dt)
                        t.line = deftoken.line
                    }
                    // add the token to the list
                    t.next = null
                    // the token being read from the define list should use the line number of
// the original file, not the header file
                    t.line = deftoken.line
                    if (last != null) {
                        last.next = t
                    } else {
                        first = t
                    }
                    last = t
                }
                dt = dt!!.next
            }
            // check for the merging operator
            t = first
            while (t != null) {
                if (t.next != null) {
                    // if the merging operator
                    if (t.next.toString() == "##") {
                        t1 = t
                        t2 = t.next!!.next
                        if (t2 != null) {
                            if (!MergeTokens(t1, t2)) {
                                this.Error("can't merge '%s' with '%s'", t1.data, t2.data)
                                return false
                            }
                            //					delete t1.next;
                            t1.next = t2.next
                            if (t2 === last) {
                                last = t1
                            }
                            //					delete t2;
                            continue
                        }
                    }
                }
                t = t.next
            }
            // store the first and last token of the list
            firstToken[0] = first
            lastToken[0] = last
            // free all the parameter tokens
            i = 0
            while (i < define.numparms) {
                pt = parms[i]
                while (pt != null) {
                    nextpt = pt.next
                    pt = nextpt
                }
                i++
            }
            return true
        }

        @Throws(idException::class)
        private fun ExpandDefineIntoSource(deftoken: idToken, define: define_s): Boolean {
            val firstToken = arrayOf<idToken?>(null)
            val lastToken = arrayOf<idToken?>(null)
            if (!ExpandDefine(deftoken, define, firstToken, lastToken)) {
                return false
            }
            // if the define is not empty
            if (firstToken[0] != null && lastToken[0] != null) {
                firstToken[0]!!.linesCrossed += deftoken.linesCrossed
                lastToken[0]!!.next = tokens
                tokens = firstToken[0]
            }
            return true
        }

        private fun AddGlobalDefinesToSource() {
            var define: define_s?
            var newdefine: define_s
            define = globaldefines
            while (define != null) {
                //TODO:check if "define = globaldefines" is correct.
                newdefine = CopyDefine(define)
                AddDefineToHash(newdefine, definehash)
                define = define.next
            }
        }

        private fun CopyDefine(define: define_s): define_s {
            val newdefine: define_s
            var token: idToken?
            var newtoken: idToken
            var lasttoken: idToken?

//	newdefine = (define_t *) Mem_Alloc(sizeof(define_t) + strlen(define.name) + 1);
            newdefine = define_s()
            //copy the define name
//	newdefine.name = (char *) newdefine + sizeof(define_t);
            newdefine.name = define.name
            newdefine.flags = define.flags
            newdefine.builtin = define.builtin
            newdefine.numparms = define.numparms
            //the define is not linked
            newdefine.next = null
            newdefine.hashnext = null
            //copy the define tokens
            newdefine.tokens = null
            lasttoken = null
            token = define.tokens
            while (token != null) {
                newtoken = idToken(token)
                newtoken.next = null
                if (lasttoken != null) {
                    lasttoken.next = newtoken
                } else {
                    newdefine.tokens = newtoken
                }
                lasttoken = newtoken
                token = token.next
            }
            //copy the define parameters
            newdefine.parms = null
            lasttoken = null
            token = define.parms
            while (token != null) {
                newtoken = idToken(token)
                newtoken.next = null
                if (lasttoken != null) {
                    lasttoken.next = newtoken
                } else {
                    newdefine.parms = newtoken
                }
                lasttoken = newtoken
                token = token.next
            }
            return newdefine
        }

        private fun FindHashedDefine(definehash: Array<define_s?>, name: String): define_s? {
            var d: define_s?
            val hash: Int
            hash = PC_NameHash(name)
            d = definehash[hash]
            while (d != null) {
                if (d.name == name) {
                    return d
                }
                d = d.hashnext
            }
            return null
        }

        private fun FindDefineParm(define: define_s, name: String): Int {
            var p: idToken?
            var i: Int
            i = 0
            p = define.parms
            while (p != null) {
                if (p.toString() == name) {
                    return i
                }
                i++
                p = p.next
            }
            return -1
        }

        private fun AddDefineToHash(define: define_s, definehash: Array<define_s?>) {
            val hash: Int
            hash = PC_NameHash(define.name)
            define.hashnext = definehash[hash]
            definehash[hash] = define
        }

        private fun FindDefine(defines: define_s?, name: String?): define_s? {
            var d: define_s?
            d = defines
            while (d != null) {
                if (d.name == name) {
                    return d
                }
                d = d.next
            }
            return null
        }

        private fun CopyFirstDefine(): define_s? {
            var i: Int
            i = 0
            while (i < DEFINEHASHSIZE) {
                if (definehash[i] != null) {
                    return CopyDefine(definehash[i]!!)
                }
                i++
            }
            return null
        }

        @Throws(idException::class)
        private fun Directive_include(): Boolean {
            var script: idLexer?
            val token = idToken()
            val path = idStr()
            if (!ReadSourceToken(token)) {
                this.Error("#include without file name")
                return false
            }
            if (token.linesCrossed > 0) {
                this.Error("#include without file name")
                return false
            }
            if (token.type == Token.TT_STRING) {
                script = idLexer()
                // try relative to the current file
                path.set(scriptstack!!.GetFileName())
                path.StripFilename()
                path.plusAssign("/")
                path.plusAssign(token)
                if (!script.LoadFile(path.toString(), OSPath)) {
                    // try absolute path
                    path.set(token)
                    if (!script.LoadFile(path.toString(), OSPath)) {
                        // try from the include path
                        path.set(includepath.plus(token))
                        if (!script.LoadFile(path.toString(), OSPath)) {
//					delete script;
                            script = null
                        }
                    }
                }
            } else if (token.type == Token.TT_PUNCTUATION && token.toString() == "<") {
                path.set(includepath)
                while (ReadSourceToken(token)) {
                    if (token.linesCrossed > 0) {
                        UnreadSourceToken(token)
                        break
                    }
                    if (token.type == Token.TT_PUNCTUATION && token.toString() == ">") {
                        break
                    }
                    path.plusAssign(token)
                }
                if (token.toString() != ">") {
                    this.Warning("#include missing trailing >")
                }
                if (0 == path.Length()) {
                    this.Error("#include without file name between < >")
                    return false
                }
                if (flags and Lexer.LEXFL_NOBASEINCLUDES != 0) {
                    return true
                }
                script = idLexer()
                if (!script.LoadFile(includepath.plus(path).toString(), OSPath)) {
//			delete script;
                    script = null
                }
            } else {
                this.Error("#include without file name")
                return false
            }
            if (null == script) {
                this.Error("file '%s' not found", path)
                return false
            }
            script.SetFlags(flags)
            script.SetPunctuations(punctuations)
            PushScript(script)
            return true
        }

        @Throws(idException::class)
        private fun Directive_undef(): Boolean {
            val token = idToken()
            var define: define_s?
            var lastdefine: define_s?
            val hash: Int

            //
            if (!ReadLine(token)) {
                this.Error("undef without name")
                return false
            }
            if (token.type != Token.TT_NAME) {
                UnreadSourceToken(token)
                this.Error("expected name but found '%s'", token)
                return false
            }
            hash = PC_NameHash(token.data)
            lastdefine = null
            define = definehash[hash]
            while (define != null) {
                if (token.toString() == define.name) {
                    if (define.flags and DEFINE_FIXED != 0) {
                        this.Warning("can't undef '%s'", token)
                    } else {
                        if (lastdefine != null) {
                            lastdefine.hashnext = define.hashnext
                        } else {
                            definehash[hash] = define.hashnext
                        }
                        FreeDefine(define)
                    }
                    break
                }
                lastdefine = define
                define = define.hashnext
            }
            return true
        }

        @Throws(idException::class)
        private fun Directive_if_def(type: Int): Boolean {
            val token = idToken()
            val d: define_s?
            val skip: Int
            if (!ReadLine(token)) {
                this.Error("#ifdef without name")
                return false
            }
            if (token.type != Token.TT_NAME) {
                UnreadSourceToken(token)
                this.Error("expected name after #ifdef, found '%s'", token)
                return false
            }
            d = FindHashedDefine(definehash, token.toString())
            skip = if (type == INDENT_IFDEF == (d == null)) 1 else 0
            PushIndent(type, skip)
            return true
        }

        @Throws(idException::class)
        private fun Directive_ifdef(): Boolean {
            return Directive_if_def(INDENT_IFDEF)
        }

        @Throws(idException::class)
        private fun Directive_ifndef(): Boolean {
            return Directive_if_def(INDENT_IFNDEF)
        }

        @Throws(idException::class)
        private fun Directive_else(): Boolean {
            val type = CInt()
            val skip = CInt()
            PopIndent(type, skip)
            if (0 == type._val) {
                this.Error("misplaced #else")
                return false
            }
            if (type._val == INDENT_ELSE) {
                this.Error("#else after #else")
                return false
            }
            PushIndent(INDENT_ELSE, if (skip._val == 0) 1 else 0)
            return true
        }

        @Throws(idException::class)
        private fun Directive_endif(): Boolean {
            val type = CInt()
            val skip = CInt()
            PopIndent(type, skip)
            if (0 == type._val) {
                this.Error("misplaced #endif")
                return false
            }
            return true
        }

        fun PC_OperatorPriority(op: Int): Int {
            when (op) {
                Lexer.P_MUL -> return 15
                Lexer.P_DIV -> return 15
                Lexer.P_MOD -> return 15
                Lexer.P_ADD -> return 14
                Lexer.P_SUB -> return 14
                Lexer.P_LOGIC_AND -> return 7
                Lexer.P_LOGIC_OR -> return 6
                Lexer.P_LOGIC_GEQ -> return 12
                Lexer.P_LOGIC_LEQ -> return 12
                Lexer.P_LOGIC_EQ -> return 11
                Lexer.P_LOGIC_UNEQ -> return 11
                Lexer.P_LOGIC_NOT -> return 16
                Lexer.P_LOGIC_GREATER -> return 12
                Lexer.P_LOGIC_LESS -> return 12
                Lexer.P_RSHIFT -> return 13
                Lexer.P_LSHIFT -> return 13
                Lexer.P_BIN_AND -> return 10
                Lexer.P_BIN_OR -> return 8
                Lexer.P_BIN_XOR -> return 9
                Lexer.P_BIN_NOT -> return 16
                Lexer.P_COLON -> return 5
                Lexer.P_QUESTIONMARK -> return 5
            }
            return 0
        }

        @Throws(idException::class)
        fun AllocValue(newVal: value_s?, value_heap: Array<value_s?>, numvalues: IntArray): Boolean {
            var newVal = newVal
            var error = false
            if (numvalues[0] >= MAX_VALUES) {
                this.Error("out of value space\n")
                error = true
            } else {
                newVal = value_heap[numvalues[0]++]
            }
            return error
        }

        @Throws(idException::class)
        fun AllocOperator(op: operator_s?, operator_heap: Array<operator_s?>, numoperators: IntArray): Boolean {
            var op = op
            var error = false
            if (numoperators[0] >= MAX_OPERATORS) {
                this.Error("out of operator space\n")
                error = true
            } else {
                op = operator_heap[numoperators[0]++]
            }
            return error
        }

        @Throws(idException::class)
        private fun EvaluateTokens(
            tokens: idToken?,
            intValue: CInt,
            floatValue: CFloat,
            integer: Int
        ): Boolean {
            var o: operator_s? = operator_s()
            var firstOperator: operator_s?
            var lastOperator: operator_s?
            var v: value_s? = value_s()
            var firstValue: value_s?
            var lastValue: value_s?
            var v1: value_s
            var v2: value_s
            var t: idToken?
            var brace = false
            var parentheses = 0
            var error = false
            var lastwasvalue = false
            var negativevalue = false
            var questmarkintvalue = false
            var questmarkfloatvalue = 0.0f
            var gotquestmarkvalue = false
            //
            val operator_heap = arrayOfNulls<operator_s?>(MAX_OPERATORS)
            val numoperators = IntArray(1)
            val value_heap = arrayOfNulls<value_s?>(MAX_VALUES)
            val numvalues = IntArray(1)
            lastOperator = null
            firstOperator = lastOperator
            lastValue = null
            firstValue = lastValue
            intValue._val = 0
            floatValue._val = 0.0f
            t = tokens
            while (t != null) {
                when (t.type) {
                    Token.TT_NAME -> {
                        if (lastwasvalue || negativevalue) {
                            this.Error("syntax error in #if/#elif")
                            error = true
                            break
                        }
                        if (t.toString() != "defined") {
                            this.Error("undefined name '%s' in #if/#elif", t)
                            error = true
                            break
                        }
                        t = t.next
                        if (t.toString() == "(") {
                            brace = true
                            t = t!!.next
                        }
                        if (null == t || t.type != Token.TT_NAME) {
                            this.Error("defined() without name in #if/#elif")
                            error = true
                            break
                        }
                        //v = (value_t *) GetClearedMemory(sizeof(value_t));
                        error = AllocValue(v, value_heap, numvalues)
                        if (FindHashedDefine(definehash, t.toString()) != null) {
                            v!!.intValue = 1
                            v.floatValue = 1.0f
                        } else {
                            v!!.intValue = 0
                            v.floatValue = 0.0f
                        }
                        v.parentheses = parentheses
                        v.next = null
                        v.prev = lastValue
                        if (lastValue != null) {
                            lastValue.next = v
                        } else {
                            firstValue = v
                        }
                        lastValue = v
                        if (brace) {
                            t = t.next
                            if (null == t || t.toString() != ")") {
                                this.Error("defined missing ) in #if/#elif")
                                error = true
                                break
                            }
                        }
                        brace = false
                        // defined() creates a value
                        lastwasvalue = true
                    }

                    Token.TT_NUMBER -> {
                        if (lastwasvalue) {
                            this.Error("syntax error in #if/#elif")
                            error = true
                            break
                        }
                        //v = (value_t *) GetClearedMemory(sizeof(value_t));
                        error = AllocValue(v, value_heap, numvalues)
                        if (negativevalue) {
                            v!!.intValue = -t.GetIntValue()
                            v.floatValue = -t.GetFloatValue()
                        } else {
                            v!!.intValue = t.GetIntValue()
                            v.floatValue = t.GetFloatValue()
                        }
                        v.parentheses = parentheses
                        v.next = null
                        v.prev = lastValue
                        if (lastValue != null) {
                            lastValue.next = v
                        } else {
                            firstValue = v
                        }
                        lastValue = v
                        //last token was a value
                        lastwasvalue = true
                        //
                        negativevalue = false
                    }

                    Token.TT_PUNCTUATION -> {
                        if (negativevalue) {
                            this.Error("misplaced minus sign in #if/#elif")
                            error = true
                        } else if (t.subtype == Lexer.P_PARENTHESESOPEN) {
                            parentheses++
                        } else if (t.subtype == Lexer.P_PARENTHESESCLOSE) {
                            parentheses--
                            if (parentheses < 0) {
                                this.Error("too many ) in #if/#elsif")
                                error = true
                            }
                        } else {
                            //check for invalid operators on floating point values
                            if (0 == integer) {
                                if (t.subtype == Lexer.P_BIN_NOT || t.subtype == Lexer.P_MOD || t.subtype == Lexer.P_RSHIFT || t.subtype == Lexer.P_LSHIFT || t.subtype == Lexer.P_BIN_AND || t.subtype == Lexer.P_BIN_OR || t.subtype == Lexer.P_BIN_XOR) {
                                    this.Error("illigal operator '%s' on floating point operands\n", t)
                                    error = true
                                }
                            }
                            if (!error) {
                                // In C++, P_SUB with !lastwasvalue breaks out of the inner switch
                                // (sets negativevalue, does NOT create an operator).
                                // With lastwasvalue, P_SUB falls through to the operator cases.
                                if (t.subtype == Lexer.P_SUB && !lastwasvalue) {
                                    negativevalue = true
                                } else {
                                    when (t.subtype) {
                                        Lexer.P_LOGIC_NOT, Lexer.P_BIN_NOT -> {
                                            if (lastwasvalue) {
                                                this.Error("! or ~ after value in #if/#elif")
                                                error = true
                                            }
                                        }

                                        Lexer.P_INC, Lexer.P_DEC -> {
                                            this.Error("++ or -- used in #if/#elif")
                                        }

                                        // P_SUB reaches here only when lastwasvalue is true (binary minus),
                                        // matching C++ fall-through from case P_SUB to the operator cases
                                        Lexer.P_SUB, Lexer.P_MUL, Lexer.P_DIV, Lexer.P_MOD, Lexer.P_ADD, Lexer.P_LOGIC_AND, Lexer.P_LOGIC_OR, Lexer.P_LOGIC_GEQ, Lexer.P_LOGIC_LEQ, Lexer.P_LOGIC_EQ, Lexer.P_LOGIC_UNEQ, Lexer.P_LOGIC_GREATER, Lexer.P_LOGIC_LESS, Lexer.P_RSHIFT, Lexer.P_LSHIFT, Lexer.P_BIN_AND, Lexer.P_BIN_OR, Lexer.P_BIN_XOR, Lexer.P_COLON, Lexer.P_QUESTIONMARK -> {
                                            if (!lastwasvalue) {
                                                this.Error("operator '%s' after operator in #if/#elif", t)
                                                error = true
                                            }
                                        }

                                        else -> {
                                            this.Error("invalid operator '%s' in #if/#elif", t)
                                            error = true
                                        }
                                    }
                                }
                                if (!error && !negativevalue) {
                                    //o = (operator_t *) GetClearedMemory(sizeof(operator_t));
                                    error = AllocOperator(o, operator_heap, numoperators)
                                    o!!.op = t.subtype
                                    o.priority = PC_OperatorPriority(t.subtype)
                                    o.parentheses = parentheses
                                    o.next = null
                                    o.prev = lastOperator
                                    if (lastOperator != null) {
                                        lastOperator.next = o
                                    } else {
                                        firstOperator = o
                                    }
                                    lastOperator = o
                                    lastwasvalue = false
                                }
                            }
                        }
                    }

                    else -> {
                        this.Error("unknown '%s' in #if/#elif", t)
                        error = true
                    }
                }
                if (error) {
                    break
                }
                t = t.next
            }
            if (!error) {
                if (!lastwasvalue) {
                    this.Error("trailing operator in #if/#elif")
                    error = true
                } else if (parentheses != 0) {
                    this.Error("too many ( in #if/#elif")
                    error = true
                }
            }
            //
            gotquestmarkvalue = false
            questmarkintvalue = false
            questmarkfloatvalue = 0.0f
            //while there are operators
            while (!error && firstOperator != null) {
                v = firstValue
                o = firstOperator
                while (o!!.next != null) {

                    //if the current operator is nested deeper in parentheses
                    //than the next operator
                    if (o.parentheses > o.next!!.parentheses) {
                        break
                    }
                    //if the current and next operator are nested equally deep in parentheses
                    if (o.parentheses == o.next!!.parentheses) {
                        //if the priority of the current operator is equal or higher
                        //than the priority of the next operator
                        if (o.priority >= o.next!!.priority) {
                            break
                        }
                    }
                    //if the arity of the operator isn't equal to 1
                    if (o.op != Lexer.P_LOGIC_NOT && o.op != Lexer.P_BIN_NOT) {
                        v = v!!.next
                    }
                    //if there's no value or no next value
                    if (null == v) {
                        this.Error("mising values in #if/#elif")
                        error = true
                        break
                    }
                    o = o.next
                }
                if (error) {
                    break
                }
                v1 = v!!
                v2 = v.next!!
                when (o.op) {
                    Lexer.P_LOGIC_NOT -> {
                        v1.intValue = (if (0 == v1.intValue) 1 else 0)
                        v1.floatValue = (if (0.0f == v1.floatValue) 1.0f else 0.0f)
                    }

                    Lexer.P_BIN_NOT -> v1.intValue = v1.intValue.inv()
                    Lexer.P_MUL -> {
                        v1.intValue *= v2.intValue
                        v1.floatValue *= v2.floatValue
                    }

                    Lexer.P_DIV -> {
                        if (0 == v2.intValue || 0.0f == v2.floatValue) {
                            this.Error("divide by zero in #if/#elif\n")
                            error = true
                            break
                        }
                        v1.intValue /= v2.intValue
                        v1.floatValue /= v2.floatValue
                    }

                    Lexer.P_MOD -> {
                        if (0 == v2.intValue) {
                            this.Error("divide by zero in #if/#elif\n")
                            error = true
                            break
                        }
                        v1.intValue %= v2.intValue
                    }

                    Lexer.P_ADD -> {
                        v1.intValue += v2.intValue
                        v1.floatValue += v2.floatValue
                    }

                    Lexer.P_SUB -> {
                        v1.intValue -= v2.intValue
                        v1.floatValue -= v2.floatValue
                    }

                    Lexer.P_LOGIC_AND -> {
                        v1.intValue = if (v1.intValue != 0 && v2.intValue != 0) 1 else 0
                        v1.floatValue = if (v1.floatValue != 0.0f && v2.floatValue != 0.0f) 1.0f else 0.0f
                    }

                    Lexer.P_LOGIC_OR -> {
                        v1.intValue = if (v1.intValue != 0 || v2.intValue != 0) 1 else 0
                        v1.floatValue = if (v1.floatValue != 0.0f || v2.floatValue != 0.0f) 1.0f else 0.0f
                    }

                    Lexer.P_LOGIC_GEQ -> {
                        v1.intValue = if (v1.intValue >= v2.intValue) 1 else 0
                        v1.floatValue = if (v1.floatValue >= v2.floatValue) 1.0f else 0.0f
                    }

                    Lexer.P_LOGIC_LEQ -> {
                        v1.intValue = if (v1.intValue <= v2.intValue) 1 else 0
                        v1.floatValue = if (v1.floatValue <= v2.floatValue) 1.0f else 0.0f
                    }

                    Lexer.P_LOGIC_EQ -> {
                        v1.intValue = if (v1.intValue == v2.intValue) 1 else 0
                        v1.floatValue = if (v1.floatValue == v2.floatValue) 1.0f else 0.0f
                    }

                    Lexer.P_LOGIC_UNEQ -> {
                        v1.intValue = if (v1.intValue != v2.intValue) 1 else 0
                        v1.floatValue = if (v1.floatValue != v2.floatValue) 1.0f else 0.0f
                    }

                    Lexer.P_LOGIC_GREATER -> {
                        v1.intValue = if (v1.intValue > v2.intValue) 1 else 0
                        v1.floatValue = if (v1.floatValue > v2.floatValue) 1.0f else 0.0f
                    }

                    Lexer.P_LOGIC_LESS -> {
                        v1.intValue = if (v1.intValue < v2.intValue) 1 else 0
                        v1.floatValue = if (v1.floatValue < v2.floatValue) 1.0f else 0.0f
                    }

                    Lexer.P_RSHIFT -> v1.intValue = v1.intValue shr v2.intValue
                    Lexer.P_LSHIFT -> v1.intValue = v1.intValue shl v2.intValue
                    Lexer.P_BIN_AND -> v1.intValue = v1.intValue and v2.intValue
                    Lexer.P_BIN_OR -> v1.intValue = v1.intValue or v2.intValue
                    Lexer.P_BIN_XOR -> v1.intValue = v1.intValue xor v2.intValue
                    Lexer.P_COLON -> {
                        if (!gotquestmarkvalue) {
                            this.Error(": without ? in #if/#elif")
                            error = true
                            break
                        }
                        if (integer != 0) {
                            if (!questmarkintvalue) {
                                v1.intValue = v2.intValue
                            }
                        } else {
                            if (0.0f == questmarkfloatvalue) {
                                v1.floatValue = v2.floatValue
                            }
                        }
                        gotquestmarkvalue = false
                    }

                    Lexer.P_QUESTIONMARK -> {
                        if (gotquestmarkvalue) {
                            this.Error("? after ? in #if/#elif")
                            error = true
                            break
                        }
                        questmarkintvalue = v1.intValue != 0
                        questmarkfloatvalue = v1.floatValue
                        gotquestmarkvalue = true
                    }
                }
                // #ifdef DEBUG_EVAL
                // if (integer) Log_Write("result value = %d", v1.intvalue);
                // else Log_Write("result value = %f", v1.floatvalue);
// #endif //DEBUG_EVAL
                if (error) {
                    break
                }
                //                lastoperatortype = o.op;
                //if not an operator with arity 1
                if (o.op != Lexer.P_LOGIC_NOT && o.op != Lexer.P_BIN_NOT) {
                    //remove the second value if not question mark operator
                    if (o.op != Lexer.P_QUESTIONMARK) {
                        v = v.next
                    }
                    //
                    if (v!!.prev != null) {
                        v.prev!!.next = v.next
                    } else {
                        firstValue = v.next
                    }
                    if (v.next != null) {
                        v.next!!.prev = v.prev
                    } else {
                        lastValue = v.prev
                    }
                    //FreeMemory(v);
//                    FreeValue(v);//TODO:does this macro do anytihng?
                }
                //remove the operator
                if (o.prev != null) {
                    o.prev!!.next = o.next
                } else {
                    firstOperator = o.next
                }
                if (o.next != null) {
                    o.next!!.prev = o.prev
                } else {
                    lastOperator = o.prev
                }
                //FreeMemory(o);
//                FreeOperator(o);//TODO:see above
            }
            if (firstValue != null) {
                if (intValue._val != 0) {
                    intValue._val = firstValue.intValue
                }
                if (floatValue._val != 0.0f) {
                    floatValue._val = firstValue.floatValue
                }
            }
            o = firstOperator
            while (o != null) {
                lastOperator = o.next
                o = lastOperator
            }
            v = firstValue
            while (v != null) {
                lastValue = v.next
                v = lastValue
            }
            if (!error) {
                return true
            }
            if (intValue._val != 0) {
                intValue._val = 0
            }
            if (floatValue._val != 0.0f) {
                floatValue._val = 0.0f
            }
            return false
        }

        @Throws(idException::class)
        private fun Evaluate(intvalue: CInt, floatvalue: CFloat, integer: Int): Boolean {
            val token = idToken()
            var firstToken: idToken?
            var lastToken: idToken?
            var t: idToken
            var define: define_s?
            var defined = false
            intvalue._val = 0
            floatvalue._val = 0.0f
            //
            if (!ReadLine(token)) {
                this.Error("no value after #if/#elif")
                return false
            }
            firstToken = null
            lastToken = null
            do {
                //if the token is a name
                if (token.type == Token.TT_NAME) {
                    if (defined) {
                        defined = false
                        t = idToken(token)
                        t.next = null
                        if (lastToken != null) {
                            lastToken.next = t
                        } else {
                            firstToken = t
                        }
                        lastToken = t
                    } else if (token.toString() == "defined") {
                        defined = true
                        t = idToken(token)
                        t.next = null
                        if (lastToken != null) {
                            lastToken.next = t
                        } else {
                            firstToken = t
                        }
                        lastToken = t
                    } else {
                        //then it must be a define
                        define = FindHashedDefine(definehash, token.toString())
                        if (null == define) {
                            this.Error("can't Evaluate '%s', not defined", token)
                            return false
                        }
                        if (!ExpandDefineIntoSource(token, define)) {
                            return false
                        }
                    }
                } //if the token is a number or a punctuation
                else if (token.type == Token.TT_NUMBER || token.type == Token.TT_PUNCTUATION) {
                    t = idToken(token)
                    t.next = null
                    if (lastToken != null) {
                        lastToken.next = t
                    } else {
                        firstToken = t
                    }
                    lastToken = t
                } else {
                    this.Error("can't Evaluate '%s'", token)
                    return false
                }
            } while (ReadLine(token))
            //
            return EvaluateTokens(firstToken, intvalue, floatvalue, integer)
            //            //
//// #ifdef DEBUG_EVAL
//            // Log_Write("eval:");
//// #endif //DEBUG_EVAL
//            for (t = firsttoken; t != null; t = nexttoken) {
//// #ifdef DEBUG_EVAL
//                // Log_Write(" %s", t.c_str());
//// #endif //DEBUG_EVAL
//                nexttoken = t.next;
////		delete t;
//            } //end for
//// #ifdef DEBUG_EVAL
//            // if (integer) Log_Write("eval result: %d", *intvalue);
//            // else Log_Write("eval result: %f", *floatvalue);
//// #endif //DEBUG_EVAL
//            //
        }

        @Throws(idException::class)
        private fun DollarEvaluate(intValue: CInt, floatValue: CFloat, integer: Int): Boolean {
            var indent: Int
            var defined = false
            val token = idToken()
            var firstToken: idToken?
            var lasttoken: idToken?
            var t: idToken
            var define: define_s?
            intValue._val = 0
            floatValue._val = 0.0f
            //
            if (!ReadSourceToken(token)) {
                this.Error("no leading ( after \$evalint/\$evalfloat")
                return false
            }
            if (!ReadSourceToken(token)) {
                this.Error("nothing to Evaluate")
                return false
            }
            indent = 1
            firstToken = null
            lasttoken = null
            do {
                //if the token is a name
                if (token.type == Token.TT_NAME) {
                    if (defined) {
                        defined = false
                        t = idToken(token)
                        t.next = null
                        if (lasttoken != null) {
                            lasttoken.next = t
                        } else {
                            firstToken = t
                        }
                        lasttoken = t
                    } else if (token.toString() == "defined") {
                        defined = true
                        t = idToken(token)
                        t.next = null
                        if (lasttoken != null) {
                            lasttoken.next = t
                        } else {
                            firstToken = t
                        }
                        lasttoken = t
                    } else {
                        //then it must be a define
                        define = FindHashedDefine(definehash, token.toString())
                        if (null == define) {
                            this.Warning("can't Evaluate '%s', not defined", token)
                            return false
                        }
                        if (!ExpandDefineIntoSource(token, define)) {
                            return false
                        }
                    }
                } //if the token is a number or a punctuation
                else if (token.type == Token.TT_NUMBER || token.type == Token.TT_PUNCTUATION) {
                    if (token[0] == '(') {
                        indent++
                    } else if (token[0] == ')') {
                        indent--
                    }
                    if (indent <= 0) {
                        break
                    }
                    t = idToken(token)
                    t.next = null
                    if (lasttoken != null) {
                        lasttoken.next = t
                    } else {
                        firstToken = t
                    }
                    lasttoken = t
                } else {
                    this.Error("can't Evaluate '%s'", token)
                    return false
                }
            } while (ReadSourceToken(token))
            //
            return EvaluateTokens(firstToken, intValue, floatValue, integer)
            // //
// // #ifdef DEBUG_EVAL
            // // Log_Write("$eval:");
// // #endif //DEBUG_EVAL
            // for (t = firsttoken; t; t = nexttoken) {
// // #ifdef DEBUG_EVAL
            // // Log_Write(" %s", t.c_str());
// // #endif //DEBUG_EVAL
            // nexttoken = t.next;
            // delete t;
            // } //end for
// // #ifdef DEBUG_EVAL
            // // if (integer) Log_Write("$eval result: %d", *intvalue);
            // // else Log_Write("$eval result: %f", *floatvalue);
// // #endif //DEBUG_EVAL
            // //
        }

        @Throws(idException::class)
        private fun Directive_define(): Boolean {
            val token = idToken()
            var t: idToken
            var last: idToken?
            var define: define_s?
            if (!ReadLine(token)) {
                this.Error("#define without name")
                return false
            }
            if (token.type != Token.TT_NAME) {
                UnreadSourceToken(token)
                this.Error("expected name after #define, found '%s'", token)
                return false
            }
            // check if the define already exists
            define = FindHashedDefine(definehash, token.toString())
            if (define != null) {
                if (define.flags and DEFINE_FIXED != 0) {
                    this.Error("can't redefine '%s'", token)
                    return false
                }
                this.Warning("redefinition of '%s'", token)
                // unread the define name before executing the #undef directive
                UnreadSourceToken(token)
                if (!Directive_undef()) {
                    return false
                }
                // if the define was not removed (define.flags & DEFINE_FIXED)
                define = FindHashedDefine(definehash, token.toString())
            }
            // allocate define
//	define = (define_t *) Mem_ClearedAlloc(sizeof(define_t) + token.Length() + 1);
            define = define_s()
            //	define.name = (char *) define + sizeof(define_t);
            define.name = token.data
            // add the define to the source
            AddDefineToHash(define, definehash)
            // if nothing is defined, just return
            if (!ReadLine(token)) {
                return true
            }
            // if it is a define with parameters
            if (!token.WhiteSpaceBeforeToken() && token.toString() == "(") {
                // read the define parameters
                last = null
                if (!CheckTokenString(")")) {
                    while (true) {
                        if (!ReadLine(token)) {
                            this.Error("expected define parameter")
                            return false
                        }
                        // if it isn't a name
                        if (token.type != Token.TT_NAME) {
                            this.Error("invalid define parameter")
                            return false
                        }
                        if (FindDefineParm(define, token.toString()) >= 0) {
                            this.Error("two the same define parameters")
                            return false
                        }
                        // add the define parm
                        t = idToken(token)
                        t.ClearTokenWhiteSpace()
                        t.next = null
                        if (last != null) {
                            last.next = t
                        } else {
                            define.parms = t
                        }
                        last = t
                        define.numparms++
                        // read next token
                        if (!ReadLine(token)) {
                            this.Error("define parameters not terminated")
                            return false
                        }
                        if (token.toString() == ")") {
                            break
                        }
                        // then it must be a comma
                        if (token.toString() != ",") {
                            this.Error("define not terminated")
                            return false
                        }
                    }
                }
                if (!ReadLine(token)) {
                    return true
                }
            }
            // read the defined stuff
            last = null
            do {
                t = idToken(token)
                if (t.type == Token.TT_NAME && t.toString() == define.name) {
                    t.flags = t.flags or TOKEN_FL_RECURSIVE_DEFINE
                    this.Warning("recursive define (removed recursion)")
                }
                t.ClearTokenWhiteSpace()
                t.next = null
                if (last != null) {
                    last.next = t
                } else {
                    define.tokens = t
                }
                last = t
            } while (ReadLine(token))
            if (last != null) {
                // check for merge operators at the beginning or end
                if (define.tokens.toString() == "##" || last.toString() == "##") {
                    this.Error("define with misplaced ##")
                    return false
                }
            }
            return true
        }

        @Throws(idException::class)
        private fun Directive_elif(): Boolean {
            val value = CInt()
            val type = CInt()
            val skip = CInt()
            PopIndent(type, skip)
            if (type._val == INDENT_ELSE) {
                this.Error("misplaced #elif")
                return false
            }
            // TODO: check if sending an empty CFloat is OK
            if (!Evaluate(value, CFloat(), 1)) {
                return false
            }
            skip._val = if (value._val == 0) 1 else 0
            PushIndent(INDENT_ELIF, skip._val)
            return true
        }

        @Throws(idException::class)
        private fun Directive_if(): Boolean {
            val value = CInt()
            val skip = CInt()
            if (!Evaluate(value, CFloat(), 1)) {
                return false
            }
            skip._val = if (value._val == 0) 1 else 0
            PushIndent(INDENT_IF, skip._val)
            return true
        }

        @Throws(idException::class)
        private fun Directive_line(): Boolean {
            val token = idToken()
            this.Error("#line directive not supported")
            while (ReadLine(token)) {
                //TODO:??
            }
            return true
        }

        @Throws(idException::class)
        private fun Directive_error(): Boolean {
            val token = idToken()
            if (!ReadLine(token) || token.type != Token.TT_STRING) {
                this.Error("#error without string")
                return false
            }
            this.Error("#error: %s", token)
            return true
        }

        @Throws(idException::class)
        private fun Directive_warning(): Boolean {
            val token = idToken()
            if (!ReadLine(token) || token.type != Token.TT_STRING) {
                this.Error("#warning without string")
                return false
            }
            this.Error("#warning: %s", token)
            return true
        }

        @Throws(idException::class)
        private fun Directive_pragma(): Boolean {
            val token = idToken()
            this.Warning("#pragma directive not supported")
            while (ReadLine(token)) {
                //TODO::???
            }
            return true
        }

        private fun UnreadSignToken() {
            val token = idToken()
            token.line = scriptstack!!.GetLineNum()
            token.whiteSpaceStart_p = 0
            token.whiteSpaceEnd_p = 0
            token.linesCrossed = 0
            token.flags = 0
            token.set("-")
            token.type = Token.TT_PUNCTUATION
            token.subtype = Lexer.P_SUB
            UnreadSourceToken(token)
        }

        @Throws(idException::class)
        private fun Directive_eval(): Boolean {
            val value = CInt()
            val token = idToken()
            val buf: String //[128];
            if (!Evaluate(value, CFloat(), 1)) {
                return false
            }
            token.line = scriptstack!!.GetLineNum()
            token.whiteSpaceStart_p = 0
            token.whiteSpaceEnd_p = 0
            token.linesCrossed = 0
            token.flags = 0
            buf = String.format("%d", abs(value._val))
            token.set(buf)
            token.type = Token.TT_NUMBER
            token.subtype = Token.TT_INTEGER or Token.TT_LONG or Token.TT_DECIMAL
            UnreadSourceToken(token)
            if (value._val < 0) {
                UnreadSignToken()
            }
            return true
        }

        @Throws(idException::class)
        private fun Directive_evalfloat(): Boolean {
            val value = CFloat()
            val token = idToken()
            val buf: String //[128];
            if (!Evaluate(CInt(), value, 1)) {
                return false
            }
            token.line = scriptstack!!.GetLineNum()
            token.whiteSpaceStart_p = 0
            token.whiteSpaceEnd_p = 0
            token.linesCrossed = 0
            token.flags = 0
            buf = String.format("%1.2f", abs(value._val))
            token.set(buf)
            token.type = Token.TT_NUMBER
            token.subtype = Token.TT_FLOAT or Token.TT_LONG or Token.TT_DECIMAL
            UnreadSourceToken(token)
            if (value._val < 0) {
                UnreadSignToken()
            }
            return true
        }

        @Throws(idException::class)
        private fun ReadDirective(): Boolean {
            val token = idToken()

            //read the directive name
            if (!ReadSourceToken(token)) {
                this.Error("found '#' without name")
                return false
            }
            //directive name must be on the same line
            if (token.linesCrossed > 0) {
                UnreadSourceToken(token)
                this.Error("found '#' at end of line")
                return false
            }
            //if if is a name
            if (token.type == Token.TT_NAME) {
                if (token.toString() == "ifdef") {
                    return Directive_ifdef()
                } else if (token.toString() == "ifndef") {
                    return Directive_ifndef()
                } else if (token.toString() == "if") { //token.equals() is overriden to startsWith.
                    return Directive_if()
                } else if (token.toString() == "elif") {
                    return Directive_elif()
                } else if (token.toString() == "else") {
                    return Directive_else()
                } else if (token.toString() == "endif") {
                    return Directive_endif()
                } else if (skip > 0) {
                    // skip the rest of the line
                    while (ReadLine(token)) {
                    }
                    return true
                } else {
                    when (token.toString()) {
                        "include" -> return Directive_include()
                        "define" -> return Directive_define()
                        "undef" -> return Directive_undef()
                        "line" -> return Directive_line()
                        "error" -> return Directive_error()
                        "warning" -> return Directive_warning()
                        "pragma" -> return Directive_pragma()
                        "eval" -> return Directive_eval()
                        "evalfloat" -> return Directive_evalfloat()
                    }
                }
            }
            this.Error("unknown precompiler directive '%s'", token)
            return false
        }

        @Throws(idException::class)
        private fun DollarDirective_evalint(): Boolean {
            val value = CInt()
            val token = idToken()
            val buf: String //[128];
            if (!DollarEvaluate(value, CFloat(), 1)) {
                return false
            }
            token.line = scriptstack!!.GetLineNum()
            token.whiteSpaceStart_p = 0
            token.whiteSpaceEnd_p = 0
            token.linesCrossed = 0
            token.flags = 0
            buf = String.format("%d", abs(value._val))
            token.set(buf)
            token.type = Token.TT_NUMBER
            token.subtype = Token.TT_INTEGER or Token.TT_LONG or Token.TT_DECIMAL or Token.TT_VALUESVALID
            token.intValue = abs(value._val).toLong()
            token.floatValue = abs(value._val.toFloat())
            UnreadSourceToken(token)
            if (value._val < 0) {
                UnreadSignToken()
            }
            return true
        }

        @Throws(idException::class)
        private fun DollarDirective_evalfloat(): Boolean {
            val value = CFloat()
            val token = idToken()
            val buf: String //[128];
            if (!DollarEvaluate(CInt(), value, 1)) {
                return false
            }
            token.line = scriptstack!!.GetLineNum()
            token.whiteSpaceStart_p = 0
            token.whiteSpaceEnd_p = 0
            token.linesCrossed = 0
            token.flags = 0
            buf = String.format("%1.2f", abs(value._val))
            token.set(buf)
            token.type = Token.TT_NUMBER
            token.subtype = Token.TT_FLOAT or Token.TT_LONG or Token.TT_DECIMAL or Token.TT_VALUESVALID
            token.intValue = abs(value._val).toLong()
            token.floatValue = abs(value._val)
            UnreadSourceToken(token)
            if (value._val < 0) {
                UnreadSignToken()
            }
            return true
        }

        @Throws(idException::class)
        private fun ReadDollarDirective(): Boolean {
            val token = idToken()

            // read the directive name
            if (!ReadSourceToken(token)) {
                this.Error("found '$' without name")
                return false
            }
            // directive name must be on the same line
            if (token.linesCrossed > 0) {
                UnreadSourceToken(token)
                this.Error("found '$' at end of line")
                return false
            }
            // if if is a name
            if (token.type == Token.TT_NAME) {
                if (token.toString() == "evalint") {
                    return DollarDirective_evalint()
                } else if (token.toString() == "evalfloat") {
                    return DollarDirective_evalfloat()
                }
            }
            UnreadSourceToken(token)
            return false
        }

        /*
         ================
         idParser::EvaluateTokens
         ================
         */
        inner class operator_s {
            var op = 0
            var parentheses = 0
            var prev: operator_s? = null
            var next: operator_s? = null
            var priority = 0
        }

        inner class value_s {
            var floatValue = 0.0f
            var intValue: Int = 0
            var parentheses = 0
            var prev: value_s? = null
            var next: value_s? = null
        }

        companion object {
            const val MAX_OPERATORS = 64
            const val MAX_VALUES = 64

            //
            private var globaldefines // list with global defines added to every source loaded
                    : define_s? = null

            // add a global define that will be added to all opened sources
            @Throws(idException::class)
            fun AddGlobalDefine(string: String): Boolean {
                val define: define_s?
                define = DefineFromString(string)
                if (null == define) {
                    return false
                }
                define.next = globaldefines //TODO:check if [0] is correcto.
                globaldefines = define
                return true
            }

            // remove the given global define
            fun RemoveGlobalDefine(name: String): Boolean {
                var d: define_s?
                var prev: define_s?
                prev = null
                d = globaldefines
                while (d != null) {
                    if (d.name == name) {
                        break
                    }
                    prev = d
                    d = d.next
                }
                if (d != null) {
                    if (prev != null) {
                        prev.next = d.next
                    } else {
                        globaldefines = d.next
                    }
                    FreeDefine(d)
                    return true
                }
                return false
            }

            // remove all global defines
            fun RemoveAllGlobalDefines() {
                var define: define_s?
                define = globaldefines
                while (define != null) {
                    globaldefines = globaldefines!!.next //TODO:ptr
                    FreeDefine(define)
                    define = globaldefines
                }
            }

            // set the base folder to load files from
            fun SetBaseFolder(path: String) {
                idLexer.SetBaseFolder(path)
            }

            @Throws(idException::class)
            private fun PrintDefine(define: define_s) {
                idLib.common.Printf("define->name = %s\n", define.name)
                idLib.common.Printf("define->flags = %d\n", define.flags)
                idLib.common.Printf("define->builtin = %d\n", define.builtin)
                idLib.common.Printf("define->numparms = %d\n", define.numparms)
            }

            private fun FreeDefine(define: define_s) {

                //free the define parameters
//            for (t = define.parms; t; t = next) {
//                next = t.next;
//		delete t;
//            }
                //free the define tokens
//            for (t = define.tokens; t; t = next) {
//                next = t.next;
//		delete t;
//            }
                define.tokens = null
                define.parms = define.tokens //TODO:check if nullifying doesn't break nothing.
                //free the define
//            Mem_Free(define);
            }

            @Throws(idException::class)
            private fun DefineFromString(string: String): define_s? {
                val src = idParser()
                val def: define_s?
                if (!src.LoadMemory(string, string.length, "*defineString")) {
                    return null
                }
                // create a define from the source
                if (!src.Directive_define()) {
                    src.FreeSource()
                    return null
                }
                def = src.CopyFirstDefine()
                src.FreeSource()
                //if the define was created succesfully
                return def
            }
        }
    }
}
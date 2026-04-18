package neo.idlib.Text

import neo.TempDump
import neo.framework.File_h.idFile
import neo.idlib.BIT
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Token.idToken
import neo.idlib.idException
import neo.idlib.idLib
import neo.idlib.math.*
import neo.idlib.math.Matrix.idMat3
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.util.*

object Lexer {
    val LEXFL_ALLOWBACKSLASHSTRINGCONCAT: Int =
        BIT(12) // allow multiple strings seperated by '\' to be concatenated
    val LEXFL_ALLOWFLOATEXCEPTIONS: Int =
        BIT(10) // allow float exceptions like 1.#INF or 1.#IND to be parsed
    val LEXFL_ALLOWIPADDRESSES: Int = BIT(9) // allow ip addresses to be parsed as numbers
    val LEXFL_ALLOWMULTICHARLITERALS: Int = BIT(11) // allow multi character literals
    val LEXFL_ALLOWNUMBERNAMES: Int = BIT(8) // allow names to start with a number
    val LEXFL_ALLOWPATHNAMES: Int = BIT(7) // allow path seperators in names
    val LEXFL_NOBASEINCLUDES: Int = BIT(6) // don't include files embraced with < >
    val LEXFL_NODOLLARPRECOMPILE: Int = BIT(5) // don't use the $ sign for precompilation

    /**
     * ===============================================================================
     *
     *
     * Lexicographical parser
     *
     *
     * Does not use memory allocation during parsing. The lexer uses no memory
     * allocation if a source is loaded with LoadMemory(). However, idToken may
     * still allocate memory for large strings.
     *
     *
     * A number directly following the escape character '\' in a string is
     * assumed to be in decimal format instead of octal. Binary numbers of the
     * form 0b.. or 0B.. can also be used.
     *
     *
     * ===============================================================================
     */
    // lexer flags
    val LEXFL_NOERRORS: Int = BIT(0) // don't print any errors


    val LEXFL_NOFATALERRORS: Int = BIT(2) // errors aren't fatal


    val LEXFL_NOSTRINGCONCAT: Int =
        BIT(3) // multiple strings seperated by whitespaces are not concatenated


    val LEXFL_NOSTRINGESCAPECHARS: Int = BIT(4) // no escape characters inside strings
    val LEXFL_NOWARNINGS: Int = BIT(1) // don't print any warnings
    val LEXFL_ONLYSTRINGS: Int =
        BIT(13) // parse as whitespace deliminated strings (quoted strings keep quotes)
    const val P_PRECOMP = 51
    const val PUNCTABLE = true
    const val P_ADD = 29
    private const val P_ADD_ASSIGN = 14
    private const val P_ASSIGN = 31
    private const val P_BACKSLASH = 50
    const val P_BIN_AND = 32
    private const val P_BIN_AND_ASSIGN = 18
    const val P_BIN_NOT = 35
    const val P_BIN_OR = 33
    private const val P_BIN_OR_ASSIGN = 19
    const val P_BIN_XOR = 34
    private const val P_BIN_XOR_ASSIGN = 20
    private const val P_BRACECLOSE = 47
    private const val P_BRACEOPEN = 46
    const val P_COLON = 42
    private const val P_COMMA = 40
    private const val P_CPP1 = 24
    private const val P_CPP2 = 25
    const val P_DEC = 17
    const val P_DIV = 27
    private const val P_DIV_ASSIGN = 12
    private const val P_DOLLAR = 52
    const val P_INC = 16
    const val P_LOGIC_AND = 5
    const val P_LOGIC_EQ = 9
    const val P_LOGIC_GEQ = 7
    const val P_LOGIC_GREATER = 37
    const val P_LOGIC_LEQ = 8
    const val P_LOGIC_LESS = 38
    const val P_LOGIC_NOT = 36
    const val P_LOGIC_OR = 6
    const val P_LOGIC_UNEQ = 10
    const val P_LSHIFT = 22
    private const val P_LSHIFT_ASSIGN = 2
    const val P_MOD = 28
    private const val P_MOD_ASSIGN = 13
    const val P_MUL = 26
    private const val P_MUL_ASSIGN = 11
    const val P_PARENTHESESCLOSE = 45
    const val P_PARENTHESESOPEN = 44
    private const val P_PARMS = 3
    private const val P_POINTERREF = 23
    private const val P_PRECOMPMERGE = 4
    const val P_QUESTIONMARK = 43
    private const val P_REF = 39
    const val P_RSHIFT = 21

    //
    //
    // punctuation ids
    private const val P_RSHIFT_ASSIGN = 1
    private const val P_SEMICOLON = 41
    private const val P_SQBRACKETCLOSE = 49
    private const val P_SQBRACKETOPEN = 48
    const val P_SUB = 30
    private const val P_SUB_ASSIGN = 15

    //
    //  
    //longer punctuations first
    val default_punctuations: Array<punctuation_t> = arrayOf( //binary operators
        punctuation_t(">>=", P_RSHIFT_ASSIGN),
        punctuation_t("<<=", P_LSHIFT_ASSIGN),  //
        punctuation_t("...", P_PARMS),  //define merge operator
        punctuation_t("##", P_PRECOMPMERGE),  // pre-compiler
        //logic operators
        punctuation_t("&&", P_LOGIC_AND),  // pre-compiler
        punctuation_t("||", P_LOGIC_OR),  // pre-compiler
        punctuation_t(">=", P_LOGIC_GEQ),  // pre-compiler
        punctuation_t("<=", P_LOGIC_LEQ),  // pre-compiler
        punctuation_t("==", P_LOGIC_EQ),  // pre-compiler
        punctuation_t("!=", P_LOGIC_UNEQ),  // pre-compiler
        //arithmatic operators
        punctuation_t("*=", P_MUL_ASSIGN),
        punctuation_t("/=", P_DIV_ASSIGN),
        punctuation_t("%=", P_MOD_ASSIGN),
        punctuation_t("+=", P_ADD_ASSIGN),
        punctuation_t("-=", P_SUB_ASSIGN),
        punctuation_t("++", P_INC),
        punctuation_t("--", P_DEC),  //binary operators
        punctuation_t("&=", P_BIN_AND_ASSIGN),
        punctuation_t("|=", P_BIN_OR_ASSIGN),
        punctuation_t("^=", P_BIN_XOR_ASSIGN),
        punctuation_t(">>", P_RSHIFT),  // pre-compiler
        punctuation_t("<<", P_LSHIFT),  // pre-compiler
        //reference operators
        punctuation_t("->", P_POINTERREF),  //C++
        punctuation_t("::", P_CPP1),
        punctuation_t(".*", P_CPP2),  //arithmatic operators
        punctuation_t("*", P_MUL),  // pre-compiler
        punctuation_t("/", P_DIV),  // pre-compiler
        punctuation_t("%", P_MOD),  // pre-compiler
        punctuation_t("+", P_ADD),  // pre-compiler
        punctuation_t("-", P_SUB),  // pre-compiler
        punctuation_t("=", P_ASSIGN),  //binary operators
        punctuation_t("&", P_BIN_AND),  // pre-compiler
        punctuation_t("|", P_BIN_OR),  // pre-compiler
        punctuation_t("^", P_BIN_XOR),  // pre-compiler
        punctuation_t("~", P_BIN_NOT),  // pre-compiler
        //logic operators
        punctuation_t("!", P_LOGIC_NOT),  // pre-compiler
        punctuation_t(">", P_LOGIC_GREATER),  // pre-compiler
        punctuation_t("<", P_LOGIC_LESS),  // pre-compiler
        //reference operator
        punctuation_t(".", P_REF),  //seperators
        punctuation_t(",", P_COMMA),  // pre-compiler
        punctuation_t(";", P_SEMICOLON),  //label indication
        punctuation_t(":", P_COLON),  // pre-compiler
        //if statement
        punctuation_t("?", P_QUESTIONMARK),  // pre-compiler
        //embracements
        punctuation_t("(", P_PARENTHESESOPEN),  // pre-compiler
        punctuation_t(")", P_PARENTHESESCLOSE),  // pre-compiler
        punctuation_t("{", P_BRACEOPEN),  // pre-compiler
        punctuation_t("}", P_BRACECLOSE),  // pre-compiler
        punctuation_t("[", P_SQBRACKETOPEN),
        punctuation_t("]", P_SQBRACKETCLOSE),  //
        punctuation_t("\\", P_BACKSLASH),  //precompiler operator
        punctuation_t("#", P_PRECOMP),  // pre-compiler
        punctuation_t("$", P_DOLLAR),
        punctuation_t(null, 0)
    )
    val default_nextpunctuation: IntArray = IntArray(default_punctuations.size)
    val default_punctuationtable: IntArray = IntArray(256)
    var default_setup = false

    // punctuation
    class punctuation_t(// punctuation character(s)
        var p: String?, // punctuation id
        var n: Int
    )

    class idLexer {
        var next // next script in a chain
                : idLexer?
        var buffer // buffer containing the script
                : CharArray = CharArray(0)
        var script_p // current pointer in the script
                = 0
        private var allocated // true if buffer memory was allocated
                : Boolean
        private var end_p // pointer to the end of the script
                = 0
        private /*ID_TIME_T*/  var fileTime // file time
                : Long = 0
        private var filename // file name of the script
                : idStr = idStr()
        private var flags // several script flags
                : Int
        private var hadError // set by idLexer::Error, even if the error is suppressed
                : Boolean
        private var lastScript_p // script pointer before reading token
                = 0
        private var lastline // line before reading token
                = 0
        private var length // length of the script in bytes
                = 0
        private var line // current line in script
                = 0
        private var loaded // set when a script file is loaded from file or memory
                : Boolean
        private var nextPunctuation // next punctuation in chain
                : IntArray = IntArray(0)
        private var punctuationTable // ASCII table with punctuations
                : IntArray = IntArray(0)
        private var punctuations // the punctuations used in the script
                : Array<punctuation_t> = default_punctuations
        private var token // available token
                : idToken = idToken()
        private var tokenAvailable // set by unreadToken
                = false
        private var whiteSpaceEnd_p // end of last white space
                = 0
        private var whiteSpaceStart_p // start of last white space
                = 0
        private val _tempToken = idToken()

        //
        //
        // constructor
        constructor() {
            loaded = false
            filename = idStr("")
            flags = 0
            SetPunctuations(null)
            allocated = false
            fileTime = 0
            length = 0
            line = 0
            lastline = 0
            tokenAvailable = false
            token = idToken()
            next = null
            hadError = false
        }

        constructor(flags: Int) {
            loaded = false
            filename = idStr("")
            this.flags = flags
            SetPunctuations(null)
            allocated = false
            fileTime = 0
            length = 0
            line = 0
            lastline = 0
            tokenAvailable = false
            token = idToken()
            next = null
            hadError = false
        }

        constructor(filename: String) {
            loaded = false
            flags = 0
            SetPunctuations(null)
            allocated = false
            token = idToken()
            next = null
            hadError = false
            this.LoadFile(filename, false)
        }

        constructor(filename: String, flags: Int) {
            loaded = false
            this.flags = flags
            SetPunctuations(null)
            allocated = false
            token = idToken()
            next = null
            hadError = false
            this.LoadFile(filename, false)
        }

        constructor(filename: String, flags: Int, OSPath: Boolean) {
            loaded = false
            this.flags = flags
            SetPunctuations(null)
            allocated = false
            token = idToken()
            next = null
            hadError = false
            this.LoadFile(filename, OSPath)
        }

        constructor(ptr: CharBuffer, length: Int, name: String) {
            loaded = false
            flags = 0
            SetPunctuations(null)
            allocated = false
            token = idToken()
            next = null
            hadError = false
            this.LoadMemory(ptr, length, name)
        }

        constructor(ptr: String, length: Int, name: String) {
            loaded = false
            flags = 0
            SetPunctuations(null)
            allocated = false
            token = idToken()
            next = null
            hadError = false
            this.LoadMemory(ptr, length, name)
        }

        constructor(ptr: String, length: Int, name: String, flags: Int) {
            loaded = false
            this.flags = flags
            SetPunctuations(null)
            allocated = false
            token = idToken()
            next = null
            hadError = false
            this.LoadMemory(ptr, length, name)
        }

        @Throws(idException::class)
        fun LoadFile(filename: idStr): Boolean {
            return this.LoadFile(filename.toString())
        }

        // load a script from the given file at the given offset with the given length

        @Throws(idException::class)
        fun LoadFile(filename: String, OSPath: Boolean = false /*= false*/): Boolean {
//        TODO:NIO
            val fp: idFile?
            val pathname: String
            val length: Int
            val buf: ByteBuffer
            if (loaded) {
                idLib.common.Error("this.LoadFile: another script already loaded")
                return false
            }
            pathname =
                if (!OSPath && baseFolder.length > 0 && baseFolder[0] != '\u0000') { //TODO: use length isntead
                    Str.va("%s/%s", baseFolder, filename)
                } else {
                    filename
                }
            fp = if (OSPath) {
                idLib.fileSystem.OpenExplicitFileRead(pathname)
            } else {
                idLib.fileSystem.OpenFileRead(pathname)
            }
            if (null == fp) {
                return false
            }
            length = fp.Length()
            buf = ByteBuffer.allocate(length + 1)
            fp.Read(buf, length)
            buf.put(length, 0.toByte()) //[length] = '\0';
            fileTime = fp.Timestamp()
            this.filename = idStr(fp.GetFullPath())
            idLib.fileSystem.CloseFile(fp)
            buffer = TempDump.bbtocb(buf).let { cb ->
                val arr = CharArray(cb.remaining())
                cb.get(arr)
                arr
            }
            this.length = length
            // pointer in script buffer
//            this.script_p = this.buffer;
            script_p = 0
            // pointer in script buffer before reading token
            lastScript_p = 0 //this.buffer;
            // pointer to end of script buffer
            end_p = buffer.size //(this.buffer[length]);
            tokenAvailable = false //0;
            line = 1
            lastline = 1
            allocated = true
            loaded = true
            return true
        }

        // load a script from the given memory with the given length and a specified line offset,
        // so source strings extracted from a file can still refer to proper line numbers in the file
        // NOTE: the ptr is expected to point at a valid C string: ptr[length] == '\0'
        @Throws(idException::class)
        fun LoadMemory(ptr: idStr, length: Int, name: idStr /*= 1*/): Boolean {
            return LoadMemory(ptr.toString(), length, name.toString())
        }

        @Throws(idException::class)
        fun LoadMemory(ptr: String, length: Int, name: String /*= 1*/): Boolean {
            return LoadMemory(ptr, length, name, 1)
        }


        @Throws(idException::class)
        fun LoadMemory(ptr: CharBuffer, length: Int, name: String, startLine: Int = 1): Boolean {
            if (loaded) {
                idLib.common.Error("this.LoadMemory: another script already loaded")
                return false
            }
            filename = idStr(name) // Allocate CharArray with null terminator directly from CharBuffer
            val arr = CharArray(length + 1)
            for (i in 0 until length) {
                arr[i] = ptr.get(i)
            }
            arr[length] = '\u0000'
            buffer = arr
            fileTime = 0
            this.length = length
            // pointer in script buffer
            script_p = 0 //this.buffer;
            // pointer in script buffer before reading token
            lastScript_p = 0 //this.buffer;
            // pointer to end of script buffer
            end_p = buffer.size //(this.buffer[length]);
            tokenAvailable = false //0;
            line = startLine
            lastline = startLine
            allocated = false
            loaded = true
            return true
        }

        @Throws(idException::class)
        fun LoadMemory(ptr: String, length: Int, name: String, startLine: Int): Boolean {
            if (loaded) {
                idLib.common.Error("this.LoadMemory: another script already loaded")
                return false
            }
            filename = idStr(name) // Convert String to CharArray with null terminator
            buffer = (ptr + Char(0)).toCharArray()
            fileTime = 0
            this.length = length // pointer in script buffer
            script_p = 0 //this.buffer;
            // pointer in script buffer before reading token
            lastScript_p = 0 //this.buffer;
            // pointer to end of script buffer
            end_p = buffer.size //(this.buffer[length]);
            tokenAvailable = false //0;
            line = startLine
            lastline = startLine
            allocated = false
            loaded = true
            return true
        }

        @Throws(idException::class)
        fun LoadMemory(ptr: idStr, length: Int, name: String): Boolean {
            return LoadMemory(ptr.toString(), length, name)
        }

        // free the script
        fun FreeSource() {
//#ifdef PUNCTABLE
            if (punctuationTable.isNotEmpty() && !punctuationTable.contentEquals(default_punctuationtable)) {
//                Mem_Free((void *) this.punctuationtable);
                punctuationTable = IntArray(0)
            }
            if (nextPunctuation.isNotEmpty() && !nextPunctuation.contentEquals(default_nextpunctuation)) {
//                Mem_Free((void *) this.nextpunctuation);
                nextPunctuation = IntArray(0)
            }
            //#endif //PUNCTABLE
            if (allocated) {
//                Mem_Free((void *) this.buffer);
                buffer = CharArray(0)
                allocated = false
            }
            tokenAvailable = false //0;
            token = idToken()
            loaded = false
        }

        // returns true if a script is loaded
        fun IsLoaded(): Boolean {
            return loaded
        }

        // read a token
        @Throws(idException::class)
        fun ReadToken(token: idToken): Boolean {
            var c: Char
            if (!loaded) {
                idLib.common.Error("idLexer::ReadToken: no file loaded")
                return false
            }

            // if there is a token available (from unreadToken)
            if (tokenAvailable) {
                tokenAvailable = false
                token.set(this.token)
                return true
            }
            // save script pointer
            lastScript_p = script_p
            // save line counter
            lastline = line
            // clear the token stuff
            token.data = ""
            token.len = 0
            // start of the white space
            token.whiteSpaceStart_p = script_p
            whiteSpaceStart_p = token.whiteSpaceStart_p
            // read white space before token
            if (!ReadWhiteSpace()) {
                return false
            }
            // end of the white space
            token.whiteSpaceEnd_p = script_p
            whiteSpaceEnd_p = token.whiteSpaceEnd_p
            // line the token is on
            token.line = line
            // number of lines crossed before token
            token.linesCrossed = line - lastline
            // clear token flags
            token.flags = 0
            c = buffer[script_p]

            // if we're keeping everything as whitespace deliminated strings
            if (flags and LEXFL_ONLYSTRINGS != 0) {
                // if there is a leading quote
                return if (c == '\"' || c == '\'') {
                    ReadString(token, c.code)
                } else ReadName(token)
            } // if there is a number
            else if (
                (c in '0'..'9') || (c == '.' && ((buffer[script_p + 1]) in '0'..'9'))
            ) {
                if (!ReadNumber(token)) {
                    return false
                }
                // if names are allowed to start with a number
                if (flags and LEXFL_ALLOWNUMBERNAMES != 0) {
                    c = buffer[script_p]
                    if (Character.isLetter(c) || c == '_') {
                        return ReadName(token)
                    }
                }
            } // if there is a leading quote
            else if (c == '\"' || c == '\'') {
                return ReadString(token, c.code)
            } // if there is a name
            else if (Character.isLetter(c) || c == '_') {
                return ReadName(token)
            } // names may also start with a slash when pathnames are allowed
            else if (flags and LEXFL_ALLOWPATHNAMES != 0 && (c == '/' || c == '\\' || c == '.')) {
                return ReadName(token)
            } // check for punctuations
            else if (!ReadPunctuation(token)) {
                Error("unknown punctuation %c", c)
                return false
            }
            // succesfully read a token
            return true
        }

        // expect a certain token, reads the token when available
        @Throws(idException::class)
        fun ExpectTokenString(string: String): Boolean {
            val token = _tempToken
            if (!ReadToken(token)) {
                Error("couldn't find expected '%s'", string)
                return false
            }
            if (!token.toString().contentEquals(string)) {
                Error("expected '%s' but found '%s'", string, token)
                return false
            }
            return true
        }

        // expect a certain token type
        @Throws(idException::class)
        fun ExpectTokenType(type: Int, subtype: Int, token: idToken): Boolean {
            val str = idStr()
            if (!ReadToken(token)) {
                Error("couldn't read expected token")
                return false
            }
            if (token.type != type) {
                when (type) {
                    Token.TT_STRING -> str.set("string")
                    Token.TT_LITERAL -> str.set("literal")
                    Token.TT_NUMBER -> str.set("number")
                    Token.TT_NAME -> str.set("name")
                    Token.TT_PUNCTUATION -> str.set("punctuation")
                    else -> str.set("unknown type")
                }
                Error("expected a %s but found '%s'", str.toString(), token.toString())
                return false
            }
            if (token.type == Token.TT_NUMBER) {
                if (token.subtype and subtype != subtype) {
                    str.Clear()
                    if (subtype and Token.TT_DECIMAL != 0) {
                        str.set("decimal ")
                    }
                    if (subtype and Token.TT_HEX != 0) {
                        str.set("hex ")
                    }
                    if (subtype and Token.TT_OCTAL != 0) {
                        str.set("octal ")
                    }
                    if (subtype and Token.TT_BINARY != 0) {
                        str.set("binary ")
                    }
                    if (subtype and Token.TT_UNSIGNED != 0) {
                        str.Append("unsigned ")
                    }
                    if (subtype and Token.TT_LONG != 0) {
                        str.Append("long ")
                    }
                    if (subtype and Token.TT_FLOAT != 0) {
                        str.Append("float ")
                    }
                    if (subtype and Token.TT_INTEGER != 0) {
                        str.Append("integer ")
                    }
                    str.StripTrailing(' ')
                    Error("expected %s but found '%s'", str.toString(), token.toString())
                    return false
                }
            } else if (token.type == Token.TT_PUNCTUATION) {
                if (subtype < 0) {
                    Error("BUG: wrong punctuation subtype")
                    return false
                }
                if (token.subtype != subtype) {
                    Error("expected '%s' but found '%s'", GetPunctuationFromId(subtype), token.toString())
                    return false
                }
            }
            return true
        }

        // expect a token
        @Throws(idException::class)
        fun ExpectAnyToken(token: idToken): Boolean {
            return if (!ReadToken(token)) {
                Error("couldn't read expected token")
                false
            } else {
                true
            }
        }

        // returns true when the token is available
        @Throws(idException::class)
        fun CheckTokenString(string: String): Boolean {
            val tok = _tempToken
            if (!ReadToken(tok)) {
                return false
            }
            // if the given string is available
            if (tok.toString().compareTo(string) == 0) {
                return true
            }
            // unread token
            script_p = lastScript_p
            line = lastline
            return false
        }

        // returns true an reads the token when a token with the given type is available
        @Throws(idException::class)
        fun CheckTokenType(type: Int, subtype: Int, token: idToken): Int {
            val tok = _tempToken
            if (!ReadToken(tok)) {
                return 0
            }
            // if the type matches
            if (tok.type == type && (tok.subtype and subtype) == subtype) {
                token.set(tok)
                return 1
            }
            // unread token
            script_p = lastScript_p
            line = lastline
            return 0
        }

        // returns true if the next token equals the given string but does not remove the token from the source
        @Throws(idException::class)
        fun PeekTokenString(string: String): Boolean {
            val tok = idToken()
            if (!ReadToken(tok)) {
                return false
            }

            // unread token
            script_p = lastScript_p
            line = lastline
            return tok.toString() == string
        }

        // returns true if the next token equals the given type but does not remove the token from the source
        @Throws(idException::class)
        fun PeekTokenType(type: Int, subtype: Int, token: Array<idToken>): Boolean {
            val tok = idToken()
            if (!ReadToken(tok)) {
                return false
            }

            // unread token
            script_p = lastScript_p
            line = lastline

            // if the type matches
            if (tok.type == type && tok.subtype and subtype == subtype) {
                token[0] = tok
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
        fun SkipRestOfLine(): Int {
            val token = idToken()
            while (ReadToken(token)) {
                if (token.linesCrossed != 0) {
                    script_p = lastScript_p
                    line = lastline
                    return 1
                }
            }
            return 0
        }

        /*
         =================
         idLexer::SkipBracedSection

         Skips until a matching close brace is found.
         Internal brace depths are properly skipped.
         =================
         */
        // skip the braced section

        @Throws(idException::class)
        fun SkipBracedSection(parseFirstBrace: Boolean = true): Boolean {
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

        // unread the given token
        @Throws(idException::class)
        fun UnreadToken(token: idToken) {
            if (tokenAvailable) {
                idLib.common.FatalError("idLexer::unreadToken, unread token twice\n")
            }
            this.token.set(token)
            tokenAvailable = true
        }

        //		
        // read a token only if on the same line
        @Throws(idException::class)
        fun ReadTokenOnLine(token: idToken): Boolean {
            val tok = idToken()
            if (!ReadToken(tok)) {
                script_p = lastScript_p
                line = lastline
                return false
            }
            // if no lines were crossed before this token
            if (0 == tok.linesCrossed) {
                token.set(tok)
                return true
            }
            // restore our position
            script_p = lastScript_p
            line = lastline
            token.Clear()
            return false
        }

        //
        //Returns the rest of the current line
        fun ReadRestOfLine(out: idStr): String {
            while (true) {
                if (buffer[script_p] == '\n') {
                    line++
                    break
                }
                if (0 == buffer[script_p].code) {
                    break
                }
                if (buffer[script_p] <= ' ') {
                    out.Append(" ")
                } else {
                    out.Append(buffer[script_p])
                }
                script_p++
            }
            out.Strip(' ')
            return out.toString()
        }

        // read a signed integer
        @Throws(idException::class)
        fun ParseInt(): Int {
            val token = _tempToken
            if (!ReadToken(token)) {
                Error("couldn't read expected integer")
                return 0
            }
            if (token.type == Token.TT_PUNCTUATION && token.toString() == "-") {
                ExpectTokenType(Token.TT_NUMBER, Token.TT_INTEGER, token)
                return -token.GetIntValue()
            } else if (token.type != Token.TT_NUMBER || token.subtype == Token.TT_FLOAT) {
                Error("expected integer value, found '%s'", token)
            }
            return token.GetIntValue()
        }

        // read a Boolean
        @Throws(idException::class)
        fun ParseBool(): Boolean {
            val token = _tempToken
            if (!ExpectTokenType(Token.TT_NUMBER, 0, token)) {
                Error("couldn't read expected boolean")
                return false
            }
            return token.GetIntValue() != 0
        }

        // read a floating point number.  If errorFlag is NULL, a non-numeric token will
        // issue an Error().  If it isn't NULL, it will issue a Warning() and set *errorFlag = true

        @Throws(idException::class)
        fun ParseFloat(errorFlag: BooleanArray? = null /*= NULL*/): Float {
            val token = _tempToken
            if (errorFlag != null) {
                errorFlag[0] = false
            }
            if (!ReadToken(token)) {
                if (errorFlag != null) {
                    Warning("couldn't read expected floating point number")
                    errorFlag[0] = true
                } else {
                    Error("couldn't read expected floating point number")
                }
                return 0.0f
            }
            if (token.type == Token.TT_PUNCTUATION && token.toString() == "-") {
                ExpectTokenType(Token.TT_NUMBER, 0, token)
                return -token.GetFloatValue()
            } else if (token.type != Token.TT_NUMBER) {
                if (errorFlag != null) {
                    Warning("expected float value, found '%s'", token)
                    errorFlag[0] = true
                } else {
                    Error("expected float value, found '%s'", token)
                }
            }
            return token.GetFloatValue()
        }

        @Throws(idException::class)
        fun Parse1DMatrix(x: Int, v: idVec<*>): Boolean {
            val m = FloatArray(x)
            val result = Parse1DMatrix(x, m)
            for (i in 0 until x) {
                v[i] = m[i]
            }
            return result
        }

        @Throws(idException::class)
        fun Parse1DMatrix(x: Int, p: idPlane): Boolean {
            val m = FloatArray(x)
            val result = Parse1DMatrix(x, m)
            for (i in 0 until x) {
                p[i] = m[i]
            }
            return result
        }

        @Throws(idException::class)
        fun Parse1DMatrix(x: Int, m: idMat3): Boolean {
            val n = FloatArray(x)
            val result = Parse1DMatrix(x, n)
            for (i in 0..2) {
                for (j in 0..2) {
                    m.set(i, j, n[i * 3 + j])
                }
            }
            return result
        }

        @Throws(idException::class)
        fun Parse1DMatrix(x: Int, q: idQuat): Boolean {
            val m = FloatArray(x)
            val result = Parse1DMatrix(x, m)
            for (i in 0 until x) {
                q[i] = m[i]
            }
            return result
        }

        @Throws(idException::class)
        fun Parse1DMatrix(x: Int, q: idCQuat): Boolean {
            val m = FloatArray(x)
            val result = Parse1DMatrix(x, m)
            for (i in 0 until x) {
                q[i] = m[i]
            }
            return result
        }

        @Throws(idException::class)
        fun Parse1DMatrix(x: Int, m: FloatArray): Boolean {
            return this.Parse1DMatrix(x, m, 0)
        }

        // parse matrices with floats
        @Throws(idException::class)
        private fun Parse1DMatrix(x: Int, m: FloatArray, offset: Int): Boolean {
            if (!ExpectTokenString("(")) {
                return false
            }
            for (i in 0 until x) {
                m[offset + i] = ParseFloat()
            }
            return ExpectTokenString(")")
        }

        @Throws(idException::class)
        fun Parse2DMatrix(y: Int, x: Int, m: Array<idVec3>): Boolean {
            if (!ExpectTokenString("(")) {
                return false
            }
            for (i in 0 until y) {
                if (!Parse1DMatrix(x, m[i])) {
                    return false
                }
            }
            return ExpectTokenString(")")
        }

        @Throws(idException::class)
        fun Parse2DMatrix(y: Int, x: Int, m: FloatArray): Boolean {
            return this.Parse2DMatrix(y, x, m, 0)
        }

        @Throws(idException::class)
        private fun Parse2DMatrix(y: Int, x: Int, m: FloatArray, offset: Int): Boolean {
            if (!ExpectTokenString("(")) {
                return false
            }
            for (i in 0 until y) {
                if (!this.Parse1DMatrix(x, m, offset + i * x)) {
                    return false
                }
            }
            return ExpectTokenString(")")
        }

        @Throws(idException::class)
        fun Parse3DMatrix(z: Int, y: Int, x: Int, m: FloatArray): Boolean {
            if (!ExpectTokenString("(")) {
                return false
            }
            for (i in 0 until z) {
                if (!this.Parse2DMatrix(y, x, m, i * x * y)) {
                    return false
                }
            }
            return ExpectTokenString(")")
        }

        /*
         =================
         idLexer::ParseBracedSection

         The next token should be an open brace.
         Parses until a matching close brace is found.
         Internal brace depths are properly skipped.
         =================
         */
        // parse a braced section into a string
        @Throws(idException::class)
        fun ParseBracedSection(out: idStr): String {
            val token = idToken()
            var i: Int
            var depth: Int
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
                    out.plusAssign("\r\n")
                    i++
                }
                if (token.type == Token.TT_PUNCTUATION) {
                    if (token.toString() == "{") {
                        depth++
                    } else if (token.toString() == "}") {
                        depth--
                    }
                }
                if (token.type == Token.TT_STRING) {
                    out.plusAssign("\"" + token + "\"")
                } else {
                    out.plusAssign(token)
                }
                out.plusAssign(" ")
            } while (depth != 0)
            return out.toString()
        }

        /*
         =================
         idParser::ParseBracedSection

         The next token should be an open brace.
         Parses until a matching close brace is found.
         Maintains exact characters between braces.

         FIXME: this should use ReadToken and replace the token white space with correct indents and newlines
         =================
         */
        @Throws(idException::class)
        fun ParseBracedSection(out: idStr, tabs: Int /*= -1*/): String {
            var tabs = tabs
            var depth: Int
            val doTabs: Boolean
            var skipWhite: Boolean
            out.Empty()
            if (!ExpectTokenString("{")) {
                return out.toString()
            }
            val sb = StringBuilder("{")
            depth = 1
            skipWhite = false
            doTabs = tabs >= 0
            while (depth != 0 && buffer[script_p].code != 0) {
                val c: Char = buffer[script_p++]
                when (c) {
                    '\t', ' ' -> {
                        if (skipWhite) {
                            continue
                        }
                    }

                    '\n' -> {
                        if (doTabs) {
                            skipWhite = true
                            sb.append(c)
                            continue
                        }
                    }

                    '{' -> {
                        depth++
                        tabs++
                    }

                    '}' -> {
                        depth--
                        tabs--
                    }
                }
                if (skipWhite) {
                    var i = tabs
                    if (c == '{') {
                        i--
                    }
                    skipWhite = false
                    while (i > 0) {
                        sb.append('\t')
                        i--
                    }
                }
                sb.append(c)
            }
            out.set(sb.toString())
            return out.toString()
        }

        /*
         =================
         idParser::ParseBracedSection

         The next token should be an open brace.
         Parses until a matching close brace is found.
         Maintains exact characters between braces.

         FIXME: this should use ReadToken and replace the token white space with correct indents and newlines
         =================
         */
        // parse a braced section into a string, maintaining indents and newlines
        //public	String	ParseBracedSectionExact ( idStr &out, int tabs = -1 );
        @Throws(idException::class)
        fun ParseBracedSectionExact(out: idStr, tabs: Int): String {
            var tabs = tabs
            var depth: Int
            val doTabs: Boolean
            var skipWhite: Boolean
            out.Empty()
            if (!ExpectTokenString("{")) {
                return out.toString()
            }
            val sb = StringBuilder("{")
            depth = 1
            skipWhite = false
            doTabs = tabs >= 0
            while (depth != 0 && buffer[script_p].code != 0) {
                val c: Char = buffer[script_p++]
                when (c) {
                    '\t', ' ' -> {
                        if (skipWhite) {
                            continue
                        }
                    }

                    '\n' -> {
                        if (doTabs) {
                            skipWhite = true
                            sb.append(c)
                            continue
                        }
                    }

                    '{' -> {
                        depth++
                        tabs++
                    }

                    '}' -> {
                        depth--
                        tabs--
                    }
                }
                if (skipWhite) {
                    var i = tabs
                    if (c == '{') {
                        i--
                    }
                    skipWhite = false
                    while (i > 0) {
                        sb.append('\t')
                        i--
                    }
                }
                sb.append(c)
            }
            out.set(sb.toString())
            return out.toString()
        }

        // parse the rest of the line
        @Throws(idException::class)
        fun ParseRestOfLine(out: idStr): String {
            val token = idToken()
            out.Empty()
            while (ReadToken(token)) {
                if (token.linesCrossed != 0) {
                    script_p = lastScript_p
                    line = lastline
                    break
                }
                if (out.Length() != 0) {
                    out.plusAssign(" ")
                }
                out.plusAssign(token)
            }
            return out.toString()
        }

        // retrieves the white space characters before the last read token
        fun GetLastWhiteSpace(whiteSpace: idStr): Int {
            whiteSpace.Clear()
            for (p in whiteSpaceStart_p until whiteSpaceEnd_p) {
                whiteSpace.Append(buffer[p])
            }
            return whiteSpace.Length()
        }

        // returns start index into text buffer of last white space
        fun GetLastWhiteSpaceStart(): Int {
            return whiteSpaceStart_p // - buffer;
        }

        // returns end index into text buffer of last white space
        fun GetLastWhiteSpaceEnd(): Int {
            return whiteSpaceEnd_p // - buffer;
        }

        // set an array with punctuations, NULL restores default C/C++ set, see default_punctuations for an example
        fun SetPunctuations(p: Array<punctuation_t>?) {
            if (PUNCTABLE) {
                if (p != null) {
                    CreatePunctuationTable(p)
                } else {
                    CreatePunctuationTable(default_punctuations)
                }
            } //PUNCTABLE
            if (p != null) {
                punctuations = p
            } else {
                punctuations = default_punctuations
            }
        }

        // returns a pointer to the punctuation with the given id
        fun GetPunctuationFromId(id: Int): String {
            var i: Int
            i = 0
            while (punctuations[i].p != null) {
                if (punctuations[i].n == id) {
                    return punctuations[i].p!!
                }
                i++
            }
            return "unknown punctuation"
        }

        // set lexer flags
        // get the id for the given punctuation
        fun GetPunctuationId(p: String): Int {
            var i: Int
            i = 0
            while (punctuations[i].p != null) {
                if (punctuations[i].p == p) {
                    return punctuations[i].n
                }
                i++
            }
            return 0
        }

        fun SetFlags(flags: Int) {
            this.flags = flags
        }

        // get lexer flags
        fun GetFlags(): Int {
            return flags
        }

        // reset the lexer
        fun Reset() {
            // pointer in script buffer
//            this.script_p = this.buffer;
            script_p = 0
            // pointer in script buffer before reading token
//            this.lastScript_p = this.buffer;
            lastScript_p = 0
            // begin of white space
            whiteSpaceStart_p = 0
            // end of white space
            whiteSpaceEnd_p = 0
            // set if there's a token available in this.token
            tokenAvailable = false
            line = 1
            lastline = 1
            // clear the saved token
            token = idToken()
        }

        // returns true if at the end of the file
        fun EndOfFile(): Boolean {
            return script_p >= end_p - 1 || script_p >= end_p
        }

        // returns the current filename
        fun GetFileName(): idStr {
            return filename
        }

        // get offset in script
        fun GetFileOffset(): Int {
            return script_p //- this.buffer;
        }

        // get file time
        fun  /*ID_TIME_T*/GetFileTime(): Long {
            return fileTime
        }

        // returns the current line number
        fun GetLineNum(): Int {
            return line
        }

        // print an error message
        @Throws(idException::class)
        fun Error(fmt: String, vararg str: Any) { //id_attribute((format(printf,2,3)));
            val text: String //[MAX_STRING_CHARS];
            //            va_list ap;
            hadError = true
            if (flags and LEXFL_NOERRORS != 0) {
                return
            }
            text = String.format(fmt, *str)
            //            va_start(ap, str);
//            vsprintf(text, str, ap);
//            va_end(ap);
            if (flags and LEXFL_NOFATALERRORS != 0) {
                idLib.common.Warning("file %s, line %d: %s", filename.toString(), line, text)
            } else {
                idLib.common.Error("file %s, line %d: %s", filename.toString(), line, text)
            }
        }

        // print a warning message
        @Throws(idException::class)
        fun Warning(fmt: String, vararg str: Any) { //id_attribute((format(printf,2,3)));
            val text: String //[MAX_STRING_CHARS];
            //	va_list ap;
            if (flags and LEXFL_NOWARNINGS != 0) {
                return
            }
            text = String.format(fmt, *str)
            //	va_start( ap, str );
//	vsprintf( text, str, ap );
//	va_end( ap );
            idLib.common.Warning("file %s, line %d: %s", filename.toString(), line, text)
        }

        // returns true if Error() was called with LEXFL_NOFATALERRORS or LEXFL_NOERRORS set
        fun HadError(): Boolean {
            return hadError
        }

        private fun CreatePunctuationTable(punctuations: Array<punctuation_t>) {
            var i: Int
            var n: Int
            var lastp: Int
            var p: punctuation_t
            var newp: punctuation_t

            //get memory for the table
            if (punctuations.contentEquals(default_punctuations)) {
                punctuationTable = default_punctuationtable
                nextPunctuation = default_nextpunctuation
                if (default_setup) {
                    return
                }
                default_setup = true
                i = default_punctuations.size
            } else {
                if (punctuationTable.isEmpty() || punctuationTable.contentEquals(default_punctuationtable)
                ) {
                    punctuationTable = IntArray(256) // (int *) Mem_Alloc(256 * sizeof(int));
                }
                if (nextPunctuation.isNotEmpty() && !nextPunctuation.contentEquals(default_nextpunctuation)) {
//			Mem_Free( this.nextPunctuation );
                    nextPunctuation = IntArray(0)
                }
                i = 0
                while (punctuations[i].p != null) {
                    i++
                }
                nextPunctuation = IntArray(i) //(int *) Mem_Alloc(i * sizeof(int));
            }
            Arrays.fill(punctuationTable, 0, 256, -1) //memset(this.punctuationTable, 0xFF, 256 * sizeof(int));
            Arrays.fill(nextPunctuation, 0, i, -1) //memset(this.nextPunctuation, 0xFF, i * sizeof(int));
            //add the punctuations in the list to the punctuation table
            i = 0
            while (punctuations[i].p != null) {
                newp = punctuations[i]
                lastp = -1
                //sort the punctuations in this table entry on length (longer punctuations first)
                n = punctuationTable[newp.p!![0].code]
                while (n >= 0) {
                    p = punctuations[n]
                    if (p.p!!.length < newp.p!!.length) {
                        nextPunctuation[i] = n
                        if (lastp >= 0) {
                            nextPunctuation[lastp] = i
                        } else {
                            punctuationTable[newp.p!![0].code] = i
                        }
                        break
                    }
                    lastp = n
                    n = nextPunctuation[n]
                }
                if (n < 0) {
                    nextPunctuation[i] = -1
                    if (lastp >= 0) {
                        nextPunctuation[lastp] = i
                    } else {
                        punctuationTable[newp.p!![0].code] = i
                    }
                }
                i++
            }
        }

        /*
         ================
         idLexer::ReadWhiteSpace

         Reads spaces, tabs, C-like comments etc.
         When a newline character is found the scripts line counter is increased.
         ================
         */
        @Throws(idException::class)
        private fun ReadWhiteSpace(): Boolean {
//            if (filename.CheckExtension("roq")) {
//                return false
//            }
            while (true) {
                // skip white space
                while (buffer[script_p] <= ' ') {
                    if (buffer[script_p] == Char(0)) {
                        return false
                    }
                    if (buffer[script_p] == '\n') {
                        line++
                    }
                    script_p++
                }
                // skip comments
                if (buffer[script_p] == '/') {
                    // comments //
                    if (buffer[script_p + 1] == '/') {
                        script_p++
                        do {
                            script_p++
                            if (buffer[script_p] == Char(0)) {
                                return false
                            }
                        } while (buffer[script_p] != '\n')
                        line++
                        script_p++
                        if (buffer[script_p] == Char(0)) {
                            return false
                        }
                        continue
                    }
                    // comments /* */
                    else if (buffer[script_p + 1] == '*') {
                        script_p++
                        while (true) {
                            script_p++
                            if (buffer[script_p] == Char(0)) {
                                return false
                            }
                            if (buffer[script_p] == '\n') {
                                line++
                            } else if (buffer[script_p] == '/') {
                                if (buffer[script_p - 1] == '*') {
                                    break
                                }
                                if (buffer[script_p + 1] == '*') {
                                    Warning("nested comment")
                                }
                            }
                        }
                        script_p++
                        if (buffer[script_p] == Char(0)) {
                            return false
                        }
                        script_p++
                        if (buffer[script_p] == Char(0)) {
                            return false
                        }
                        continue
                    }
                }
                break
            }
            return true
        }

        @Throws(idException::class)
        private fun ReadEscapeCharacter(ch: CharArray): Boolean {
            var c: Int
            var `val`: Int
            var i: Int

            // step over the leading '\\'
            script_p++
            when (buffer[script_p]) {
                '\\' -> c = '\\'.code
                'n' -> c = '\n'.code
                'r' -> c = '\r'.code
                't' -> c = '\t'.code
                'v' -> c = '\u000B'.code //'\v';
                'b' -> c = '\b'.code
                'f' -> c = '\u000C'.code //'\f'
                'a' -> c = '\u0007'.code //'\a';
                '\'' -> c = '\''.code
                '\"' -> c = '\"'.code
                '?' -> c = '?'.code // I hope it works
                'x' -> {
                    script_p++
                    i = 0
                    `val` = 0
                    while (true) {
                        c = buffer[script_p].code
                        c = if (Character.isDigit(c)) {
                            c - '0'.code
                        } else if (Character.isUpperCase(c)) {
                            c - 'A'.code + 10
                        } else if (Character.isLowerCase(c)) {
                            c - 'a'.code + 10
                        } else {
                            break
                        }
                        `val` = (`val` shl 4) + c
                        i++
                        script_p++
                    }
                    script_p--
                    if (`val` > 0xFF) {
                        Warning("too large value in escape character")
                        `val` = 0xFF
                    }
                    c = `val`
                }

                else -> {
                    if (buffer[script_p] < '0' || buffer[script_p] > '9') {
                        Error("unknown escape char")
                    }
                    i = 0
                    `val` = 0
                    while (true) {
                        c = buffer[script_p].code
                        c = if (Character.isDigit(c)) {
                            c - '0'.code
                        } else {
                            break
                        }
                        `val` = `val` * 10 + c
                        i++
                        script_p++
                    }
                    script_p--
                    if (`val` > 0xFF) {
                        Warning("too large value in escape character")
                        `val` = 0xFF
                    }
                    c = `val`
                }
            }
            // step over the escape character or the last digit of the number
            script_p++
            // store the escape character
            ch[0] = c.toChar()
            // succesfully read escape character
            return true
        }

        /*
         ================
         idLexer::ReadString

         Escape characters are interpretted.
         Reads two strings with only a white space between them as one string.
         ================
         */
        @Throws(idException::class)
        private fun ReadString(token: idToken, quote: Int): Boolean {
            var tmpline: Int
            var tmpscript_p: Int
            val ch = CharArray(1)
            if (quote == '\"'.code) {
                token.type = Token.TT_STRING
            } else {
                token.type = Token.TT_LITERAL
            }

            // leading quote
            script_p++
            while (true) {
                // if there is an escape character and escape characters are allowed
                if (buffer[script_p] == '\\' && 0 == (flags and LEXFL_NOSTRINGESCAPECHARS)) {
                    if (!ReadEscapeCharacter(ch)) {
                        return false
                    }
                    token.AppendDirty(ch[0])
                } // if a trailing quote
                else if (buffer[script_p].code == quote) {
                    // step over the quote
                    script_p++
                    // if consecutive strings should not be concatenated
                    if (flags and LEXFL_NOSTRINGCONCAT != 0
                        && (0 == (flags and LEXFL_ALLOWBACKSLASHSTRINGCONCAT) || quote != '\"'.code)
                    ) {
                        break
                    }
                    tmpscript_p = script_p
                    tmpline = line
                    // read white space between possible two consecutive strings
                    if (!ReadWhiteSpace()) {
                        script_p = tmpscript_p
                        line = tmpline
                        break
                    }
                    if (flags and LEXFL_NOSTRINGCONCAT != 0) {
                        if (buffer[script_p] != '\\') {
                            script_p = tmpscript_p
                            line = tmpline
                            break
                        }
                        // step over the '\\'
                        script_p++
                        if (!ReadWhiteSpace() || buffer[script_p].code != quote) {
                            Error("expecting string after '' terminated line")
                            return false
                        }
                    }

                    // if there's no leading qoute
                    if (buffer[script_p].code != quote) {
                        script_p = tmpscript_p
                        line = tmpline
                        break
                    }
                    // step over the new leading quote
                    script_p++
                } else {
                    if (buffer[script_p] == '\u0000') {
                        Error("missing trailing quote")
                        return false
                    }
                    if (buffer[script_p] == '\n') {
                        Error("newline inside string")
                        return false
                    }
                    token.AppendDirty(buffer[script_p++])
                }
            }
            //            token.set(token.len, '\0');
            if (token.type == Token.TT_LITERAL) {
                if (0 == (flags and LEXFL_ALLOWMULTICHARLITERALS)) {
                    if (token.Length() != 1) {
                        Warning("literal is not one character long")
                    }
                }
                token.subtype = token[0].code
            } else {
                // the sub type is the length of the string
                token.subtype = token.Length()
            }
            return true
        }

        private fun ReadName(token: idToken): Boolean {
            var c: Char
            token.type = Token.TT_NAME
            do {
                token.AppendDirty(buffer[script_p])
            } while (script_p++ + 1 < buffer.size && (Character.isLowerCase(buffer[script_p].also { c = it })
                        || Character.isUpperCase(c)
                        || Character.isDigit(c)
                        || c == '_' ||  // if treating all tokens as strings, don't parse '-' as a seperate token
                        flags and LEXFL_ONLYSTRINGS != 0 && c == '-'
                        ||  // if special path name characters are allowed
                        flags and LEXFL_ALLOWPATHNAMES != 0 && (c == '/' || c == '\\' || c == ':' || c == '.'))
            )
            //            token.set(token.len, '\0');
            //the sub type is the length of the name
            token.subtype = token.Length()
            return true
        }

        @Throws(idException::class)
        private fun ReadNumber(token: idToken): Boolean {
            var i: Int
            var dot: Int
            var c: Char
            var c2: Char
            token.type = Token.TT_NUMBER
            token.subtype = 0
            token.intValue = 0
            token.floatValue = 0.0f
            c = buffer[script_p]
            c2 = buffer[script_p + 1]
            if (c == '0' && c2 != '.') {
                // check for a hexadecimal number
                if (c2 == 'x' || c2 == 'X') {
                    token.AppendDirty(buffer[script_p++])
                    token.AppendDirty(buffer[script_p++])
                    c = buffer[script_p]
                    while (Character.isDigit(c)
                        || c in 'a'..'f'
                        || c in 'A'..'F'
                    ) {
                        token.AppendDirty(c)
                        c = buffer[++script_p]
                    }
                    token.subtype = Token.TT_HEX or Token.TT_INTEGER
                } // check for a binary number
                else if (c2 == 'b' || c2 == 'B') {
                    token.AppendDirty(buffer[script_p++])
                    token.AppendDirty(buffer[script_p++])
                    c = buffer[script_p]
                    while (c == '0' || c == '1') {
                        token.AppendDirty(c)
                        c = buffer[++script_p]
                    }
                    token.subtype = Token.TT_BINARY or Token.TT_INTEGER
                } // its an octal number
                else {
                    token.AppendDirty(buffer[script_p++])
                    c = buffer[script_p]
                    while (c in '0'..'7') {
                        token.AppendDirty(c)
                        c = buffer[++script_p]
                    }
                    token.subtype = Token.TT_OCTAL or Token.TT_INTEGER
                }
            } else {
                // decimal integer or floating point number or ip address
                dot = 0
                while (true) {
                    if (c in '0'..'9') {
                        // if (c >= '0' && c <= '9') {
                    } else if (c == '.') {
                        dot++
                    } else {
                        break
                    }
                    token.AppendDirty(c)
                    if (buffer.size <= script_p + 1) {
                        return false
                    }
                    c = buffer[++script_p]
                }
                if (c == 'e' && dot == 0) {
                    //We have scientific notation without a decimal point
                    dot++
                }
                // if a floating point number
                if (dot == 1) {
                    token.subtype = Token.TT_DECIMAL or Token.TT_FLOAT
                    // check for floating point exponent
                    if (c == 'e') {
                        //Append the e so that GetFloatValue code works
                        token.AppendDirty(c)
                        c = buffer[++script_p]
                        if (c == '-') {
                            token.AppendDirty(c)
                            c = buffer[++script_p]
                        } else if (c == '+') {
                            token.AppendDirty(c)
                            c = buffer[++script_p]
                        }
                        while (Character.isDigit(c)) { //c >= '0' && c <= '9') {
                            token.AppendDirty(c)
                            c = buffer[++script_p]
                        }
                    } // check for floating point exception infinite 1.#INF or indefinite 1.#IND or NaN
                    else if (c == '#') {
                        c2 = 4.toChar()
                        if (CheckString("INF")) {
                            token.subtype = token.subtype or Token.TT_INFINITE
                        } else if (CheckString("IND")) {
                            token.subtype = token.subtype or Token.TT_INDEFINITE
                        } else if (CheckString("NAN")) {
                            token.subtype = token.subtype or Token.TT_NAN
                        } else if (CheckString("QNAN")) {
                            token.subtype = token.subtype or Token.TT_NAN
                            c2++
                        } else if (CheckString("SNAN")) {
                            token.subtype = token.subtype or Token.TT_NAN
                            c2++
                        }
                        i = 0
                        while (i < c2.code) {
                            token.AppendDirty(c)
                            c = buffer[++script_p]
                            i++
                        }
                        while (Character.isDigit(c)) {
                            token.AppendDirty(c)
                            c = buffer[++script_p]
                        }
                        if (0 == (flags and LEXFL_ALLOWFLOATEXCEPTIONS)) {
//                            token.AppendDirty('\0');	// zero terminate for c_str
                            Error("parsed %s", token.toString())
                        }
                    }
                } else if (dot > 1) {
                    if (0 == flags and LEXFL_ALLOWIPADDRESSES) {
                        Error("more than one dot in number")
                        return false
                    }
                    if (dot != 3) {
                        Error("ip address should have three dots")
                        return false
                    }
                    token.subtype = Token.TT_IPADDRESS
                } else {
                    token.subtype = Token.TT_DECIMAL or Token.TT_INTEGER
                }
            }
            if (token.subtype and Token.TT_FLOAT != 0) {
                if (c > ' ') {
                    // single-precision: float
                    if (c == 'f' || c == 'F') {
                        token.subtype = token.subtype or Token.TT_SINGLE_PRECISION
                        script_p++
                    } // extended-precision: long double
                    else if (c == 'l' || c == 'L') {
                        token.subtype = token.subtype or Token.TT_EXTENDED_PRECISION
                        script_p++
                    } // default is double-precision: double
                    else {
                        token.subtype = token.subtype or Token.TT_DOUBLE_PRECISION
                    }
                } else {
                    token.subtype = token.subtype or Token.TT_DOUBLE_PRECISION
                }
            } else if (token.subtype and Token.TT_INTEGER != 0) {
                if (c > ' ') {
                    // default: signed long
                    i = 0
                    while (i < 2) {

                        // long integer
                        if (c == 'l' || c == 'L') {
                            token.subtype = token.subtype or Token.TT_LONG
                        } // unsigned integer
                        else if (c == 'u' || c == 'U') {
                            token.subtype = token.subtype or Token.TT_UNSIGNED
                        } else {
                            break
                        }
                        c = buffer[++script_p]
                        i++
                    }
                }
            } else if (token.subtype and Token.TT_IPADDRESS != 0) {
                if (c == ':') {
                    token.AppendDirty(c)
                    c = buffer[++script_p]
                    while (Character.isDigit(c)) {
                        token.AppendDirty(c)
                        c = buffer[++script_p]
                    }
                    token.subtype = token.subtype or Token.TT_IPPORT
                }
            }
            //            token.set(token.len, '\0');
            return true
        }

        private fun ReadPunctuation(token: idToken): Boolean {
            var l: Int
            var n: Int
            var i: Int
            var p: CharArray
            var punc: punctuation_t

// #ifdef PUNCTABLE
            val readCode = buffer[script_p]
            n = punctuationTable[readCode.code]
            while (n >= 0) {
                punc = punctuations[n]
                // #else
//	int i;
//
//	for (i = 0; idLexer::punctuations[i].p; i++) {
//		punc = &idLexer::punctuations[i];
//#endif
                p = punc.p!!.toCharArray()
                // check for this punctuation in the script
                l = 0
                while (l < p.size && buffer[script_p + l].code != 0) {
                    if (buffer[script_p + l] != p[l]) {
                        break
                    }
                    l++
                }
                if (l >= p.size) {
                    //
                    token.EnsureAlloced(l + 1, false)
                    i = 0
                    while (i < l) {

//                        token.data[i] = p[i];
                        token[i] = p[i]
                        i++
                    }
                    token.len = l
                    //
                    script_p += l
                    token.type = Token.TT_PUNCTUATION
                    // sub type is the punctuation id
                    token.subtype = punc.n
                    return true
                }
                n = nextPunctuation[n]
            }
            return false
        }

        //        private boolean ReadPrimitive(idToken token);
        private fun CheckString(str: String): Boolean {
            var i: Int
            i = 0
            while (str[i].code != 0) {
                if (buffer[i + script_p] != str[i]) {
                    return false
                }
                i++
            }
            return true
        }

        private fun NumLinesCrossed(): Int {
            return line - lastline
        }

        companion object {
            //
            // base folder to load files from
            private val baseFolder: StringBuilder = StringBuilder(256)

            //					// destructor
            //public					~idLexer();
            // set the base folder to load files from
            fun SetBaseFolder(path: String) {
                idStr.Copynz(baseFolder, path) //TODO:length
            }
        }
    }
}
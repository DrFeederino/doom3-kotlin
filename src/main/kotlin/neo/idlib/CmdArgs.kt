package neo.idlib

import neo.idlib.Text.Lexer
import neo.idlib.Text.Lexer.idLexer
import neo.idlib.Text.Token
import neo.idlib.Text.Token.idToken

class CmdArgs {
    class idCmdArgs {
        private val argv: Array<String> = Array(MAX_COMMAND_ARGS) { String() } // points into tokenized
        private var argc // number of arguments
                = 0
        private var tokenized: StringBuilder = StringBuilder(MAX_COMMAND_STRING) // will have 0 bytes inserted

        //
        //
        constructor()
        constructor(text: String, keepAsStrings: Boolean) {
            TokenizeString(text, keepAsStrings)
        }

        //operator=( final idCmdArgs &args );
        fun set(args: idCmdArgs) {
            var i: Int
            argc = args.argc //	memcpy( tokenized, args.tokenized, MAX_COMMAND_STRING );
            tokenized = StringBuilder(args.tokenized)
            i = 0
            while (i < argc) {

                //		argv[ i ] = tokenized + ( args.argv[ i ] - args.tokenized );//TODO:what the hell does this do??????
                argv[i] = args.argv[i]
                i++
            }
        }

        fun set(text: String?) {
            TokenizeString(text, false)
        }

        // The functions that execute commands get their parameters with these functions.
        fun Argc(): Int {
            return argc
        }

        // Argv() will return an empty string, not NULL if arg >= argc.
        fun Argv(arg: Int): String {
            return if (arg >= 0 && arg < argc) argv[arg] else ""
        }

        // Returns a single string containing argv(start) to argv(end)
        // escapeArgs is a fugly way to put the string back into a state ready to tokenize again

        fun Args(
            start: Int = 1, end: Int = -1, escapeArgs: Boolean = false
        ): String { //	char []cmd_args=new char[MAX_COMMAND_STRING];
            var end = end
            var cmd_args = ""
            var i: Int
            if (end < 0) {
                end = argc - 1
            } else if (end >= argc) {
                end = argc - 1
            } //	cmd_args[0] = '\0';
            if (escapeArgs) { //		strcat( cmd_args, "\"" );
                cmd_args += "\""
            }
            i = start
            while (i <= end) {
                if (i > start) {
                    cmd_args += if (escapeArgs) { //				strcat( cmd_args, "\" \"" );
                        "\" \""
                    } else { //				strcat( cmd_args, " " );
                        " "
                    }
                } //		if ( escapeArgs && strchr( argv[i], '\\' ) ) {
                if (escapeArgs && argv[i].contains("\\")) { //			char *p = argv[i];
                    var p = 0
                    while (p < argv[i].length) {
                        if (argv[i][p] == '\\') { //					strcat( cmd_args, "\\\\" );
                            cmd_args += "\\\\"
                        } else {
                            cmd_args.length
                            cmd_args += argv[i][p] //					cmd_args[ l ] = *p;
                            //					cmd_args[ l+1 ] = '\0';
                        }
                        p++
                    }
                } else { //			strcat( cmd_args, argv[i] );
                    cmd_args += argv[i]
                }
                i++
            }
            if (escapeArgs) { //		strcat( cmd_args, "\"" );
                cmd_args += "\""
            }
            return cmd_args
        }

        /*
         ============
         idCmdArgs::TokenizeString

         Parses the given string into command line tokens.
         The text is copied to a separate buffer and 0 characters
         are inserted in the appropriate place. The argv array
         will point into this temporary buffer.
         ============
         // Takes a null terminated string and breaks the string up into arg tokens.
         // Does not need to be /n terminated.
         // Set keepAsStrings to true to only seperate tokens from whitespace and comments, ignoring punctuation
         */
        @Throws(idException::class)
        fun TokenizeString(text: String?, keepAsStrings: Boolean) {
            val lex = idLexer()
            val token = idToken()
            val number = idToken()
            var len: Int
            var totalLen: Int

            // clear previous args
            argc = 0
            if (null == text) {
                return
            }
            lex.LoadMemory(text, text.length, "idCmdSystemLocal::TokenizeString")
            lex.SetFlags(
                Lexer.LEXFL_NOERRORS or Lexer.LEXFL_NOWARNINGS or Lexer.LEXFL_NOSTRINGCONCAT or Lexer.LEXFL_ALLOWPATHNAMES or Lexer.LEXFL_NOSTRINGESCAPECHARS or Lexer.LEXFL_ALLOWIPADDRESSES or if (keepAsStrings) Lexer.LEXFL_ONLYSTRINGS else 0
            )
            totalLen = 0
            while (true) {
                if (argc == MAX_COMMAND_ARGS) {
                    return  // this is usually something malicious
                }
                if (!lex.ReadToken(token)) {
                    return
                }

                // check for negative numbers
                if (!keepAsStrings && token.toString() == "-") {
                    if (lex.CheckTokenType(Token.TT_NUMBER, 0, number) != 0) {
                        token.set("-$number")
                    }
                }

                // check for cvar expansion
                if (token.toString() == "$") {
                    if (!lex.ReadToken(token)) {
                        return
                    }
                    token.set(idLib.cvarSystem.GetCVarString(token.toString()))
                }
                len = token.Length()
                if (totalLen + len + 1 >  /*sizeof(*/tokenized.capacity()) {
                    return  // this is usually something malicious
                }

                //                tokenized.append(token);//damn pointers!
                // regular token
                argv[argc] = tokenized.replace(totalLen, tokenized.capacity(), token.toString()).substring(totalLen)
                argc++

                //                idStr::Copynz( tokenized + totalLen, token.c_str(), sizeof( tokenized ) - totalLen );
                //                tokenized.replace(totalLen, tokenized.capacity() - token.Length(), token.toString());
                totalLen += len //+ 1;//we don't need the '\0'.
            }
        }

        fun AppendArg(text: String) {
            if (0 == argc) {
                argc = 1
                argv[0] = text //		idStr::Copynz( tokenized, text, sizeof( tokenized ) );
                tokenized = StringBuilder(tokenized.capacity()).append(text)
            } else { //              argv[ argc ] = argv[ argc-1 ] + strlen( argv[ argc-1 ] ) + 1;
                //              idStr::Copynz( argv[ argc ], text, sizeof( tokenized ) - ( argv[ argc ] - tokenized ) );
                argv[argc++] = text
            }
        }

        fun Clear() {
            argc = 0
        }

        fun GetArgs(_argc: IntArray): Array<String> {
            _argc[0] = argc
            return argv
        }

        companion object {
            private const val MAX_COMMAND_ARGS = 64
            private val MAX_COMMAND_STRING: Int = 2 * MAX_STRING_CHARS
        }
    }
}
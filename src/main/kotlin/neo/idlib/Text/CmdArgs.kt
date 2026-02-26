package neo.idlib.Text

import neo.idlib.MAX_STRING_CHARS
import neo.idlib.Text.Lexer.idLexer
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Token.idToken
import neo.idlib.idException
import neo.idlib.idLib
import neo.idlib.math.*
import java.util.*

class CmdArgs {
    /*
     ===============================================================================

     Command arguments.

     ===============================================================================
     */
    internal inner class idCmdArgs {
        private val MAX_COMMAND_ARGS = 64
        private val MAX_COMMAND_STRING: Int = 2 * MAX_STRING_CHARS
        private var argc // number of arguments
                = 0
        private val argv: CharArray = CharArray(MAX_COMMAND_ARGS) // points into tokenized
        private val tokenized: CharArray =
            CharArray(MAX_COMMAND_STRING) // will have 0 bytes inserted

        //
        //
        constructor() {
            argc = 0
        }

        constructor(text: String?, keepAsStrings: Boolean) {
            TokenizeString(text, keepAsStrings)
        }

        //
        fun oSet(args: idCmdArgs?) {
            argc = args!!.argc
            //	memcpy( tokenized, args.tokenized, MAX_COMMAND_STRING );
            System.arraycopy(args.tokenized, 0, tokenized, 0, MAX_COMMAND_STRING)
            //            for (i = 0; i < argc; i++) {
//		argv[ i ] = tokenized + ( args.argv[ i ] - args.tokenized );
//            }
            System.arraycopy(args.argv, 0, argv, 0, argc)
        }

        //
        // The functions that execute commands get their parameters with these functions.
        fun Argc(): Int {
            return argc
        }

        // Argv() will return an empty string, not NULL if arg >= argc.
        fun Argv(arg: Int): String {
            return (if (arg >= 0 && arg < argc) argv[arg] else "") as String
        }

        // Returns a single string containing argv(start) to argv(end)
        // escapeArgs is a fugly way to put the string back into a state ready to tokenize again
        //public	String			Args( int start = 1, int end = -1, bool escapeArgs = false ) const;
        fun Args(start: Int, end: Int, escapeArgs: Boolean): String {
//	static char cmd_args[MAX_COMMAND_STRING];
            var end = end
            var cmd_args = ""
            var i: Int
            if (end < 0) {
                end = argc - 1
            } else if (end >= argc) {
                end = argc - 1
            }
            cmd_args += '\u0000'
            if (escapeArgs) {
//		strcat( cmd_args, "\"" );
                cmd_args += "\""
            }
            i = start
            while (i <= end) {
                if (i > start) {
                    cmd_args += if (escapeArgs) {
                        "\" \""
                    } else {
                        " "
                    }
                }
                if (escapeArgs && Arrays.binarySearch(argv, i, argv.size, '\\') != 0) {
                    var p = i
                    while (argv[p] != '\u0000') {
                        if (argv[p] == '\\') {
                            cmd_args += "\\\\"
                        } else {
                            cmd_args.length
                            cmd_args += argv[p]
                            cmd_args += '\u0000'
                        }
                        p++
                    }
                } else {
                    cmd_args += argv[i]
                }
                i++
            }
            if (escapeArgs) {
                cmd_args += "\""
            }
            return cmd_args
        }

        //
        /*
         ============
         idCmdArgs::TokenizeString

         Parses the given string into command line tokens.
         The text is copied to a separate buffer and 0 characters
         are inserted in the appropriate place. The argv array
         will point into this temporary buffer.
         ============
         */
        // Takes a null terminated string and breaks the string up into arg tokens.
        // Does not need to be /n terminated.
        // Set keepAsStrings to true to only seperate tokens from whitespace and comments, ignoring punctuation
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
                Lexer.LEXFL_NOERRORS
                        or Lexer.LEXFL_NOWARNINGS
                        or Lexer.LEXFL_NOSTRINGCONCAT
                        or Lexer.LEXFL_ALLOWPATHNAMES
                        or Lexer.LEXFL_NOSTRINGESCAPECHARS
                        or Lexer.LEXFL_ALLOWIPADDRESSES or if (keepAsStrings) Lexer.LEXFL_ONLYSTRINGS else 0
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
                if (totalLen + len + 1 > tokenized.size) {
                    return  // this is usually something malicious
                }

                // regular token
                argv[argc] = tokenized[totalLen]
                argc++
                val tokenizedClam = clam(tokenized, totalLen)
                idStr.Copynz(tokenizedClam, token.toString(), tokenized.size - totalLen)
                unClam(tokenized, tokenizedClam)
                totalLen += len + 1
            }
        }

        //
        fun AppendArg(text: String) {
            if (0 == argc) {
                argc = 1
                argv[0] = tokenized[0]
                idStr.Copynz(tokenized, text, tokenized.size)
            } else {
                argv[argc] = argv[argc - 1 + (argv.size - argc - 1) + 1]
                val argvClam = clam(argv, argc)
                idStr.Copynz(argvClam, text, tokenized.size - (argv.size - argc - tokenized[0].code))
                unClam(argv, argvClam)
                argc++
            }
        }

        fun Clear() {
            argc = 0
        }

        fun GetArgs(_argc: IntArray): CharArray {
            _argc[0] = argc
            return argv
        }


    }
}
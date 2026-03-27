package neo.framework

import neo.TempDump
import neo.TempDump.void_callback
import neo.framework.CmdSystem.cmdExecution_t
import neo.framework.CmdSystem.cmdFunction_t
import neo.framework.File_h.idFile
import neo.idlib.CmdArgs
import neo.idlib.MAX_STRING_CHARS
import neo.idlib.Text.Str
import neo.idlib.Text.Str.idStr
import neo.idlib.idException
import neo.sys.win_input

object KeyInput {
    /*
     ===============================================================================

     Key Input

     ===============================================================================
     */
    const val K_ACUTE_ACCENT = 180 // accute accent

    //
    const val K_ALT = 140

    //
    const val K_AUX1 = 230
    const val K_AUX10 = 244
    const val K_AUX11 = 245
    const val K_AUX12 = 246
    const val K_AUX13 = 247
    const val K_AUX14 = 248
    const val K_AUX15 = 250
    const val K_AUX16 = 251
    const val K_AUX2 = 233 // FIX: was 231, conflicted with K_CEDILLA_C. C++ auto-increments after K_GRAVE_E=232
    const val K_AUX3 = 234 // FIX: was 232, conflicted with K_GRAVE_E. C++ auto-increments after K_AUX2=233
    const val K_AUX4 = 235 // FIX: was 233, C++ auto-increments after K_AUX3=234
    const val K_AUX5 = 237
    const val K_AUX6 = 238
    const val K_AUX7 = 239
    const val K_AUX8 = 240
    const val K_AUX9 = 243

    //
    const val K_BACKSPACE = 127
    const val K_CAPSLOCK = 129
    const val K_CEDILLA_C = 231 // lowercase c with Cedilla

    //
    const val K_COMMAND = 128
    const val K_CTRL = 141
    const val K_DEL = 144
    const val K_DOWNARROW = 134
    const val K_END = 148
    const val K_ENTER = 13
    const val K_ESCAPE = 27

    //
    const val K_F1 = 149
    const val K_F10 = 158
    const val K_F11 = 159
    const val K_F12 = 160
    const val K_F13 = 162
    const val K_F14 = 163
    const val K_F15 = 164
    const val K_F2 = 150
    const val K_F3 = 151
    const val K_F4 = 152
    const val K_F5 = 153
    const val K_F6 = 154
    const val K_F7 = 155
    const val K_F8 = 156
    const val K_F9 = 157
    const val K_GRAVE_A = 224 // lowercase a with grave accent
    const val K_GRAVE_E = 232 // lowercase e with grave accent
    const val K_GRAVE_I = 236 // lowercase i with grave accent
    const val K_GRAVE_O = 242 // lowercase o with grave accent
    const val K_GRAVE_U = 249 // lowercase u with grave accent
    const val K_HOME = 147
    const val K_INS = 143
    const val K_INVERTED_EXCLAMATION = 161 // upside down !

    //
    const val K_JOY1 = 197
    const val K_JOY10 = 206
    const val K_JOY11 = 207
    const val K_JOY12 = 208
    const val K_JOY13 = 209
    const val K_JOY14 = 210
    const val K_JOY15 = 211
    const val K_JOY16 = 212
    const val K_JOY17 = 213
    const val K_JOY18 = 214
    const val K_JOY19 = 215
    const val K_JOY2 = 198
    const val K_JOY20 = 216
    const val K_JOY21 = 217
    const val K_JOY22 = 218
    const val K_JOY23 = 219
    const val K_JOY24 = 220
    const val K_JOY25 = 221
    const val K_JOY26 = 222
    const val K_JOY27 = 223
    const val K_JOY28 = 225
    const val K_JOY29 = 226
    const val K_JOY3 = 199
    const val K_JOY30 = 227
    const val K_JOY31 = 228
    const val K_JOY32 = 229
    const val K_JOY4 = 200
    const val K_JOY5 = 201
    const val K_JOY6 = 202
    const val K_JOY7 = 203
    const val K_JOY8 = 204
    const val K_JOY9 = 205
    const val K_KP_5 = 169
    const val K_KP_DEL = 176
    const val K_KP_DOWNARROW = 172
    const val K_KP_END = 171
    const val K_KP_ENTER = 174
    const val K_KP_EQUALS = 184 // FIX: was 183, C++ auto-increments after K_KP_STAR=183

    //
    const val K_KP_HOME = 165
    const val K_KP_INS = 175
    const val K_KP_LEFTARROW = 168
    const val K_KP_MINUS = 179
    const val K_KP_NUMLOCK = 182 // FIX: was 181, C++ auto-increments after K_KP_PLUS=181
    const val K_KP_PGDN = 173
    const val K_KP_PGUP = 167
    const val K_KP_PLUS =
        181 // FIX: was 180, conflicted with K_ACUTE_ACCENT. C++ auto-increments after K_ACUTE_ACCENT=180
    const val K_KP_RIGHTARROW = 170
    const val K_KP_SLASH = 177
    const val K_KP_STAR = 183 // FIX: was 182, C++ auto-increments after K_KP_NUMLOCK=182
    const val K_KP_UPARROW = 166
    const val K_LAST_KEY = 254 // this better be < 256!
    const val K_LEFTARROW = 135

    //
    //                          // The 3 windows keys
    const val K_LWIN = 137

    //
    const val K_MASCULINE_ORDINATOR = 186
    const val K_MENU = 139

    //                       // K_MOUSE enums must be contiguous (no char codes in the middle)
    const val K_MOUSE1 = 187
    const val K_MOUSE2 = 188
    const val K_MOUSE3 = 189
    const val K_MOUSE4 = 190
    const val K_MOUSE5 = 191
    const val K_MOUSE6 = 192
    const val K_MOUSE7 = 193
    const val K_MOUSE8 = 194

    //
    const val K_MWHEELDOWN = 195
    const val K_MWHEELUP = 196
    const val K_PAUSE = 132
    const val K_PGDN = 145
    const val K_PGUP = 146
    const val K_POWER = 131

    //
    const val K_PRINT_SCR = 252 // SysRq / PrintScr
    const val K_RIGHTARROW = 136
    const val K_RIGHT_ALT = 253 // used by some languages as "Alt-Gr"
    const val K_RIGHT_CTRL = 254
    const val K_RIGHT_SHIFT = 255
    const val K_RWIN = 138
    const val K_SCROLL = 130
    const val K_SHIFT = 142
    const val K_SPACE = 32
    const val K_SUPERSCRIPT_TWO = 178 // superscript 2

    // these are the key numbers that are used by the key system
    // normal keys should be passed as lowercased ascii
    // Some high ascii (> 127) characters that are mapped directly to keys on
    // western european keyboards are inserted in this table so that those keys
    // are bindable (otherwise they get bound as one of the special keys in this table)
    const val K_TAB = 9
    const val K_TILDE_N = 241 // lowercase n with tilde
    //
    const val K_UPARROW = 133
    //
    const val ID_DOOM_LEGACY = false // FIX: was false, but C++ always #defines ID_DOOM_LEGACY
    //
    const val MAX_KEYS = 256
    //
    val cheatCodes: Array<String?> = arrayOf(
        "iddqd",  // Invincibility
        "idkfa",  // All weapons, keys, ammo, and 200% armor
        "idfa",  // Reset ammunition
        "idspispopd",  // Walk through walls
        "idclip",  // Walk through walls
        "idchoppers",  // Chainsaw
        /*
         "idbeholds",	// Berserker strength
         "idbeholdv",	// Temporary invincibility
         "idbeholdi",	// Temporary invisibility
         "idbeholda",	// Full automap
         "idbeholdr",	// Anti-radiation suit
         "idbeholdl",	// Light amplification visor
         "idclev",		// Level select
         "iddt",			// Toggle full map; full map and objects; normal map
         "idmypos",		// Display coordinates and heading
         "idmus",		// Change music to indicated level
         "fhhall",		// Kill all enemies in level
         "fhshh",		// Invisible to enemies until attack
         */
        null
    )

    // names not in this list can either be lowercase ascii, or '0xnn' hex sequences
    val keynames: Array<keyname_t> = arrayOf(
        keyname_t("TAB", K_TAB, "#str_07018"),
        keyname_t("ENTER", K_ENTER, "#str_07019"),
        keyname_t("ESCAPE", K_ESCAPE, "#str_07020"),
        keyname_t("SPACE", K_SPACE, "#str_07021"),
        keyname_t("BACKSPACE", K_BACKSPACE, "#str_07022"),
        keyname_t("UPARROW", K_UPARROW, "#str_07023"),
        keyname_t("DOWNARROW", K_DOWNARROW, "#str_07024"),
        keyname_t("LEFTARROW", K_LEFTARROW, "#str_07025"),
        keyname_t("RIGHTARROW", K_RIGHTARROW, "#str_07026"),  //
        keyname_t("ALT", K_ALT, "#str_07027"),
        keyname_t("RIGHTALT", K_RIGHT_ALT, "#str_07027"),
        keyname_t("CTRL", K_CTRL, "#str_07028"),
        keyname_t("SHIFT", K_SHIFT, "#str_07029"),  //
        keyname_t("LWIN", K_LWIN, "#str_07030"),
        keyname_t("RWIN", K_RWIN, "#str_07031"),
        keyname_t("MENU", K_MENU, "#str_07032"),  //
        keyname_t("COMMAND", K_COMMAND, "#str_07033"),  //
        keyname_t("CAPSLOCK", K_CAPSLOCK, "#str_07034"),
        keyname_t("SCROLL", K_SCROLL, "#str_07035"),
        keyname_t("PRINTSCREEN", K_PRINT_SCR, "#str_07179"),  //
        keyname_t("F1", K_F1, "#str_07036"),
        keyname_t("F2", K_F2, "#str_07037"),
        keyname_t("F3", K_F3, "#str_07038"),
        keyname_t("F4", K_F4, "#str_07039"),
        keyname_t("F5", K_F5, "#str_07040"),
        keyname_t("F6", K_F6, "#str_07041"),
        keyname_t("F7", K_F7, "#str_07042"),
        keyname_t("F8", K_F8, "#str_07043"),
        keyname_t("F9", K_F9, "#str_07044"),
        keyname_t("F10", K_F10, "#str_07045"),
        keyname_t("F11", K_F11, "#str_07046"),
        keyname_t("F12", K_F12, "#str_07047"),  //
        keyname_t("INS", K_INS, "#str_07048"),
        keyname_t("DEL", K_DEL, "#str_07049"),
        keyname_t("PGDN", K_PGDN, "#str_07050"),
        keyname_t("PGUP", K_PGUP, "#str_07051"),
        keyname_t("HOME", K_HOME, "#str_07052"),
        keyname_t("END", K_END, "#str_07053"),  //
        keyname_t("MOUSE1", K_MOUSE1, "#str_07054"),
        keyname_t("MOUSE2", K_MOUSE2, "#str_07055"),
        keyname_t("MOUSE3", K_MOUSE3, "#str_07056"),
        keyname_t("MOUSE4", K_MOUSE4, "#str_07057"),
        keyname_t("MOUSE5", K_MOUSE5, "#str_07058"),
        keyname_t("MOUSE6", K_MOUSE6, "#str_07059"),
        keyname_t("MOUSE7", K_MOUSE7, "#str_07060"),
        keyname_t("MOUSE8", K_MOUSE8, "#str_07061"),  //
        keyname_t("MWHEELUP", K_MWHEELUP, "#str_07131"),
        keyname_t("MWHEELDOWN", K_MWHEELDOWN, "#str_07132"),  //
        keyname_t("JOY1", K_JOY1, "#str_07062"),
        keyname_t("JOY2", K_JOY2, "#str_07063"),
        keyname_t("JOY3", K_JOY3, "#str_07064"),
        keyname_t("JOY4", K_JOY4, "#str_07065"),
        keyname_t("JOY5", K_JOY5, "#str_07066"),
        keyname_t("JOY6", K_JOY6, "#str_07067"),
        keyname_t("JOY7", K_JOY7, "#str_07068"),
        keyname_t("JOY8", K_JOY8, "#str_07069"),
        keyname_t("JOY9", K_JOY9, "#str_07070"),
        keyname_t("JOY10", K_JOY10, "#str_07071"),
        keyname_t("JOY11", K_JOY11, "#str_07072"),
        keyname_t("JOY12", K_JOY12, "#str_07073"),
        keyname_t("JOY13", K_JOY13, "#str_07074"),
        keyname_t("JOY14", K_JOY14, "#str_07075"),
        keyname_t("JOY15", K_JOY15, "#str_07076"),
        keyname_t("JOY16", K_JOY16, "#str_07077"),
        keyname_t("JOY17", K_JOY17, "#str_07078"),
        keyname_t("JOY18", K_JOY18, "#str_07079"),
        keyname_t("JOY19", K_JOY19, "#str_07080"),
        keyname_t("JOY20", K_JOY20, "#str_07081"),
        keyname_t("JOY21", K_JOY21, "#str_07082"),
        keyname_t("JOY22", K_JOY22, "#str_07083"),
        keyname_t("JOY23", K_JOY23, "#str_07084"),
        keyname_t("JOY24", K_JOY24, "#str_07085"),
        keyname_t("JOY25", K_JOY25, "#str_07086"),
        keyname_t("JOY26", K_JOY26, "#str_07087"),
        keyname_t("JOY27", K_JOY27, "#str_07088"),
        keyname_t("JOY28", K_JOY28, "#str_07089"),
        keyname_t("JOY29", K_JOY29, "#str_07090"),
        keyname_t("JOY30", K_JOY30, "#str_07091"),
        keyname_t("JOY31", K_JOY31, "#str_07092"),
        keyname_t("JOY32", K_JOY32, "#str_07093"),  //
        keyname_t("AUX1", K_AUX1, "#str_07094"),
        keyname_t("AUX2", K_AUX2, "#str_07095"),
        keyname_t("AUX3", K_AUX3, "#str_07096"),
        keyname_t("AUX4", K_AUX4, "#str_07097"),
        keyname_t("AUX5", K_AUX5, "#str_07098"),
        keyname_t("AUX6", K_AUX6, "#str_07099"),
        keyname_t("AUX7", K_AUX7, "#str_07100"),
        keyname_t("AUX8", K_AUX8, "#str_07101"),
        keyname_t("AUX9", K_AUX9, "#str_07102"),
        keyname_t("AUX10", K_AUX10, "#str_07103"),
        keyname_t("AUX11", K_AUX11, "#str_07104"),
        keyname_t("AUX12", K_AUX12, "#str_07105"),
        keyname_t("AUX13", K_AUX13, "#str_07106"),
        keyname_t("AUX14", K_AUX14, "#str_07107"),
        keyname_t("AUX15", K_AUX15, "#str_07108"),
        keyname_t("AUX16", K_AUX16, "#str_07109"),  //
        keyname_t("KP_HOME", K_KP_HOME, "#str_07110"),
        keyname_t("KP_UPARROW", K_KP_UPARROW, "#str_07111"),
        keyname_t("KP_PGUP", K_KP_PGUP, "#str_07112"),
        keyname_t("KP_LEFTARROW", K_KP_LEFTARROW, "#str_07113"),
        keyname_t("KP_5", K_KP_5, "#str_07114"),
        keyname_t("KP_RIGHTARROW", K_KP_RIGHTARROW, "#str_07115"),
        keyname_t("KP_END", K_KP_END, "#str_07116"),
        keyname_t("KP_DOWNARROW", K_KP_DOWNARROW, "#str_07117"),
        keyname_t("KP_PGDN", K_KP_PGDN, "#str_07118"),
        keyname_t("KP_ENTER", K_KP_ENTER, "#str_07119"),
        keyname_t("KP_INS", K_KP_INS, "#str_07120"),
        keyname_t("KP_DEL", K_KP_DEL, "#str_07121"),
        keyname_t("KP_SLASH", K_KP_SLASH, "#str_07122"),
        keyname_t("KP_MINUS", K_KP_MINUS, "#str_07123"),
        keyname_t("KP_PLUS", K_KP_PLUS, "#str_07124"),
        keyname_t("KP_NUMLOCK", K_KP_NUMLOCK, "#str_07125"),
        keyname_t("KP_STAR", K_KP_STAR, "#str_07126"),
        keyname_t("KP_EQUALS", K_KP_EQUALS, "#str_07127"),  //
        keyname_t("PAUSE", K_PAUSE, "#str_07128"),  //
        keyname_t("SEMICOLON", ';'.code, "#str_07129"),  // because a raw semicolon separates commands
        keyname_t("APOSTROPHE", '\''.code, "#str_07130"),  // because a raw apostrophe messes with parsing
        //
        keyname_t(null, 0, null)
    )

    //
    // keys that can be set without a special name
    val unnamedkeys: String = "*,-=./[\\]1234567890abcdefghijklmnopqrstuvwxyz"

    var key_overstrikeMode = false
    var keys: Array<idKey> = Array(MAX_KEYS) { idKey() }
    var lastKeyIndex = 0

    //
    var lastKeys: CharArray = CharArray(32)

    object idKeyInput {
        /*
         ===================
         idKeyInput::KeyNumToString

         Returns a string (either a single ascii char, a K_* name, or a 0x11 hex string) for the
         given keynum.
         ===================
         */
        var tinystr: CharArray = CharArray(5)

        /*
         ============
         idKeyInput::KeysFromBinding
         returns the localized name of the key for the binding
         ============
         */
        private val keyName: CharArray = CharArray(MAX_STRING_CHARS)

        @Throws(idException::class)
        fun Init() {
            keys = Array(MAX_KEYS) { idKey() }

            // register our functions
            CmdSystem.cmdSystem.AddCommand(
                "bind",
                Key_Bind_f.getInstance(),
                CmdSystem.CMD_FL_SYSTEM,
                "binds a command to a key",
                ArgCompletion_KeyName.getInstance()
            )
            CmdSystem.cmdSystem.AddCommand(
                "bindunbindtwo",
                Key_BindUnBindTwo_f.getInstance(),
                CmdSystem.CMD_FL_SYSTEM,
                "binds a key but unbinds it first if there are more than two binds"
            )
            CmdSystem.cmdSystem.AddCommand(
                "unbind",
                Key_Unbind_f.getInstance(),
                CmdSystem.CMD_FL_SYSTEM,
                "unbinds any command from a key",
                ArgCompletion_KeyName.getInstance()
            )
            CmdSystem.cmdSystem.AddCommand(
                "unbindall",
                Key_Unbindall_f.getInstance(),
                CmdSystem.CMD_FL_SYSTEM,
                "unbinds any commands from all keys"
            )
            CmdSystem.cmdSystem.AddCommand(
                "listBinds",
                Key_ListBinds_f.getInstance(),
                CmdSystem.CMD_FL_SYSTEM,
                "lists key bindings"
            )
        }

        fun Shutdown() {
//	delete [] keys;
        }

        /*
         ===================
         idKeyInput::PreliminaryKeyEvent

         Tracks global key up/down state
         Called by the system for both key up and key down events
         ===================
         */
        @Throws(idException::class)
        fun PreliminaryKeyEvent(keyNum: Int, down: Boolean) {
            keys[keyNum].down = down
            if (ID_DOOM_LEGACY) {
                // FIX: added keyNum < 127 check — only ASCII keys are of interest for cheat codes
                if (down && keyNum < 127) {
                    lastKeys[0 + (lastKeyIndex and 15)] = keyNum.toChar()
                    lastKeys[16 + (lastKeyIndex and 15)] = keyNum.toChar()
                    lastKeyIndex = lastKeyIndex + 1 and 15
                    var i = 0
                    while (cheatCodes[i] != null) {
                        val l = cheatCodes[i]!!.length
                        assert(l <= 16)
                        if (idStr.Icmpn(
                                TempDump.ctos(lastKeys).substring(16 + (lastKeyIndex and 15) - l),
                                cheatCodes[i]!!,
                                l
                            ) == 0
                        ) {
                            Common.common.Printf("your memory serves you well!\n")
                            break
                        }
                        i++
                    }
                }
            }
        }


        fun IsDown(keyNum: Int): Boolean {
            if (keyNum == -1) {
                return false
            }

            // FIX: K_RIGHT_CTRL/SHIFT should be handled as different keys for bindings
            // but the same for keyboard shortcuts in the console and such
            // (this function is used for the latter)
            if (keyNum == K_CTRL) {
                return keys[K_CTRL].down || keys[K_RIGHT_CTRL].down
            } else if (keyNum == K_SHIFT) {
                return keys[K_SHIFT].down || keys[K_RIGHT_SHIFT].down
            }

            return keys[keyNum].down
        }

        fun GetUsercmdAction(keyNum: Int): Int {
            return keys[keyNum].usercmdAction
        }

        fun GetOverstrikeMode(): Boolean {
            return key_overstrikeMode
        }

        fun SetOverstrikeMode(state: Boolean) {
            key_overstrikeMode = state
        }

        @Throws(idException::class)
        fun ClearStates() {
            var i: Int
            i = 0
            while (i < MAX_KEYS) {
                if (keys[i].down) {
                    PreliminaryKeyEvent(i, false)
                }
                keys[i].down = false
                i++
            }

            // clear the usercommand states
            UsercmdGen.usercmdGen.Clear()
        }

        /*
         ===================
         idKeyInput::StringToKeyNum

         Returns a key number to be used to index keys[] by looking at
         the given string.  Single ascii characters return themselves, while
         the K_* names are matched up.

         0x11 will be interpreted as raw hex, which will allow new controlers
         to be configured even if they don't have defined names.
         ===================
         */
        fun StringToKeyNum(str: String?): Int {
            var kn: Int
            if (null == str || str.isEmpty()) {
                return -1
            }
            if (1 == str.length) {
                return str[0].code
            }

            // check for hex code
            // FIX: was str[0] == 'x' (always false since str[0] can't be both '0' and 'x')
            if (str[0] == '0' && str[1] == 'x' && str.length == 4) {
                var n1: Int
                var n2: Int
                n1 = str[2].code
                if (n1 >= '0'.code && n1 <= '9'.code) {
                    n1 -= '0'.code
                } else if (n1 >= 'a'.code && n1 <= 'f'.code) {
                    n1 = n1 - 'a'.code + 10
                } else {
                    n1 = 0
                }
                n2 = str[3].code
                if (n2 >= '0'.code && n2 <= '9'.code) {
                    n2 -= '0'.code
                } else if (n2 >= 'a'.code && n2 <= 'f'.code) {
                    n2 = n2 - 'a'.code + 10
                } else {
                    n2 = 0
                }
                return n1 * 16 + n2
            }

            // scan for a text match
            kn = 0
            while (kn < keynames.size) {
                if (0 == idStr.Icmp(str, keynames[kn].name!!)) {
                    return keynames[kn].keynum
                }
                kn++
            }
            return -1
        }

        @Throws(idException::class)
        fun KeyNumToString(keyNum: Int, localized: Boolean): String? {
//	keyname_t	kn;
            val i: Int
            val j: Int
            if (keyNum == -1) {
                return "<KEY NOT FOUND>"
            }
            if (keyNum < 0 || keyNum > 255) {
                return "<OUT OF RANGE>"
            }

            // check for printable ascii (don't use quote)
            if (keyNum > 32 && keyNum < 127 && keyNum != '"'.code && keyNum != ';'.code && keyNum != '\''.code) {
                tinystr[0] = win_input.Sys_MapCharForKey(keyNum)
                tinystr[1] = Char(0)
                return TempDump.ctos(tinystr)
            }

            // check for a key string
            for (kn in keynames) {
                if (keyNum == kn.keynum) {
                    return if (!localized || kn.strId!![0] != '#') {
                        kn.name
                    } else {
                        val locStr = Common.common.GetLanguageDict().GetString(kn.strId)
                        // fall back to key name if the localized string wasn't found
                        if (locStr.startsWith("#str_")) kn.name else locStr
                    }
                }
            }

            // check for European high-ASCII characters
            if (localized && keyNum >= 161 && keyNum <= 255) {
                tinystr[0] = keyNum.toChar()
                tinystr[1] = Char(0)
                return TempDump.ctos(tinystr)
            }

            // make a hex string
            i = keyNum shr 4
            j = keyNum and 15
            tinystr[0] = '0'
            tinystr[1] = 'x'
            tinystr[2] = (if (i > 9) i - 10 + 'a'.code else i + '0'.code).toChar()
            tinystr[3] = (if (j > 9) j - 10 + 'a'.code else j + '0'.code).toChar()
            tinystr[4] = Char(0)
            return TempDump.ctos(tinystr)
        }

        fun SetBinding(keyNum: Int, binding: String) {
            if (keyNum == -1) {
                return
            }

            // Clear out all button states so we aren't stuck forever thinking this key is held down
            UsercmdGen.usercmdGen.Clear()

            // allocate memory for new binding
            keys[keyNum].binding.set(binding)

            // find the action for the async command generation
            keys[keyNum].usercmdAction = UsercmdGen.usercmdGen.CommandStringUsercmdData(binding)

            // consider this like modifying an archived cvar, so the
            // file write will be triggered at the next oportunity
            CVarSystem.cvarSystem.SetModifiedFlags(CVarSystem.CVAR_ARCHIVE)
        }

        fun GetBinding(keyNum: Int): String {
            return if (keyNum == -1) {
                ""
            } else keys[keyNum].binding.toString()
        }

        fun UnbindBinding(binding: String?): Boolean {
            var unbound = false
            var i: Int
            // FIX: C++ checks binding && *binding (non-null AND non-empty)
            if (binding != null && binding.isNotEmpty()) {
                i = 0
                while (i < MAX_KEYS) {
                    if (keys[i].binding.Icmp(binding) == 0) {
                        SetBinding(i, "")
                        unbound = true
                    }
                    i++
                }
            }
            return unbound
        }

        fun NumBinds(binding: String?): Int {
            var i: Int
            var count = 0
            // FIX: C++ checks binding && *binding (non-null AND non-empty)
            if (binding != null && binding.isNotEmpty()) {
                i = 0
                while (i < MAX_KEYS) {
                    if (keys[i].binding.Icmp(binding) == 0) {
                        count++
                    }
                    i++
                }
            }
            return count
        }

        @Throws(idException::class)
        fun ExecKeyBinding(keyNum: Int): Boolean {
            // commands that are used by the async thread
            // don't add text
            if (keys[keyNum].usercmdAction != 0) {
                return false
            }

            // send the bound action
            if (keys[keyNum].binding.Length() != 0) {
                CmdSystem.cmdSystem.BufferCommandText(
                    cmdExecution_t.CMD_EXEC_APPEND,
                    keys[keyNum].binding.toString()
                )
                CmdSystem.cmdSystem.BufferCommandText(cmdExecution_t.CMD_EXEC_APPEND, "\n")
            }
            return true
        }

        @Throws(idException::class)
        fun KeysFromBinding(bind: String?): String {
            var i: Int
            keyName[0] = '\u0000'
            if (!bind.isNullOrEmpty()) {
                i = 0
                while (i < MAX_KEYS) {
                    if (keys[i].binding.Icmp(bind) == 0) {
                        if (keyName[0] != '\u0000') {
                            val sep = Common.common.GetLanguageDict().GetString("#str_07183")
                            idStr.Append(
                                keyName,
                                MAX_STRING_CHARS,
                                if (sep.startsWith("#str_")) " or " else sep
                            )
                        }
                        idStr.Append(keyName, keyName.size, KeyNumToString(i, true)!!)
                    }
                    i++
                }
            }
            if (keyName[0] == '\u0000') {
                val none = Common.common.GetLanguageDict().GetString("#str_07133")
                idStr.Copynz(keyName, if (none.startsWith("#str_")) "<none>" else none, keyName.size)
            }
            idStr.ToLower(keyName)
            return TempDump.ctos(keyName)
        }

        /*
         ============
         idKeyInput::BindingFromKey
         returns the binding for the localized name of the key
         ============
         */
        fun BindingFromKey(key: String?): String? {
            val keyNum = StringToKeyNum(key)
            return if (keyNum < 0 || keyNum >= MAX_KEYS) {
                null
            } else keys[keyNum].binding.toString()
        }

        fun KeyIsBoundTo(keyNum: Int, binding: String): Boolean {
            return if (keyNum >= 0 && keyNum < MAX_KEYS) {
                keys[keyNum].binding.Icmp(binding) == 0
            } else false
        }

        /*
         ============
         idKeyInput::WriteBindings

         Writes lines containing "bind key value"
         ============
         */
        @Throws(idException::class)
        fun WriteBindings(f: idFile) {
            var i: Int
            f.Printf("unbindall\n")
            i = 0
            while (i < MAX_KEYS) {
                if (keys[i].binding.Length() != 0) {
                    val name = KeyNumToString(i, false)

                    // handle the escape character nicely
                    if ("\\" == name) {
                        f.Printf("bind \"\\\" \"%s\"\n", keys[i].binding)
                    } else {
                        f.Printf("bind \"%s\" \"%s\"\n", KeyNumToString(i, false)!!, keys[i].binding)
                    }
                }
                i++
            }
        }

        class ArgCompletion_KeyName : CmdSystem.argCompletion_t() {
            @Throws(idException::class)
            override fun run(args: CmdArgs.idCmdArgs?, callback: void_callback<String>) {
                var kn: Int
                var i: Int
                i = 0
                while (i < unnamedkeys.length - 1) {
                    callback.run(Str.va("%s %c", args!!.Argv(0), unnamedkeys[i]))
                    i++
                }
                kn = 0
                while (kn < keynames.size) {
                    callback.run(Str.va("%s %s", args!!.Argv(0), keynames[kn].name!!))
                    kn++
                }
            }

            companion object {
                private val instance: CmdSystem.argCompletion_t = ArgCompletion_KeyName()
                fun getInstance(): CmdSystem.argCompletion_t {
                    return instance
                }
            }
        }
    }

    class keyname_t(
        var name: String?, var keynum: Int, // localized string id
        var strId: String?
    )

    class idKey {
        val binding: idStr = idStr()
        var down = false
        var repeats // if > 1, it is autorepeating
                = 0
        var usercmdAction // for testing by the asyncronous usercmd generation
                : Int = 0
    }

    /////////////////////////////
    /*
     ===================
     Key_Unbind_f
     ===================
     */
    internal class Key_Unbind_f : cmdFunction_t() {
        @Throws(idException::class)
        override fun run(args: CmdArgs.idCmdArgs?) {
            val b: Int
            if (args!!.Argc() != 2) {
                Common.common.Printf("unbind <key> : remove commands from a key\n")
                return
            }
            b = idKeyInput.StringToKeyNum(args.Argv(1))
            if (b == -1) {
                // If it wasn't a key, it could be a command
                if (!idKeyInput.UnbindBinding(args.Argv(1))) {
                    Common.common.Printf("\"%s\" isn't a valid key\n", args.Argv(1))
                }
            } else {
                idKeyInput.SetBinding(b, "")
            }
        }

        companion object {
            private val instance: cmdFunction_t = Key_Unbind_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }

    /*
     ===================
     Key_Unbindall_f
     ===================
     */
    internal class Key_Unbindall_f : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            var i: Int
            i = 0
            while (i < MAX_KEYS) {
                idKeyInput.SetBinding(i, "")
                i++
            }
        }

        companion object {
            private val instance: cmdFunction_t = Key_Unbindall_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }

    /*
     ===================
     Key_Bind_f
     ===================
     */
    internal class Key_Bind_f : cmdFunction_t() {
        @Throws(idException::class)
        override fun run(args: CmdArgs.idCmdArgs?) {
            var i: Int
            val c: Int
            val b: Int
            var cmd: String? //= new char[MAX_STRING_CHARS];
            c = args!!.Argc()
            if (c < 2) {
                Common.common.Printf("bind <key> [command] : attach a command to a key\n")
                return
            }
            b = idKeyInput.StringToKeyNum(args.Argv(1))
            if (b == -1) {
                Common.common.Printf("\"%s\" isn't a valid key\n", args.Argv(1))
                return
            }
            if (c == 2) {
                if (keys[b].binding.Length() != 0) {
                    Common.common.Printf("\"%s\" = \"%s\"\n", args.Argv(1), keys[b].binding.toString())
                } else {
                    Common.common.Printf("\"%s\" is not bound\n", args.Argv(1))
                }
                return
            }

            // copy the rest of the command line
            cmd = "" //[0] = 0;		// start out with a null string
            //            String cmd_str=new String (cmd);
            i = 2
            while (i < c) {
                cmd += args.Argv(i)
                if (i != c - 1) {
                    cmd += " "
                }
                i++
            }
            idKeyInput.SetBinding(b, cmd)
        }

        companion object {
            private val instance: cmdFunction_t = Key_Bind_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }

    /*
     ============
     Key_BindUnBindTwo_f

     binds keynum to bindcommand and unbinds if there are already two binds on the key
     ============
     */
    internal class Key_BindUnBindTwo_f : cmdFunction_t() {
        @Throws(idException::class)
        override fun run(args: CmdArgs.idCmdArgs?) {
            val c = args!!.Argc()
            if (c < 3) {
                Common.common.Printf("bindunbindtwo <keynum> [command]\n")
                return
            }
            val key = args.Argv(1).toInt()
            val bind = args.Argv(2)
            if (idKeyInput.NumBinds(bind) >= 2 && !idKeyInput.KeyIsBoundTo(key, bind)) {
                idKeyInput.UnbindBinding(bind)
            }
            idKeyInput.SetBinding(key, bind)
        }

        companion object {
            private val instance: cmdFunction_t = Key_BindUnBindTwo_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }

    /*
     ============
     Key_ListBinds_f
     ============
     */
    internal class Key_ListBinds_f : cmdFunction_t() {
        @Throws(idException::class)
        override fun run(args: CmdArgs.idCmdArgs?) {
            var i: Int
            i = 0
            while (i < MAX_KEYS) {
                if (keys[i].binding.Length() != 0) {
                    Common.common.Printf(
                        "%s \"%s\"\n",
                        idKeyInput.KeyNumToString(i, false)!!,
                        keys[i].binding.toString()
                    )
                }
                i++
            }
        }

        companion object {
            private val instance: cmdFunction_t = Key_ListBinds_f()
            fun getInstance(): cmdFunction_t {
                return instance
            }
        }
    }
}
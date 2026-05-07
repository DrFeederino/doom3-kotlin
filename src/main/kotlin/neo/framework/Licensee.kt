/*
 * Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
 * Translated to Kotlin by Dr. Feederino with support of Claude Code
 *
 * This file is part of the Doom 3 Kotlin project.
 * Original source: neo/framework/Licensee.h
 *
 * Doom 3 Source Code is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Doom 3 Source Code is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 */
package neo.framework

/*
 ===============================================================================

     Definitions for information that is related to a licensee's game name and location.

 ===============================================================================
 */
object Licensee {

    const val GAME_NAME: String = "dhewm 3"                            // appears on window titles and errors
    const val ENGINE_VERSION: String = "dhewm3 1.5.5rc3"           // printed in console
    const val D3_OSTYPE = "windows" // we are faking it
    const val D3_ARCH = "x86_64"
    const val D3_SHORT_SIZE: Short = 8

    // NOTE: Differs from C++ "dhewm3 1.5.3pre" — intentional for this port

    // paths
    const val BASE_GAMEDIR: String = "base"

    // filenames
    const val CONFIG_FILE: String = "dhewm.cfg"

    // base folder where the source code lives
    const val SOURCE_CODE_BASE_FOLDER: String = "neo"

    // default idnet host address
    const val IDNET_HOST: String = "idnet.ua-corp.com"

    // default idnet master port
    // NOTE: C++ defines as string "27650"; Kotlin uses Int which works for both
    // port assignment and string concatenation in AsyncNetwork
    const val IDNET_MASTER_PORT = 27650

    // default network server port
    const val PORT_SERVER = 27666

    // broadcast scan this many ports after PORT_SERVER so a single machine can run multiple servers
    const val NUM_SERVER_PORTS = 4

    // see ASYNC_PROTOCOL_VERSION
    // use a different major for each game
    const val ASYNC_PROTOCOL_MAJOR = 1

    // Savegame Version
    // Update when you can no longer maintain compatibility with previous savegames
    // NOTE: a separate core savegame version and game savegame version could be useful
    // 16: Doom v1.1
    // 17: Doom v1.2 / D3XP. Can still read old v16 with defaults for new data
    // 18: dhewm3 additions
    // NOTE: Differs from C++ reference which has 17 — Kotlin port uses 18
    const val SAVEGAME_VERSION = 18

    // <= Doom v1.1: 1. no DS_VERSION token ( default )
    // Doom v1.2: 2
    const val RENDERDEMO_VERSION = 2

    // editor info
    const val EDITOR_DEFAULT_PROJECT: String = "doom.qe4"
    const val EDITOR_REGISTRY_KEY: String = "DOOMRadiant"
    const val EDITOR_WINDOWTEXT: String = "DOOMEdit"

    // win32 info
    // NOTE: Differs from C++ "dhewm 3 WinConsole" — uses original id Software name
    const val WIN32_CONSOLE_CLASS: String = "DOOM 3 WinConsole"

    // NOTE: Kotlin-only, no C++ counterpart in Licensee.h
    const val WIN32_FAKE_WINDOW_CLASS_NAME: String = "DOOM3_WGL_FAKE"

    // NOTE: Kotlin-only, no C++ counterpart in Licensee.h
    const val WIN32_WINDOW_CLASS_NAME: String = "DOOM3"

    // Linux info
    const val LINUX_DEFAULT_PATH: String = "/usr/local/games/doom3"

    // CD Key file info
    // goes into BASE_GAMEDIR whatever the fs_game is set to
    // two distinct files for easier win32 installer job
    const val CDKEY_FILE: String = "doomkey"
    const val XPKEY_FILE: String = "xpkey"
    val CDKEY_TEXT: String = "\n// Do not give this file to ANYONE.\n" +
            "// id Software or Zenimax will NEVER ask you to send this file to them.\n"

    const val CONFIG_SPEC: String = "config.spec"

    // NOTE: Kotlin-only, no C++ counterpart in Licensee.h
    const val CD_BASEDIR: String = "Doom"

    // NOTE: Kotlin-only, no C++ counterpart in Licensee.h
    const val CD_EXE: String = "doom.exe"
}

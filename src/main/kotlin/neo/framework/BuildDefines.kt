/*
 * Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
 * Translated to Kotlin by Dr. Feederino with support of Claude Code
 *
 * This file is part of the Doom 3 Kotlin project.
 * Original source: neo/framework/BuildDefines.h
 *
 * Doom 3 Source Code is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package neo.framework

/*
===============================================================================

	Preprocessor settings for compiling different versions.

===============================================================================
*/


// platform detection
private val _WIN32 = System.getProperty("os.name").startsWith("Windows")
val WIN32 = _WIN32
private val _MACOSX = System.getProperty("os.name").contains("Mac")
val MACOS_X = _MACOSX
val __linux__ = System.getProperty("os.name") == "Linux"

// debug mode
const val _DEBUG = true

// memory debugging
//#define ID_REDIRECT_NEWDELETE
//#define ID_DEBUG_MEMORY
//#define ID_DEBUG_UNINITIALIZED_MEMORY

// if enabled, the console won't toggle upon ~, unless you start the binary with +set com_allowConsole 1
// Ctrl+Alt+~ will always toggle the console no matter what
// DG: dhewm3 always unlocks console (was platform-conditional in original)
const val ID_CONSOLE_LOCK = false

// useful for network debugging, turns off 'LAN' checks, all IPs are classified 'internet'
const val ID_NOLANADDRESS = false

// let .dds be loaded from FS without altering pure state. only for developement.
const val ID_PURE_ALLOWDDS = false

// build an exe with no CVAR_CHEAT controls
const val ID_ALLOW_CHEATS = false

// verify checksums in clientinfo traffic
// NOTE: this makes the network protocol incompatible
const val ID_CLIENTINFO_TAGS = false

// for win32 this is defined in preprocessor settings so that MFC can be
// compiled out.
const val ID_DEDICATED = false

// don't define ID_ALLOW_TOOLS when we don't want tool code in the executable.
// DG: defined in cmake now — disabled for Kotlin/JVM port
const val ID_ALLOW_TOOLS = false

const val ID_ALLOW_D3XP = true

// FIX: dhewm3 hardcodes ID_ENFORCE_KEY to 0 — the Kotlin version was incorrectly computing it
const val ID_ENFORCE_KEY = false

const val ID_ENFORCE_KEY_CLIENT = false

// if this is defined, the executable positively won't work with any paks other
// than the demo pak, even if productid is present.
val ID_DEMO_BUILD = System.getProperty("ID_DEMO_BUILD") == "true"

const val ID_ENABLE_CURL = true

// fake a pure client. useful to connect an all-debug client to a server
// dhewm3 removed all DLL pure checksum logic for monolithic builds - same effect
const val ID_FAKE_PURE = true // monolithic build has no game DLL, skip game pak checksum

const val ID_OPENAL = true

// DG: dhewm3 removed backtrace logic entirely
const val ID_BT_STUB = true
package neo.framework


object BuildDefines {
    // build an exe with no CVAR_CHEAT controls
    const val ID_ALLOW_CHEATS = false
    const val ID_ALLOW_D3XP = true

    // don't define ID_ALLOW_TOOLS when we don't want tool code in the executable.
    const val ID_ALLOW_TOOLS = false
    var ID_BT_STUB = false

    // verify checksums in clientinfo traffic
    // NOTE: this makes the network protocol incompatible
    const val ID_CLIENTINFO_TAGS = false

    /*
     ===============================================================================

     Preprocessor settings for compiling different versions.

     ===============================================================================
     */
    // memory debugging
    //#define ID_REDIRECT_NEWDELETE
    //#define ID_DEBUG_MEMORY
    //#define ID_DEBUG_UNINITIALIZED_MEMORY
    // if enabled, the console won't toggle upon ~, unless you start the binary with +set com_allowConsole 1
    // Ctrl+Alt+~ will always toggle the console no matter what
    var ID_CONSOLE_LOCK = false

    // for win32 this is defined in preprocessor settings so that MFC can be
    // compiled out.
    const val ID_DEDICATED = false

    // if this is defined, the executable positively won't work with any paks other
    // than the demo pak, even if productid is present.
    val ID_DEMO_BUILD = System.getProperty("ID_DEMO_BUILD") == "true"
    const val ID_ENABLE_CURL = true
    var ID_ENFORCE_KEY = false

    // fake a pure client. useful to connect an all-debug client to a server
    const val ID_FAKE_PURE = false

    // useful for network debugging, turns off 'LAN' checks, all IPs are classified 'internet'
    const val ID_NOLANADDRESS = false
    const val ID_OPENAL = true

    // let .dds be loaded from FS without altering pure state. only for developement.
    const val ID_PURE_ALLOWDDS = false
    val MACOS_X = System.getProperty("os.name") == "MacOSX"
    const val _DEBUG = true


    val _WIN32 = System.getProperty("os.name").startsWith("Windows")
    val _MACOSX = System.getProperty("os.name").contains("Mac")
    val WIN32 = _WIN32
    val __linux__ = System.getProperty("os.name") == "Linux"

    init {
        if (_WIN32 || MACOS_X) {
            ID_CONSOLE_LOCK = !_DEBUG
        } else {
            ID_CONSOLE_LOCK = false
        }
    }

    init {
        if (__linux__) {
            ID_BT_STUB = _DEBUG
        } else {
            ID_BT_STUB = true
        }
    }

    init {
        ID_ENFORCE_KEY = !ID_DEDICATED && !ID_DEMO_BUILD
    }
}
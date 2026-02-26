package neo.framework

object Licensee {
    const val ASYNC_PROTOCOL_MAJOR = 1
    const val BASE_GAMEDIR: String = "base"
    const val CDKEY_FILE: String = "doomkey"
    val CDKEY_TEXT: String = """
         
         // Do not give this file to ANYONE.
         // id Software or Zenimax will NEVER ask you to send this file to them.
         
         """.trimIndent()
    const val CD_BASEDIR: String = "Doom"
    const val CD_EXE: String = "doom.exe"
    const val CONFIG_FILE: String = "DoomConfig.cfg"

    //
    const val CONFIG_SPEC: String = "config.spec"
    const val EDITOR_DEFAULT_PROJECT: String = "doom.qe4"
    const val EDITOR_REGISTRY_KEY: String = "DOOMRadiant"
    const val EDITOR_WINDOWTEXT: String = "DOOMEdit"
    const val ENGINE_VERSION: String = "dhewm3 1.5.3-kotlin" // printed in console

    /*
     ===============================================================================

     Definitions for information that is related to a licensee's game name and location.

     ===============================================================================
     */
    const val GAME_NAME: String = "dhewm 3" // appears on window titles and errors
    const val IDNET_HOST: String = "idnet.ua-corp.com"
    const val IDNET_MASTER_PORT = 27650
    const val LINUX_DEFAULT_PATH: String = "/usr/local/games/doom3"
    const val NUM_SERVER_PORTS = 4
    const val PORT_SERVER = 27666
    const val RENDERDEMO_VERSION = 2
    const val SAVEGAME_VERSION = 18
    const val SOURCE_CODE_BASE_FOLDER: String = "neo"
    const val WIN32_CONSOLE_CLASS: String = "DOOM 3 WinConsole"
    const val WIN32_FAKE_WINDOW_CLASS_NAME: String = "DOOM3_WGL_FAKE"
    const val WIN32_WINDOW_CLASS_NAME: String = "DOOM3"
    const val XPKEY_FILE: String = "xpkey"
}
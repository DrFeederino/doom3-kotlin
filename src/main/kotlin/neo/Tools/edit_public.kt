package neo.Tools

import neo.Game.Script.Script_Interpreter.idInterpreter
import neo.Game.Script.idProgram
import neo.framework.Common
import neo.idlib.Dict_h.idDict
import neo.idlib.math.idAngles
import neo.idlib.math.idVec3

object edit_public {
    /*
     ===============================================================================

     Editors.

     ===============================================================================
     */
    // class	idProgram;
    // class	idInterpreter;
    // Radiant Level Editor
    fun RadiantInit() {
        Common.common.Printf("The level editor Radiant only runs on Win32\n")
    }

    fun RadiantShutdown() {}
    fun RadiantRun() {}
    fun RadiantPrint(text: String) {}
    fun RadiantSync(mapName: String, viewOrg: idVec3, viewAngles: idAngles) {}

    // in-game Light Editor
    fun LightEditorInit(spawnArgs: idDict) {
        Common.common.Printf("The Light Editor only runs on Win32\n")
    }

    fun LightEditorShutdown() {}
    fun LightEditorRun() {}

    // in-game Sound Editor
    fun SoundEditorInit(spawnArgs: idDict) {
        Common.common.Printf("The Sound Editor only runs on Win32\n")
    }

    fun SoundEditorShutdown() {}
    fun SoundEditorRun() {}

    // in-game Articulated Figure Editor
    fun AFEditorInit(spawnArgs: idDict) {
        Common.common.Printf("The Articulated Figure Editor only runs on Win32\n")
    }

    fun AFEditorShutdown() {}
    fun AFEditorRun() {}

    // in-game Particle Editor
    fun ParticleEditorInit(spawnArgs: idDict) {
        Common.common.Printf("The Particle Editor only runs on Win32\n")
    }

    fun ParticleEditorShutdown() {}
    fun ParticleEditorRun() {}

    // in-game PDA Editor
    fun PDAEditorInit(spawnArgs: idDict) {
        Common.common.Printf("The PDA editor only runs on Win32\n")
    }

    fun PDAEditorShutdown() {}
    fun PDAEditorRun() {}

    // in-game Script Editor
    fun ScriptEditorInit(spawnArgs: idDict) {
        Common.common.Printf("The Script Editor only runs on Win32\n")
    }

    fun ScriptEditorShutdown() {}
    fun ScriptEditorRun() {}

    // in-game Declaration Browser
    fun DeclBrowserInit(spawnArgs: idDict) {
        Common.common.Printf("The Declaration Browser only runs on Win32\n")
    }

    fun DeclBrowserShutdown() {}
    fun DeclBrowserRun() {}
    fun DeclBrowserReloadDeclarations() {}

    // GUI Editor
    fun GUIEditorInit() {
        Common.common.Printf("The GUI Editor only runs on Win32\n")
    }

    fun GUIEditorShutdown() {}
    fun GUIEditorRun() {}
    fun GUIEditorHandleMessage(msg: Any): Boolean {
        return false
    }

    // Script Debugger
    fun DebuggerClientLaunch() {}
    fun DebuggerClientInit(cmdline: String) {
        Common.common.Printf("The Script Debugger Client only runs on Win32\n")
    }

    fun DebuggerServerInit(): Boolean {
        return false
    }

    fun DebuggerServerShutdown() {}
    fun DebuggerServerPrint(text: String) {}
    fun DebuggerServerCheckBreakpoint(interpreter: idInterpreter, program: idProgram, instructionPointer: Int) {}

    //Material Editor
    fun MaterialEditorInit() {
        Common.common.Printf("The Material editor only runs on Win32\n")
    }

    fun MaterialEditorRun() {}
    fun MaterialEditorShutdown() {}
    fun MaterialEditorPrintConsole(msg: String) {}
}
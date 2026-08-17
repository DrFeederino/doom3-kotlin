/*
 * Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
 * Translated to Kotlin by Dr. Feederino with support of Claude Code
 *
 * This file is part of the Doom 3 Kotlin project.
 * Original source: neo/game/anim/Anim_Import.cpp
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
 *
 * You should have received a copy of the GNU General Public License
 * along with Doom 3 Source Code. If not, see <http://www.gnu.org/licenses/>.
 */

package neo.Game.Animation

import neo.Game.Animation.Anim.idAnimManager
import neo.Game.GameSys.SysCvar
import neo.Game.Game_local
import neo.Renderer.Model
import neo.framework.CVarSystem
import neo.framework.Common
import neo.framework.FileSystem_h
import neo.framework.FileSystem_h.idFileList
import neo.framework.Licensee
import neo.idlib.Text.Lexer
import neo.idlib.Text.Lexer.idLexer
import neo.idlib.Text.Parser.idParser
import neo.idlib.Text.Str
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Token.idToken
import neo.idlib.idException

/***********************************************************************

Maya conversion functions

 ***********************************************************************/

object Anim_Import {

    val Maya_Error: idStr = idStr()

    // NOTE: Differs from C++ — Maya DLL function pointers (Maya_ConvertModel, Maya_Shutdown)
    // are not applicable on JVM. The Maya export pipeline is a Windows-specific dev tool
    // that loads a native MayaImport DLL. On JVM we track the state but conversion always
    // fails gracefully since Maya DLLs cannot be loaded.
    private var hasMayaConvert = false
    private var hasMayaShutdown = false
    var importDLL = 0

    /*
     ==============================================================================================

     idModelExport

     ==============================================================================================
     */
    class idModelExport {

        val commandLine: idStr = idStr()
        val dest: idStr = idStr()
        var force = false
        val src: idStr = idStr()

        /*
         ====================
         idModelExport::Reset
         ====================
         */
        private fun Reset() {
            force = false
            commandLine.set("")
            src.set("")
            dest.set("")
        }

        /*
         ====================
         idModelExport::ParseOptions
         ====================
         */
        @Throws(idException::class)
        private fun ParseOptions(lex: idLexer): Boolean {
            val token = idToken()
            var destdir = idStr()
            var sourcedir = idStr()

            if (!lex.ReadToken(token)) {
                lex.Error("Expected filename")
                return false
            }

            src.set(token)
            dest.set(token)

            while (lex.ReadToken(token)) {
                if (token.toString() == "-") {
                    if (!lex.ReadToken(token)) {
                        lex.Error("Expecting option")
                        return false
                    }
                    if (token.toString() == "sourcedir") {
                        if (!lex.ReadToken(token)) {
                            lex.Error("Missing pathname after -sourcedir")
                            return false
                        }
                        sourcedir.set(token)
                    } else if (token.toString() == "destdir") {
                        if (!lex.ReadToken(token)) {
                            lex.Error("Missing pathname after -destdir")
                            return false
                        }
                        destdir.set(token)
                    } else if (token.toString() == "dest") {
                        if (!lex.ReadToken(token)) {
                            lex.Error("Missing filename after -dest")
                            return false
                        }
                        dest.set(token)
                    } else {
                        commandLine.plusAssign(Str.va(" -%s", token.toString()))
                    }
                } else {
                    commandLine.plusAssign(Str.va(" %s", token.toString()))
                }
            }

            if (sourcedir.Length() != 0) {
                src.StripPath()
                sourcedir.BackSlashesToSlashes()
                src.set(String.format("%s/%s", sourcedir, src.toString()))
            }

            if (destdir.Length() != 0) {
                dest.StripPath()
                destdir.BackSlashesToSlashes()
                dest.set(String.format("%s/%s", destdir, dest.toString()))
            }

            return true
        }

        /*
         ====================
         idModelExport::ParseExportSection
         ====================
         */
        private fun ParseExportSection(parser: idParser): Int {
            val command = idToken()
            val token = idToken()
            val defaultCommands = idStr()
            val lex = idLexer()
            var temp = idStr()
            val parms = idStr()
            var count: Int

            // only export sections that match our export mask
            if (!SysCvar.g_exportMask.GetString().isNullOrEmpty()) {
                if (parser.CheckTokenString("{")) {
                    parser.SkipBracedSection(false)
                    return 0
                }

                parser.ReadToken(token)
                if (token.Icmp(SysCvar.g_exportMask.GetString()!!) != 0) {
                    parser.SkipBracedSection()
                    return 0
                }
                parser.ExpectTokenString("{")
            } else if (!parser.CheckTokenString("{")) { // skip the export mask
                parser.ReadToken(token)
                parser.ExpectTokenString("{")
            }

            count = 0

            lex.SetFlags(Lexer.LEXFL_NOSTRINGCONCAT or Lexer.LEXFL_ALLOWPATHNAMES or Lexer.LEXFL_ALLOWMULTICHARLITERALS or Lexer.LEXFL_ALLOWBACKSLASHSTRINGCONCAT)

            while (true) {

                if (!parser.ReadToken(command)) { // NOTE: Original C++ has typo "Unexpoected" — preserved for fidelity
                    parser.Error("Unexpoected end-of-file")
                    break
                }

                if (command.toString() == "}") {
                    break
                }

                if (command.toString() == "options") {
                    parser.ParseRestOfLine(defaultCommands)
                } else if (command.toString() == "addoptions") {
                    parser.ParseRestOfLine(temp)
                    defaultCommands.plusAssign(" ")
                    defaultCommands.plusAssign(temp)
                } else if (command.toString() == "mesh" || command.toString() == "anim" || command.toString() == "camera") {
                    if (!parser.ReadToken(token)) {
                        parser.Error("Expected filename")
                    }

                    temp.set(token)
                    parser.ParseRestOfLine(parms)

                    if (defaultCommands.Length() != 0) {
                        temp.set(String.format("%s %s", temp, defaultCommands))
                    }

                    if (parms.Length() != 0) {
                        temp.set(String.format("%s %s", temp, parms))
                    }

                    lex.LoadMemory(temp, temp.Length(), parser.GetFileName()!!)

                    Reset()
                    if (ParseOptions(lex)) {
                        var game = CVarSystem.cvarSystem.GetCVarString("fs_game")
                        if (game.isEmpty()) {
                            game = Licensee.BASE_GAMEDIR
                        }

                        if (command.toString() == "mesh") {
                            dest.SetFileExtension(Model.MD5_MESH_EXT)
                        } else if (command.toString() == "anim") {
                            dest.SetFileExtension(Model.MD5_ANIM_EXT)
                        } else if (command.toString() == "camera") {
                            dest.SetFileExtension(Model.MD5_CAMERA_EXT)
                        } else {
                            dest.SetFileExtension(command.toString())
                        } // FIX: C++ uses commandLine.c_str() in the sprintf while writing into commandLine,
                        // which is undefined behavior. We correctly save commandLine to 'back' first and
                        // use that saved copy in the format string.
                        val back = commandLine.toString()
                        commandLine.set(
                            String.format(
                                "%s %s -dest %s -game %s%s", command, src.toString(), dest.toString(), game, back
                            )
                        )
                        if (ConvertMayaToMD5()) {
                            count++
                        } else {
                            parser.Warning("Failed to export '%s' : %s", src, Maya_Error)
                        }
                    }
                    lex.FreeSource()
                } else {
                    parser.Error("Unknown token: %s", command)
                    parser.SkipBracedSection(false)
                    break
                }
            }

            return count
        }

        /*
         =====================
         idModelExport::ConvertMayaToMD5

         Checks if a Maya model should be converted to an MD5, and converts if if the time/date or
         version number has changed.
         =====================
         */
        private fun ConvertMayaToMD5(): Boolean {
            val sourceTime = LongArray(1)
            val destTime = LongArray(1)
            var version: Int
            val cmdLine = idToken()
            val path = idStr()

            // check if our DLL got loaded
            if (initialized && !hasMayaConvert) {
                Maya_Error.set("MayaImport dll not loaded.")
                return false
            }

            // if idAnimManager::forceExport is set then we always reexport Maya models
            if (idAnimManager.forceExport) {
                force = true
            }

            // get the source file's time
            if (FileSystem_h.fileSystem.ReadFile(src, null, sourceTime) < 0) { // source file doesn't exist
                return true
            }

            // get the destination file's time
            if (!force && (FileSystem_h.fileSystem.ReadFile(dest, null, destTime) >= 0)) {
                val parser = idParser(Lexer.LEXFL_ALLOWPATHNAMES or Lexer.LEXFL_NOSTRINGESCAPECHARS)

                parser.LoadFile(dest)

                // read the file version
                if (parser.CheckTokenString(Model.MD5_VERSION_STRING)) {
                    version = parser.ParseInt()

                    // check the command line
                    if (parser.CheckTokenString("commandline")) {
                        parser.ReadToken(cmdLine)

                        // check the file time, scale, and version
                        if ((destTime[0] >= sourceTime[0]) && (version == Model.MD5_VERSION) && (cmdLine.toString() == commandLine.toString())) { // don't convert it
                            return true
                        }
                    }
                }
            }

            // if this is the first time we've been run, check if Maya is installed and load our DLL
            if (!initialized) {
                initialized = true

                if (!CheckMayaInstall()) {
                    Maya_Error.set("Maya not installed in registry.")
                    return false
                }

                LoadMayaDll()

                // check if our DLL got loaded
                if (!hasMayaConvert) {
                    Maya_Error.set("Could not load MayaImport dll.")
                    return false
                }
            }

            // we need to make sure we have a full path, so convert the filename to an OS path
            src.set(FileSystem_h.fileSystem.RelativePathToOSPath(src.toString()))
            dest.set(FileSystem_h.fileSystem.RelativePathToOSPath(dest.toString()))

            dest.ExtractFilePath(path)
            if (path.Length() != 0) {
                FileSystem_h.fileSystem.CreateOSPath(path.toString())
            }

            // get the os path in case it needs to create one
            path.set(FileSystem_h.fileSystem.RelativePathToOSPath(""))

            Common.common.SetRefreshOnPrint(true) // NOTE: Differs from C++ — Maya DLL function pointer call (Maya_ConvertModel) cannot
            // be performed on JVM. The Maya export pipeline requires a native Windows DLL.
            // On JVM, this path is only reached if CheckMayaInstall() returned true, which
            // currently always returns false. If a future JVM-native Maya bridge is implemented,
            // the conversion call would go here.
            Maya_Error.set("Maya conversion not available on JVM platform.")
            Common.common.SetRefreshOnPrint(false)
            return Maya_Error.toString() == "Ok"

            // conversion succeeded
        }

        /*
         ================
         idModelExport::ExportDefFile
         ================
         */
        fun ExportDefFile(filename: String): Int {
            val parser =
                idParser(Lexer.LEXFL_NOSTRINGCONCAT or Lexer.LEXFL_ALLOWPATHNAMES or Lexer.LEXFL_ALLOWMULTICHARLITERALS or Lexer.LEXFL_ALLOWBACKSLASHSTRINGCONCAT)
            val token = idToken()
            var count: Int

            count = 0

            if (!parser.LoadFile(filename)) {
                Game_local.gameLocal.Printf("Could not load '%s'\n", filename)
                return 0
            }

            while (parser.ReadToken(token)) {
                if (token.toString() == "export") {
                    count += ParseExportSection(parser)
                } else {
                    parser.ReadToken(token)
                    parser.SkipBracedSection()
                }
            }

            return count
        }

        /*
         ====================
         idModelExport::ExportModel
         ====================
         */
        fun ExportModel(model: String): Boolean {
            var game = CVarSystem.cvarSystem.GetCVarString("fs_game")
            if (game.isEmpty()) {
                game = Licensee.BASE_GAMEDIR
            }

            Reset()
            src.set(model)
            dest.set(model)
            dest.SetFileExtension(Model.MD5_MESH_EXT)

            commandLine.set(String.format("mesh %s -dest %s -game %s", src.toString(), dest.toString(), game))
            if (!ConvertMayaToMD5()) {
                Game_local.gameLocal.Printf("Failed to export '%s' : %s", src, Maya_Error)
                return false
            }

            return true
        }

        /*
         ====================
         idModelExport::ExportAnim
         ====================
         */
        fun ExportAnim(anim: String): Boolean {
            var game = CVarSystem.cvarSystem.GetCVarString("fs_game")
            if (game.isEmpty()) {
                game = Licensee.BASE_GAMEDIR
            }

            Reset()
            src.set(anim)
            dest.set(anim)
            dest.SetFileExtension(Model.MD5_ANIM_EXT)

            commandLine.set(String.format("anim %s -dest %s -game %s", src, dest, game))
            if (!ConvertMayaToMD5()) {
                Game_local.gameLocal.Printf("Failed to export '%s' : %s", src, Maya_Error)
                return false
            }

            return true
        }

        /*
         ================
         idModelExport::ExportModels
         ================
         */
        fun ExportModels(pathname: String, extension: String): Int {
            var count: Int
            val files: idFileList
            var i: Int

            count = 0

            if (!CheckMayaInstall()) { // if Maya isn't installed, don't bother checking if we have anims to export
                return 0
            }

            Game_local.gameLocal.Printf("----- Exporting models -----\n") // NOTE: Original C++ has `if ( !g_exportMask.GetString()[ 0 ] )` which prints
            // the mask value when it is EMPTY — this is likely a C++ bug (should print when
            // mask IS set). Preserving original C++ behavior here.
            if (SysCvar.g_exportMask.GetString().isNullOrEmpty()) {
                Game_local.gameLocal.Printf("  Export mask: '%s'\n", SysCvar.g_exportMask.GetString())
            }

            count = 0

            files = FileSystem_h.fileSystem.ListFiles(pathname, extension)
            i = 0
            while (i < files.GetNumFiles()) {
                count += ExportDefFile(Str.va("%s/%s", pathname, files.GetFile(i)))
                i++
            }
            FileSystem_h.fileSystem.FreeFileList(files)

            Game_local.gameLocal.Printf("...%d models exported.\n", count)

            return count
        }

        companion object {
            // FIX: Was `const val` which is a compile-time constant that can never change.
            // C++ has `bool idModelExport::initialized = false` as a mutable static.
            private var initialized = false

            /*
             =====================
             idModelExport::CheckMayaInstall

             Determines if Maya is installed on the user's machine
             =====================
             */
            private fun CheckMayaInstall(): Boolean { // NOTE: Differs from C++ — Original checks Windows registry for Maya installation.
                // On JVM/Kotlin there is no Maya DLL loading support, so this always returns false.
                return false
            }

            /*
             =====================
             idModelExport::LoadMayaDll

             Checks to see if we can load the Maya export dll
             =====================
             */
            private fun LoadMayaDll() { // NOTE: Differs from C++ — Original loads MayaImport DLL and resolves function
                // pointers (dllEntry, Maya_ConvertModel, Maya_Shutdown). This is not applicable
                // on JVM. The function is a no-op; hasMayaConvert/hasMayaShutdown remain false.
            }

            /*
             ====================
             idModelExport::Shutdown
             ====================
             */
            fun Shutdown() { // NOTE: Differs from C++ — Original calls Maya_Shutdown() and unloads the DLL.
                // On JVM we just reset the state variables.
                importDLL = 0
                hasMayaShutdown = false
                hasMayaConvert = false
                Maya_Error.Clear()
                initialized = false
            }
        }

        /*
         ====================
         idModelExport::idModelExport
         ====================
         */
        init {
            Reset()
        }
    }
}

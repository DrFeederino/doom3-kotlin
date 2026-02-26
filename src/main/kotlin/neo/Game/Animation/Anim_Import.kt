package neo.Game.Animation

import neo.Game.GameSys.SysCvar
import neo.Game.Game_local
import neo.Renderer.Model
import neo.TempDump.TODO_Exception
import neo.framework.CVarSystem
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

object Anim_Import {
    /**
     * *********************************************************************
     *
     *
     * Maya conversion functions
     *
     *
     * *********************************************************************
     */
    val Maya_Error: idStr = idStr()

    //
    //    public static exporterInterface_t Maya_ConvertModel = null;
    //    public static exporterShutdown_t Maya_Shutdown = null;
    var importDLL = 0

    //
    //bool idModelExport::initialized = false;
    /*
     ==============================================================================================

     idModelExport

     ==============================================================================================
     */
    class idModelExport {
        //
        val commandLine: idStr = idStr()
        val dest: idStr = idStr()
        var force = false
        val src: idStr = idStr()
        private fun Reset() {
            force = false
            commandLine.set("")
            src.set("")
            dest.set("")
        }

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
            } else if (!parser.CheckTokenString("{")) {
                // skip the export mask
                parser.ReadToken(token)
                parser.ExpectTokenString("{")
            }
            count = 0
            lex.SetFlags(Lexer.LEXFL_NOSTRINGCONCAT or Lexer.LEXFL_ALLOWPATHNAMES or Lexer.LEXFL_ALLOWMULTICHARLITERALS or Lexer.LEXFL_ALLOWBACKSLASHSTRINGCONCAT)
            while (true) {
                if (!parser.ReadToken(command)) {
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
                        if (game.length == 0) {
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
                        }
                        val back = commandLine.toString()
                        commandLine.set(
                            String.format(
                                "%s %s -dest %s -game %s%s",
                                command,
                                src.toString(),
                                dest.toString(),
                                game,
                                back
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
//            long[] sourceTime = {0};
//            long[] destTime = {0};
//            int version;
//            idToken cmdLine = new idToken();
//            idStr path = new StrPool.idPoolStr();
//
//            // check if our DLL got loaded
//            if (initialized && !Maya_ConvertModel) {
//                Maya_Error.oSet("MayaImport dll not loaded.");
//                return false;
//            }
//
//            // if idAnimManager::forceExport is set then we always reexport Maya models
//            if (idAnimManager.forceExport) {
//                force = true;
//            }
//
//            // get the source file's time
//            if (fileSystem.ReadFile(src, null, sourceTime) < 0) {
//                // source file doesn't exist
//                return true;
//            }
//
//            // get the destination file's time
//            if (!force && (fileSystem.ReadFile(dest, null, destTime) >= 0)) {
//                idParser parser = new idParser(LEXFL_ALLOWPATHNAMES | LEXFL_NOSTRINGESCAPECHARS);
//
//                parser.LoadFile(dest);
//
//                // read the file version
//                if (parser.CheckTokenString(MD5_VERSION_STRING)) {
//                    version = parser.ParseInt();
//
//                    // check the command line
//                    if (parser.CheckTokenString("commandline")) {
//                        parser.ReadToken(cmdLine);
//
//                        // check the file time, scale, and version
//                        if ((destTime[0] >= sourceTime[0]) && (version == MD5_VERSION) && (cmdLine == commandLine)) {
//                            // don't convert it
//                            return true;
//                        }
//                    }
//                }
//            }
//
//            // if this is the first time we've been run, check if Maya is installed and load our DLL
//            if (!initialized) {
//                initialized = true;
//
//                if (!CheckMayaInstall()) {
//                    Maya_Error.oSet("Maya not installed in registry.");
//                    return false;
//                }
//
//                LoadMayaDll();
//
//                // check if our DLL got loaded
//                if (!Maya_ConvertModel) {
//                    Maya_Error.oSet("Could not load MayaImport dll.");
//                    return false;
//                }
//            }
//
//            // we need to make sure we have a full path, so convert the filename to an OS path
//            src.oSet(fileSystem.RelativePathToOSPath(src));
//            dest.oSet(fileSystem.RelativePathToOSPath(dest));
//
//            dest.ExtractFilePath(path);
//            if (path.Length() != 0) {
//                fileSystem.CreateOSPath(path);
//            }
//
//            // get the os path in case it needs to create one
//            path.oSet(fileSystem.RelativePathToOSPath(""));
//
//            common.SetRefreshOnPrint(true);
//            Maya_Error = Maya_ConvertModel(path, commandLine);
//            common.SetRefreshOnPrint(false);
//            if (!Maya_Error.equals("Ok")) {
//                return false;
//            }
//
//            // conversion succeded
//            return true;
            throw TODO_Exception()
        }

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

        fun ExportModels(pathname: String, extension: String): Int {
            var count: Int

//	count = 0;
            val files: idFileList
            var i: Int
            if (!CheckMayaInstall()) {
                // if Maya isn't installed, don't bother checking if we have anims to export
                return 0
            }
            Game_local.gameLocal.Printf("--------- Exporting models --------\n")
            if (!SysCvar.g_exportMask.GetString().isNullOrEmpty()) {
                Game_local.gameLocal.Printf(" Export mask: '%s'\n", SysCvar.g_exportMask.GetString())
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
            Game_local.gameLocal.Printf("-----------------------------------\n")
            return count
        }

        companion object {
            private const val initialized = false

            /*
         =====================
         idModelExport::CheckMayaInstall

         Determines if Maya is installed on the user's machine
         =====================
         */
            private fun CheckMayaInstall(): Boolean { //TODO:is this necessary?
////if( _WIN32){
////	return false;
////}else if(false){
////	HKEY	hKey;
////	long	lres, lType;
////
////	lres = RegOpenKey( HKEY_LOCAL_MACHINE, "SOFTWARE\\Alias|Wavefront\\Maya\\4.5\\Setup\\InstallPath", &hKey );
////
////	if ( lres != ERROR_SUCCESS ) {
////		return false;
////	}
////
////	lres = RegQueryValueEx( hKey, "MAYA_INSTALL_LOCATION", NULL, (unsigned long*)&lType, (unsigned char*)NULL, (unsigned long*)NULL );
////
////	RegCloseKey( hKey );
////
////	if ( lres != ERROR_SUCCESS ) {
////		return false;
////	}
////	return true;
////}else{
//            HKEY hKey;
//            long lres;
//
//            // only check the non-version specific key so that we only have to update the maya dll when new versions are released
//            lres = RegOpenKey(HKEY_LOCAL_MACHINE, "SOFTWARE\\Alias|Wavefront\\Maya", hKey);
//            RegCloseKey(hKey);
//
//            return lres == ERROR_SUCCESS;
                throw TODO_Exception()
            }

            /*
         =====================
         idModelExport::LoadMayaDll

         Checks to see if we can load the Maya export dll
         =====================
         */
            private fun LoadMayaDll() {
//            exporterDLLEntry_t dllEntry;
//            char[] dllPath = new char[MAX_OSPATH];
//
//            fileSystem.FindDLL("MayaImport", dllPath, false);
//            if (0 == dllPath[ 0]) {
//                return;
//            }
//            importDLL = sys.DLL_Load(dllPath);
//            if (0 == importDLL) {
//                return;
//            }
//
//            // look up the dll interface functions
//            dllEntry = (exporterDLLEntry_t) sys.DLL_GetProcAddress(importDLL, "dllEntry");
//            Maya_ConvertModel = (exporterInterface_t) sys.DLL_GetProcAddress(importDLL, "Maya_ConvertModel");
//            Maya_Shutdown = (exporterShutdown_t) sys.DLL_GetProcAddress(importDLL, "Maya_Shutdown");
//            if (!Maya_ConvertModel || !dllEntry || !Maya_Shutdown) {
//                Maya_ConvertModel = null;
//                Maya_Shutdown = null;
//                sys.DLL_Unload(importDLL);
//                importDLL = 0;
//                gameLocal.Error("Invalid interface on export DLL.");
//                return;
//            }
//
//            // initialize the DLL
//            if (!dllEntry(MD5_VERSION, common, sys)) {
//                // init failed
//                Maya_ConvertModel = null;
//                Maya_Shutdown = null;
//                sys.DLL_Unload(importDLL);
//                importDLL = 0;
//                gameLocal.Error("Export DLL init failed.");
//                return;
//            }
                throw TODO_Exception()
            }

            fun Shutdown() {
//            if (Maya_Shutdown) {
//                Maya_Shutdown.run();
//            }
//
//            if (importDLL != 0) {
//                sys.DLL_Unload(importDLL);
//            }
//
//            importDLL = 0;
//            Maya_Shutdown = null;
//            Maya_ConvertModel = null;
//            Maya_Error.Clear();
//            initialized = false;
                throw TODO_Exception()
            }
        }

        //
        //
        init {
            Reset()
        }
    }
}
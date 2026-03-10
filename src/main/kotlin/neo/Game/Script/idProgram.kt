/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package neo.Game.Script

import neo.Game.GameSys.SaveGame.idRestoreGame
import neo.Game.GameSys.SaveGame.idSaveGame
import neo.Game.GameSys.SysCvar
import neo.Game.Game_local
import neo.Game.Game_local.idGameLocal.Companion.Error
import neo.Game.Script.Script_Program.function_t
import neo.Game.Script.Script_Program.idCompileError
import neo.Game.Script.Script_Program.idTypeDef
import neo.Game.Script.Script_Program.idVarDef
import neo.Game.Script.Script_Program.idVarDef.initialized_t
import neo.Game.Script.Script_Program.idVarDefName
import neo.Game.Script.Script_Program.statement_s
import neo.Game.Script.Script_Thread.idThread
import neo.Game.idEntity
import neo.framework.FileSystem_h.fileSystem
import neo.framework.FileSystem_h.fsMode_t
import neo.framework.File_h.idFile
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Str.idStr.Companion.Cmp
import neo.idlib.containers.CInt
import neo.idlib.containers.List.idList
import neo.idlib.containers.StaticList.idStaticList
import neo.idlib.containers.idHashIndex
import neo.idlib.containers.idStrList
import neo.idlib.hashing.MD4_BlockChecksum
import neo.idlib.math.idVec3
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

/* **********************************************************************

 idProgram

 Handles compiling and storage of script data.  Multiple idProgram objects
 would represent seperate programs with no knowledge of each other.  Scripts
 meant to access shared data and functions should all be compiled by a
 single idProgram.

 ***********************************************************************/
class idProgram {
    //
    var returnDef: idVarDef? = null
    var returnStringDef: idVarDef? = null
    private val fileList = idStrList()
    private val filename = idStr()
    private var filenum = 0
    private val functions: idStaticList<function_t> = idStaticList(Script_Program.MAX_FUNCS, function_t::class.java)

    //
    private var numVariables = 0
    private val statements = idStaticList(Script_Program.MAX_STATEMENTS, statement_s::class.java)

    //
    private var sysDef: idVarDef? = null
    private var top_defs = 0
    private var top_files = 0

    //
    private var top_functions = 0
    private var top_statements = 0
    private var top_types = 0
    private val types = idList<idTypeDef>()
    private val varDefNameHash = idHashIndex()
    private val varDefNames = idList<idVarDefName>()
    private val varDefs = idList<idVarDef>()
    private val variableDefaults = idStaticList<Byte>(Script_Program.MAX_GLOBALS)
    private var variables = ByteArray(Script_Program.MAX_GLOBALS)

    //
    //
    init {
        FreeData()
    }

    /*
     ==============
     idProgram::CompileStats

     called after all files are compiled to report memory usage.
     ==============
     */
    private fun CompileStats() {
        var memused: Int
        val memallocated: Int
        val numdefs: Int
        var stringspace: Int
        var funcMem: Int
        var i: Int
        Game_local.gameLocal.Printf("---------- Compile stats ----------\n")
        Game_local.gameLocal.DPrintf("Files loaded:\n")
        stringspace = 0
        i = 0
        while (i < fileList.size()) {
            Game_local.gameLocal.DPrintf("   %s\n", fileList[i])
            stringspace += fileList[i].Allocated()
            i++
        }
        stringspace += fileList.sizeStrings()
        numdefs = varDefs.Num()
        memused = varDefs.Num() * idVarDef.BYTES
        memused += types.Num() * idTypeDef.BYTES
        memused += stringspace
        i = 0
        while (i < types.Num()) {
            memused += types[i].Allocated()
            i++
        }
        funcMem = functions.MemoryUsed()
        i = 0
        while (i < functions.Num()) {
            funcMem += functions[i].Allocated()
            i++
        }
        memallocated = funcMem + memused + BYTES
        memused += statements.MemoryUsed()
        memused += functions.MemoryUsed() // name and filename of functions are shared, so no need to include them
        memused += variables.size
        Game_local.gameLocal.Printf("\nMemory usage:\n")
        Game_local.gameLocal.Printf("     Strings: %d, %d bytes\n", fileList.size(), stringspace)
        Game_local.gameLocal.Printf("  Statements: %d, %d bytes\n", statements.Num(), statements.MemoryUsed())
        Game_local.gameLocal.Printf("   Functions: %d, %d bytes\n", functions.Num(), funcMem)
        Game_local.gameLocal.Printf("   Variables: %d bytes\n", numVariables)
        Game_local.gameLocal.Printf("    Mem used: %d bytes\n", memused)
        Game_local.gameLocal.Printf(" Static data: %d bytes\n", BYTES)
        Game_local.gameLocal.Printf("   Allocated: %d bytes\n", memallocated)
        Game_local.gameLocal.Printf(" Thread size: %d bytes\n\n", idThread.BYTES)
    }

    // ~idProgram();
    // save games
    fun Save(savefile: idSaveGame) {
        var i: Int
        var currentFileNum = top_files

        savefile.WriteInt(fileList.size() - currentFileNum)
        while (currentFileNum < fileList.size()) {
            savefile.WriteString(fileList[currentFileNum])
            currentFileNum++
        }
        i = 0
        while (i < variableDefaults.Num()) {
            if (variables[i] != variableDefaults[i]) {
                savefile.WriteInt(i)
                savefile.WriteByte(variables[i])
            }
            i++
        }
        // Mark the end of the diff with default variables with -1
        savefile.WriteInt(-1)
        savefile.WriteInt(numVariables)
        i = variableDefaults.Num()
        while (i < numVariables) {
            savefile.WriteByte(variables[i])
            i++
        }
        val checksum = CalculateChecksum(false).toInt()
        savefile.WriteInt(checksum)
    }

    fun Restore(savefile: idRestoreGame): Boolean {
        var i: Int
        val num = CInt()
        val index = CInt()
        var result = true
        val scriptname = idStr()

        savefile.ReadInt(num)
        for (i in 0 until num.integerValue) {
            savefile.ReadString(scriptname)
            CompileFile(scriptname.toString())
        }

        savefile.ReadInt(index)
        while (index.integerValue >= 0) {
            variables[index.integerValue] = savefile.ReadByte()
            savefile.ReadInt(index)
        }

        savefile.ReadInt(num)
        for (i in variableDefaults.Num() until num.integerValue) {
            variables[i] = savefile.ReadByte()
        }

        val saved_checksum = CInt()
        val checksum: Long

        savefile.ReadInt(saved_checksum)
        checksum = CalculateChecksum(false)

        if (saved_checksum.integerValue.toLong() != checksum) {
            Game_local.gameLocal.Warning("WARNING: Real Script checksum didn't match the one from the savegame!")
            result = false
        }

        return result
    }

    // Used to insure program code has not changed between savegames
    fun CalculateChecksum(forOldSavegame: Boolean): Long {
        // C++ statementBlock_t layout (natural alignment, sizeof = 20):
        //   unsigned short op;         // offset 0,  size 2
        //   (2 bytes padding)          // offset 2,  size 2 (align int a to 4)
        //   int a;                     // offset 4,  size 4
        //   int b;                     // offset 8,  size 4
        //   int c;                     // offset 12, size 4
        //   unsigned short linenumber; // offset 16, size 2
        //   unsigned short file;       // offset 18, size 2
        val SIZEOF_STATEMENT_BLOCK = 20
        val numStatements = statements.Num()
        val totalBytes = SIZEOF_STATEMENT_BLOCK * numStatements
        val buffer = ByteBuffer.allocate(totalBytes).order(ByteOrder.LITTLE_ENDIAN)

        // memset equivalent — ByteBuffer.allocate() already zero-fills

        // Copy info into new list, using the variable numbers instead of a pointer to the variable
        for (i in 0 until numStatements) {
            val st = statements[i]
            buffer.putShort((st.op and 0xFFFF).toShort())  // op (unsigned short)
            buffer.putShort(0)                              // padding (2 bytes)
            buffer.putInt(if (st.a != null) st.a!!.num else -1)  // a
            buffer.putInt(if (st.b != null) st.b!!.num else -1)  // b
            buffer.putInt(if (st.c != null) st.c!!.num else -1)  // c
            buffer.putShort((st.linenumber and 0xFFFF).toShort()) // linenumber (unsigned short)
            buffer.putShort((st.file and 0xFFFF).toShort())       // file (unsigned short)
        }

        buffer.flip()
        return MD4_BlockChecksum(buffer, totalBytes)
    }

    //    changed between savegames
    fun Startup(defaultScript: String?) {
        Game_local.gameLocal.Printf("Initializing scripts\n")
        // make sure all data is freed up

        idThread.Restart()

        // get ready for loading scripts
        BeginCompilation()

        // load the default script
        if (!defaultScript.isNullOrEmpty()) {
            CompileFile(defaultScript)
        }
        FinishCompilation()
    }

    /*
     ==============
     idProgram::Restart

     Restores all variables to their initial value
     ==============
     */
    fun Restart() {
        var i: Int
        idThread.Restart()

        // since there may have been a script loaded by the map or the user may
        // have typed "script" from the console, free up any types and vardefs that
        // have been allocated after the initial startup
        //
//        for (i in top_types until types.Num()) {
//            types[i].deconstructor()
//        }
//        types.SetNum(top_types, false)
//
//        for (i in top_defs until varDefs.Num()) {
//            varDefs[i].deconstructor()
//        }
        varDefs.SetNum(top_defs, false)
        i = top_functions
        while (i < functions.Num()) {
            functions[i].Clear()
            i++
        }
        functions.SetNum(top_functions)
        statements.SetNum(top_statements)
        fileList.setSize(top_files, false)
        filename.Clear()

        // reset the variables to their default values
        numVariables = variableDefaults.Num()
        i = 0
        while (i < numVariables) {
            variables[i] = variableDefaults[i]
            i++
        }
    }

    fun CompileText(source: String, text: String, console: Boolean): Boolean {
        val compiler = idCompiler()
        var i: Int
        var def: idVarDef
        val ospath: String

        // use a full os path for GetFilenum since it calls OSPathToRelativePath to convert filenames from the parser
        ospath = fileSystem.RelativePathToOSPath(source)
        filenum = GetFilenum(ospath)
        try {
            compiler.CompileFile(text, filename.toString(), console)

            // check to make sure all functions prototyped have code
            i = 0
            while (i < varDefs.Num()) {
                def = varDefs[i]
                if (def.Type() == Script_Program.ev_function && (def.scope!!.Type() == Script_Program.ev_namespace || def.scope!!.TypeDef()!!
                        .Inherits(Script_Program.type_object))
                ) {
                    if (null == def.value!!.functionPtr!!.eventdef && 0 == def.value!!.functionPtr!!.firstStatement) {
                        throw idCompileError(
                            String.format(
                                "function %s was not defined\n",
                                def.GlobalName()
                            )
                        )
                    }
                }
                i++
            }
        } catch (err: idCompileError) {
            if (console) {
                Game_local.gameLocal.Printf("%s\n", err.error)
                return false
            } else {
                Error("%s\n", err.error)
            }
        }
        if (!console) {
            CompileStats()
        }
        return true
    }

    fun CompileFunction(functionName: String, text: String): function_t? {
        val result: Boolean
        result = CompileText(functionName, text, false)
        if (SysCvar.g_disasm.GetBool()) {
            Disassemble()
        }
        if (!result) {
            Error("Compile failed.")
        }
        return FindFunction(functionName)
    }

    fun CompileFile(filename: String) {
        val src = arrayOf<ByteBuffer?>(null)
        val result: Boolean
        if (fileSystem.ReadFile(filename, src, null) < 0) {
            Error("Couldn't load %s\n", filename)
        }
        result = CompileText(filename, String(src[0]!!.array()), false)
        fileSystem.FreeFile(src)
        if (SysCvar.g_disasm.GetBool()) {
            Disassemble()
        }
        if (!result) {
            Error("Compile failed in file %s.", filename)
        }
    }

    /*
     ==============
     idProgram::BeginCompilation

     called before compiling a batch of files, clears the pr struct
     ==============
     */
    fun BeginCompilation() {
        val statement: statement_s?
        FreeData()
        try {
            // make the first statement a return for a "NULL" function
            statement = AllocStatement()
            statement!!.linenumber = 0
            statement.file = 0
            statement.op = OP_RETURN
            statement.a = null
            statement.b = null
            statement.c = null

            // define NULL
            //AllocDef( &type_void, "<NULL>", &def_namespace, true );
            // define the return def
            returnDef = AllocDef(Script_Program.type_vector, "<RETURN>", Script_Program.def_namespace, false)

            // define the return def for strings
            returnStringDef = AllocDef(Script_Program.type_string, "<RETURN>", Script_Program.def_namespace, false)

            // define the sys object
            sysDef = AllocDef(Script_Program.type_void, "sys", Script_Program.def_namespace, true)
        } catch (err: idCompileError) {
            Error("%s", err.error)
        }
    }

    /*
     ==============
     idProgram::FinishCompilation

     Called after all files are compiled to check for errors
     ==============
     */
    fun FinishCompilation() {
        var i: Int
        top_functions = functions.Num()
        top_statements = statements.Num()
        top_types = types.Num()
        top_defs = varDefs.Num()
        top_files = fileList.size()
        variableDefaults.Clear()
        variableDefaults.SetNum(numVariables)
        i = 0
        while (i < numVariables) {
            variableDefaults[i] = variables[i]
            i++
        }
    }

    fun DisassembleStatement(file: idFile, instructionPointer: Int) {
        val op: opcode_s
        val statement: statement_s
        statement = statements[instructionPointer]
        op = idCompiler.opcodes[statement.op]!!
        file.Printf(
            "%20s(%d):\t%6d: %15s\t",
            fileList[statement.file],
            statement.linenumber,
            instructionPointer,
            op.opname
        )
        if (statement.a != null) {
            file.Printf("\ta: ")
            statement.a!!.PrintInfo(file, instructionPointer)
        }
        if (statement.b != null) {
            file.Printf("\tb: ")
            statement.b!!.PrintInfo(file, instructionPointer)
        }
        if (statement.c != null) {
            file.Printf("\tc: ")
            statement.c!!.PrintInfo(file, instructionPointer)
        }
        file.Printf("\n")
    }

    fun Disassemble() {
        var i: Int
        var instructionPointer: Int
        var func: function_t?
        val file: idFile
        file = fileSystem.OpenFileByMode("script/disasm.txt", fsMode_t.FS_WRITE)!!
        i = 0
        while (i < functions.Num()) {
            func = functions[i]
            if (func.eventdef != null) {
                // skip eventdefs
                i++
                continue
            }
            file.Printf(
                "\nfunction %s() %d stack used, %d parms, %d locals {\n",
                func.Name(),
                func.locals,
                func.parmTotal,
                func.locals - func.parmTotal
            )
            instructionPointer = 0
            while (instructionPointer < func.numStatements) {
                DisassembleStatement(file, func.firstStatement + instructionPointer)
                instructionPointer++
            }
            file.Printf("}\n")
            i++
        }
        fileSystem.CloseFile(file)
    }

    fun FreeData() {
        var i: Int

        // free the defs
        varDefs.DeleteContents(true)
        varDefNames.DeleteContents(true)
        varDefNameHash.Free()
        returnDef = null
        returnStringDef = null
        sysDef = null

        // free any special types we've created
        types.DeleteContents(true)
        filenum = 0
        numVariables = 0
        //	memset( variables, 0, sizeof( variables ) );
        variables = ByteArray(variables.size)

        // clear all the strings in the functions so that it doesn't look like we're leaking memory.
        i = 0
        while (i < functions.Num()) {
            functions[i].Clear()
            i++
        }
        filename.Clear()
        fileList.clear()
        statements.Clear()
        functions.Clear()
        top_functions = 0
        top_statements = 0
        top_types = 0
        top_defs = 0
        top_files = 0
        filename.set("")
    }

    fun GetFilename(num: Int): String {
        return fileList[num].toString()
    }

    fun GetFilenum(name: String): Int {
        if (filename.equals(name)) {
            return filenum
        }
        val strippedName: String
        strippedName = fileSystem.OSPathToRelativePath(name)
        filenum = if (strippedName == null || strippedName.isEmpty()) {
            // not off the base path so just use the full path
            fileList.addUnique(name)
        } else {
            fileList.addUnique(strippedName)
        }

        // save the unstripped name so that we don't have to strip the incoming name every time we call GetFilenum
        filename.set(name)
        return filenum
    }

    fun GetLineNumberForStatement(index: Int): Int {
        return statements[index].linenumber
    }

    fun GetFilenameForStatement(index: Int): String {
        return GetFilename(statements[index].file)
    }

    fun AllocType(type: idTypeDef): idTypeDef {
        val newtype: idTypeDef
        newtype = idTypeDef(type)
        types.Append(newtype)
        return newtype
    }

    fun AllocType(  /*etype_t*/etype: Int, edef: idVarDef?, ename: String?, esize: Int, aux: idTypeDef?): idTypeDef {
        val newtype: idTypeDef
        newtype = idTypeDef(etype, edef, ename, esize, aux)
        types.Append(newtype)
        return newtype
    }

    /*
     ============
     idProgram::GetType

     Returns a preexisting complex type that matches the parm, or allocates
     a new one and copies it out.
     ============
     */
    fun GetType(type: idTypeDef, allocate: Boolean): idTypeDef? {
        var i: Int

        //FIXME: linear search == slow
        i = types.Num() - 1
        while (i >= 0) {
            if (types[i].MatchesType(type) && types[i].Name() == type.Name()) {
                return types[i]
            }
            i--
        }
        return if (!allocate) {
            null
        } else AllocType(type)

        // allocate a new one
    }

    /*
     ============
     idProgram::FindType

     Returns a preexisting complex type that matches the name, or returns NULL if not found
     ============
     */
    fun FindType(name: String?): idTypeDef? {
        var check: idTypeDef
        var i: Int
        i = types.Num() - 1
        while (i >= 0) {
            check = types[i]
            if (check.Name() == name) {
                return check
            }
            i--
        }
        return null
    }

    fun AllocVarDef(type: idTypeDef?, name: String?, scope: idVarDef?): idVarDef {
        val def = idVarDef(type)
        def.scope = scope
        def.numUsers = 1
        def.num = varDefs.Append(def)
        def.value = Script_Program.varEval_s()

        // add the def to the list with defs with this name and set the name pointer
        AddDefToNameList(def, name)

        return def
    }

    fun AllocDef(type: idTypeDef?, name: String?, scope: idVarDef?, constant: Boolean): idVarDef {
        val def: idVarDef
        var element: String
        val def_x: idVarDef
        val def_y: idVarDef
        val def_z: idVarDef

        // allocate a new def
        def = idVarDef(type)
        def.scope = scope
        def.numUsers = 1
        def.num = varDefs.Append(def)
        def.value = Script_Program.varEval_s()

        // add the def to the list with defs with this name and set the name pointer
        AddDefToNameList(def, name)
        if (type!!.Type() == Script_Program.ev_vector || type.Type() == Script_Program.ev_field && type.FieldType()!!
                .Type() == Script_Program.ev_vector
        ) {
            //
            // vector
            //
            if (RESULT_STRING == name) {
                // <RESULT> vector defs don't need the _x, _y and _z components
                assert(scope!!.Type() == Script_Program.ev_function)
                def.value!!.stackOffset = scope.value!!.functionPtr!!.locals
                def.initialized = initialized_t.stackVariable
                scope.value!!.functionPtr!!.locals += type.Size()
            } else if (scope!!.TypeDef()!!.Inherits(Script_Program.type_object)) {
                val newtype = idTypeDef(Script_Program.ev_field, null, "float field", 0, Script_Program.type_float)
                val type2 = GetType(newtype, true)

                // set the value to the variable's position in the object
                def.value!!.ptrOffset = scope.TypeDef()!!.Size()

                // make automatic defs for the vectors elements
                // origin can be accessed as origin_x, origin_y, and origin_z
                element = String.format("%s_x", def.Name())
                def_x = AllocDef(type2, element, scope, constant)
                element = String.format("%s_y", def.Name())
                def_y = AllocDef(type2, element, scope, constant)
                def_y.value!!.ptrOffset = def_x.value!!.ptrOffset + Script_Program.type_float.Size()
                element = String.format("%s_z", def.Name())
                def_z = AllocDef(type2, element, scope, constant)
                def_z.value!!.ptrOffset = def_y.value!!.ptrOffset + Script_Program.type_float.Size()
            } else {
                val newtype = idTypeDef(Script_Program.ev_float, null, "vector float", 0, null)
                val ftype = GetType(newtype, true)

                // make automatic defs for the vectors elements
                // origin can be accessed as origin_x, origin_y, and origin_z
                element = String.format("%s_x", def.Name())
                def_x = AllocVarDef(ftype, element, scope)
                element = String.format("%s_y", def.Name())
                def_y = AllocVarDef(ftype, element, scope)
                element = String.format("%s_z", def.Name())
                def_z = AllocVarDef(ftype, element, scope)

                // get the memory for the full vector and point the _x, _y and _z
                // defs at the vector member offsets
                if (scope!!.Type() == Script_Program.ev_function) {
                    // vector on stack
                    def.value!!.stackOffset = scope.value!!.functionPtr!!.locals
                    def.initialized = initialized_t.stackVariable
                    scope.value!!.functionPtr!!.locals += type.Size()

                    def_x.value!!.stackOffset = def.value!!.stackOffset
                    def_y.value!!.stackOffset = def_x.value!!.stackOffset + java.lang.Float.BYTES
                    def_z.value!!.stackOffset = def_y.value!!.stackOffset + java.lang.Float.BYTES
                } else {
                    // global vector
                    val baseOffset = numVariables
                    def.value!!.setBytePtr(variables, baseOffset)
                    numVariables += type.Size()
                    if (numVariables > variables.size) {
                        throw idCompileError(String.format("Exceeded global memory size (%d bytes)", variables.size))
                    }
                    //variables.fill(0, baseOffset, numVariables)
                    def_x.value!!.setBytePtr(variables, baseOffset)
                    def_y.value!!.setBytePtr(variables, baseOffset + java.lang.Float.BYTES)
                    def_z.value!!.setBytePtr(variables, baseOffset + java.lang.Float.BYTES * 2)
                }

                def_x.initialized = def.initialized
                def_y.initialized = def.initialized
                def_z.initialized = def.initialized
            }
        } else if (scope!!.TypeDef()!!.Inherits(Script_Program.type_object)) {
            //
            // object variable
            //
            // set the value to the variable's position in the object
            def.value!!.ptrOffset = scope.TypeDef()!!.Size()
        } else if (scope.Type() == Script_Program.ev_function) {
            //
            // stack variable
            //
            // since we don't know how many local variables there are,
            // we have to have them go backwards on the stack
            def.value!!.stackOffset = scope.value!!.functionPtr!!.locals
            def.initialized = initialized_t.stackVariable
            if (type.Inherits(Script_Program.type_object)) {
                // objects only have their entity number on the stack, not the entire object
                scope.value!!.functionPtr!!.locals += Script_Program.type_object.Size()
            } else {
                scope.value!!.functionPtr!!.locals += type.Size()
            }
        } else {
            // global variable
            def.value!!.setBytePtr(variables, numVariables)
            numVariables += def.TypeDef()!!.Size()
            if (numVariables > variables.size) {
                throw idCompileError(String.format("Exceeded global memory size (%d bytes)", variables.size))
            }
            variables.fill(0, numVariables, variables.size)
        }
        return def
    }

    /*
     ============
     idProgram::GetDef

     If type is NULL, it will match any type
     ============
     */
    fun GetDef(type: idTypeDef?, name: String?, scope: idVarDef?): idVarDef? {
        var def: idVarDef?
        var bestDef: idVarDef?
        var bestDepth: Int
        var depth: Int
        bestDepth = 0
        bestDef = null
        def = GetDefList(name)
        while (def != null) {
            if (def.scope!!.Type() == Script_Program.ev_namespace) {
                depth = def.DepthOfScope(scope)
                if (0 == depth) {
                    // not in the same namespace
                    def = def.Next()
                    continue
                }
            } else if (def.scope !== scope) {
                // in a different function
                def = def.Next()
                continue
            } else {
                depth = 1
            }
            if (null == bestDef || depth < bestDepth) {
                bestDepth = depth
                bestDef = def
            }
            def = def.Next()
        }

        // see if the name is already in use for another type
        if (bestDef != null && type != null && bestDef.TypeDef() != type) {
            throw idCompileError(String.format("Type mismatch on redeclaration of %s", name))
        }
        return bestDef
    }

    fun FreeDef(def: idVarDef, scope: idVarDef?) {
        var e: idVarDef?
        var i: Int
        if (def.Type() == Script_Program.ev_vector) {
            var name: String
            name = String.format("%s_x", def.Name())
            e = GetDef(null, name, scope)
            e?.let { FreeDef(it, scope) }
            name = String.format("%s_y", def.Name())
            e = GetDef(null, name, scope)
            e?.let { FreeDef(it, scope) }
            name = String.format("%s_z", def.Name())
            e = GetDef(null, name, scope)
            e?.let { FreeDef(it, scope) }
        }
        varDefs.RemoveIndex(def.num)
        i = def.num
        while (i < varDefs.Num()) {
            varDefs[i].num = i
            i++
        }
        def.close()
    }

    fun FindFreeResultDef(type: idTypeDef?, name: String?, scope: idVarDef?, a: idVarDef?, b: idVarDef?): idVarDef {
        var def: idVarDef?
        def = GetDefList(name)
        while (def != null) {
            if (def == a || def == b) {
                def = def.Next()
                continue
            }
            if (def.TypeDef() != type) {
                def = def.Next()
                continue
            }
            if (def.scope != scope) {
                def = def.Next()
                continue
            }
            if (def.numUsers <= 1) {
                def = def.Next()
                continue
            }
            return def
            def = def.Next()
        }
        return AllocDef(type, name, scope, false)
    }

    fun GetDefList(name: String?): idVarDef? {
        var i: Int
        val hash: Int
        hash = varDefNameHash.GenerateKey(name!!, true)
        i = varDefNameHash.First(hash)
        while (i != -1) {
            if (Cmp(varDefNames[i].Name(), name) == 0) {
                return varDefNames[i].GetDefs()
            }
            i = varDefNameHash.Next(i)
        }
        return null
    }

    fun AddDefToNameList(def: idVarDef, name: String?) {
        var i: Int
        val hash: Int
        hash = varDefNameHash.GenerateKey(name!!, true)
        i = varDefNameHash.First(hash)
        while (i != -1) {
            if (Cmp(varDefNames[i].Name(), name) == 0) {
                break
            }
            i = varDefNameHash.Next(i)
        }
        if (i == -1) {
            i = varDefNames.Append(idVarDefName(name))
            varDefNameHash.Add(hash, i)
        }
        varDefNames[i].AddDef(def)
    }

    /*
     ================
     idProgram::FindFunction

     Searches for the specified function in the currently loaded script.  A full namespace should be
     specified if not in the global namespace.

     Returns 0 if function not found.
     Returns >0 if function found.
     ================
     */
    fun FindFunction(name: String?): function_t? {                // returns NULL if function not found
        var start: Int
        var pos: Int
        var namespaceDef: idVarDef?
        var def: idVarDef?
        assert(name != null)
        val fullname = idStr(name!!)
        start = 0
        namespaceDef = Script_Program.def_namespace
        do {
            pos = fullname.Find("::", true, start)
            if (pos < 0) {
                break
            }
            val namespaceName = fullname.Mid(start, pos - start).toString()
            def = GetDef(null, namespaceName, namespaceDef)
            if (null == def) {
                // couldn't find namespace
                return null
            }
            namespaceDef = def

            // skip past the ::
            start = pos + 2
        } while (def.Type() == Script_Program.ev_namespace)
        val funcName = fullname.Right(fullname.Length() - start).toString()
        def = GetDef(null, funcName, namespaceDef)
        if (null == def) {
            // couldn't find function
            return null
        }
        return if (def.Type() == Script_Program.ev_function && def.value!!.functionPtr!!.eventdef == null) {
            def.value!!.functionPtr
        } else null

        // is not a function, or is an eventdef
    }

    fun FindFunction(name: idStr): function_t? {
        return FindFunction(name.toString())
    }

    /*
     ================
     idProgram::FindFunction

     Searches for the specified object function in the currently loaded script.

     Returns 0 if function not found.
     Returns >0 if function found.
     ================
     */
    fun FindFunction(name: String?, type: idTypeDef): function_t? {    // returns NULL if function not found
        var tdef: idVarDef?
        var def: idVarDef?

        // look for the function
//            def = null;
        tdef = type.def
        while (tdef !== Script_Program.def_object) {
            def = GetDef(null, name, tdef)
            if (def != null) {
                return def.value!!.functionPtr
            }
            tdef = tdef!!.TypeDef()!!.SuperClass()!!.def
        }
        return null
    }

    fun AllocFunction(def: idVarDef?): function_t {
        if (functions.Num() >= functions.Max()) {
            throw idCompileError(String.format("Exceeded maximum allowed number of functions (%d)", functions.Max()))
        }

        // fill in the dfunction
        val func = functions.Alloc()
        func!!.eventdef = null
        func.def = def
        func.type = def!!.TypeDef()
        func.firstStatement = 0
        func.numStatements = 0
        func.parmTotal = 0
        func.locals = 0
        func.filenum = filenum
        func.parmSize.SetGranularity(1)
        func.SetName(def.GlobalName())
        def.SetFunction(func)
        return func
    }

    fun GetFunction(index: Int): function_t {
        return functions[index]
    }

    fun GetFunctionIndex(func: function_t): Int {
        return functions.IndexOf(func)
    }

    fun SetEntity(name: String?, ent: idEntity?) {
        val def: idVarDef?
        var defName: String? = "$"
        defName += name
        def = GetDef(Script_Program.type_entity, defName, Script_Program.def_namespace)
        if (def != null && def.initialized != initialized_t.stackVariable) {
            // 0 is reserved for NULL entity
            if (null == ent) {
                def.value!!.entityNumberPtr = 0
            } else {
                def.value!!.entityNumberPtr = ent.entityNumber + 1
            }
        }
    }

    fun AllocStatement(): statement_s? {
        if (statements.Num() == 61960) {
        }
        if (statements.Num() >= statements.Max()) {
            throw idCompileError(String.format("Exceeded maximum allowed number of statements (%d)", statements.Max()))
        }
        return statements.Alloc()
    }

    fun GetStatement(index: Int): statement_s {
        if (index == 61961) {
        }
        return statements[index]
    }

    fun NumStatements(): Int {
        return statements.Num()
    }

    fun GetReturnedInteger(): Int {
        return returnDef!!.value!!.intPtr
    }

    fun ReturnFloat(value: Float) {
        returnDef!!.value!!.floatPtr = value
    }

    fun ReturnInteger(value: Int) {
        returnDef!!.value!!.intPtr = value
    }

    fun ReturnVector(vec: idVec3) {
        returnDef!!.value!!.setVectorPtr(vec)
    }

    fun ReturnString(string: String?) {
        returnStringDef!!.value!!.stringPtr =
            string //idStr.Copynz(returnStringDef.value.stringPtr, string, MAX_STRING_LEN);
    }

    fun ReturnEntity(ent: idEntity?) {
        if (ent != null) {
            returnDef!!.value!!.entityNumberPtr = ent.entityNumber + 1
        } else {
            returnDef!!.value!!.entityNumberPtr = 0
        }
    }

    fun NumFilenames(): Int {
        return fileList.size()
    }

    companion object {
        const val BYTES = Integer.BYTES * 20 //TODO:
    }
}
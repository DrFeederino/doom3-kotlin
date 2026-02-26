package neo.framework

import neo.TempDump.void_callback
import neo.framework.DeclManager.declType_t
import neo.framework.FileSystem_h.idFileList
import neo.idlib.BIT
import neo.idlib.CmdArgs
import neo.idlib.Text.Str
import neo.idlib.Text.Str.idStr
import neo.idlib.containers.List.cmp_t
import neo.idlib.containers.List.idList
import neo.idlib.containers.idStrList
import neo.idlib.idException
import neo.idlib.idLib
import java.nio.ByteBuffer

object CmdSystem {
    /*
     ===============================================================================

     Console command execution and command text buffering.

     Any number of commands can be added in a frame from several different
     sources. Most commands come from either key bindings or console line input,
     but entire text files can be execed.

     Command execution takes a null terminated string, breaks it into tokens,
     then searches for a command or variable that matches the first token.

     ===============================================================================
     */
    // command flags
    //typedef enum {
    const val CMD_FL_ALL: Long = -1


    val CMD_FL_CHEAT: Long = BIT(0) // command is considered a cheat
        .toLong()


    val CMD_FL_GAME: Long = BIT(4) // game command
        .toLong()


    val CMD_FL_RENDERER: Long = BIT(2) // renderer command
        .toLong()


    val CMD_FL_SOUND: Long = BIT(3) // sound command
        .toLong()


    val CMD_FL_SYSTEM: Long = BIT(1) // system command
        .toLong()


    val CMD_FL_TOOL: Long = BIT(5) // tool command
        .toLong()
    private var cmdSystemLocal: idCmdSystemLocal = idCmdSystemLocal()


    var cmdSystem: idCmdSystem = cmdSystemLocal

    //} cmdFlags_t;
    fun setCmdSystems(cmdSystem: idCmdSystem) {
        cmdSystemLocal = cmdSystem as idCmdSystemLocal
        CmdSystem.cmdSystem = cmdSystemLocal
    }

    // parameters for command buffer stuffing
    enum class cmdExecution_t {
        CMD_EXEC_NOW,  // don't return until completed
        CMD_EXEC_INSERT,  // insert at current position, but don't run yet
        CMD_EXEC_APPEND // add to end of the command buffer (normal case)
    }

    // command function
    abstract class cmdFunction_t {
        @Throws(idException::class)
        abstract fun run(args: CmdArgs.idCmdArgs??)
    }

    // argument completion function
    abstract class argCompletion_t {
        //typedef void (*argCompletion_t)( final idCmdArgs args, void_callback<String> callback );
        @Throws(idException::class)
        abstract fun run(args: CmdArgs.idCmdArgs?, callback: void_callback<String>)
        fun run(args: CmdArgs.idCmdArgs?, callback: void_callback<String>, type: Int) {}
    }

    abstract class idCmdSystem {
        //
        //public	virtual				~idCmdSystem( void ) {}
        //
        @Throws(idException::class)
        abstract fun Init()
        abstract fun Shutdown()

        // Registers a command and the function to call for it.
        @Throws(idException::class)
        abstract fun AddCommand(
            cmdName: String,
            function: cmdFunction_t,
            flags: Long,
            description: String,
            argCompletion: argCompletion_t?
        )

        @Throws(idException::class)
        fun AddCommand(
            cmdName: String,
            function: cmdFunction_t,
            flags: Long,
            description: String /*, argCompletion_t argCompletion = NULL*/
        ) {
            AddCommand(cmdName, function, flags, description, null)
        }

        // Removes a command.
        abstract fun RemoveCommand(cmdName: String)

        // Remove all commands with one of the flags set.
        abstract fun RemoveFlaggedCommands(flags: Int)

        // Command and argument completion using callback for each valid string.
        @Throws(idException::class)
        abstract fun CommandCompletion(callback: void_callback<String>)

        @Throws(idException::class)
        abstract fun ArgCompletion(cmdString: String, callback: void_callback<String>)

        // Adds command text to the command buffer, does not add a final \n
        @Throws(idException::class)
        abstract fun BufferCommandText(exec: cmdExecution_t, text: String)

        // Pulls off \n \r or ; terminated lines of text from the command buffer and
        // executes the commands. Stops when the buffer is empty.
        // Normally called once per frame, but may be explicitly invoked.
        @Throws(idException::class)
        abstract fun ExecuteCommandBuffer()

        // Base for path/file auto-completion.
        @Throws(idException::class)
        abstract fun ArgCompletion_FolderExtension(
            args: CmdArgs.idCmdArgs?,
            callback: void_callback<String>,
            folder: String,
            stripFolder: Boolean,
            vararg objects: Any?
        )

        // Base for decl name auto-completion.
        @Throws(idException::class)
        abstract fun ArgCompletion_DeclName(args: CmdArgs.idCmdArgs?, callback: void_callback<String>, type: Int)

        // Adds to the command buffer in tokenized form ( CMD_EXEC_NOW or CMD_EXEC_APPEND only )
        @Throws(idException::class)
        abstract fun BufferCommandArgs(exec: cmdExecution_t, args: CmdArgs.idCmdArgs?)

        // Setup a reloadEngine to happen on next command run, and give a command to execute after reload
        @Throws(idException::class)
        abstract fun SetupReloadEngine(args: CmdArgs.idCmdArgs?)

        @Throws(idException::class)
        abstract fun PostReloadEngine(): Boolean

        // Default argument completion functions.
        class ArgCompletion_Boolean : argCompletion_t() {
            @Throws(idException::class)
            override fun run(args: CmdArgs.idCmdArgs?, callback: void_callback<String>) {
                callback.run(Str.va("%s 0", args!!.Argv(0)))
                callback.run(Str.va("%s 1", args.Argv(0)))
            }

            companion object {
                private val instance: argCompletion_t = ArgCompletion_Boolean()
                fun getInstance(): argCompletion_t {
                    return instance
                }
            }
        }

        class ArgCompletion_Integer(private val min: Int, private val max: Int) : argCompletion_t() {
            @Throws(idException::class)
            override fun run(args: CmdArgs.idCmdArgs?, callback: void_callback<String>) {
                for (i in min..max) {
                    callback.run(Str.va("%s %d", args!!.Argv(0), i))
                }
            }
        }

        //	template<final String *strings>
        class ArgCompletion_String(private val listDeclStrings: Array<String?>) : argCompletion_t() {
            @Throws(idException::class)
            override fun run(args: CmdArgs.idCmdArgs?, callback: void_callback<String>) {
                for (decl in listDeclStrings) {
                    callback.run(Str.va("%s %s", args!!.Argv(0), decl!!))
                }
            }
        }

        //	template<int type>
        class ArgCompletion_Decl(private val type: declType_t) : argCompletion_t() {
            @Throws(idException::class)
            override fun run(args: CmdArgs.idCmdArgs?, callback: void_callback<String>) {
                cmdSystem.ArgCompletion_DeclName(args, callback, type.ordinal)
            }
        }

        class ArgCompletion_FileName : argCompletion_t() {
            @Throws(idException::class)
            override fun run(args: CmdArgs.idCmdArgs?, callback: void_callback<String>) {
                cmdSystem.ArgCompletion_FolderExtension(args, callback, "/", true, "", null)
            }

            companion object {
                private val instance: argCompletion_t = ArgCompletion_FileName()
                fun getInstance(): argCompletion_t {
                    return instance
                }
            }
        }

        class ArgCompletion_MapName : argCompletion_t() {
            @Throws(idException::class)
            override fun run(args: CmdArgs.idCmdArgs?, callback: void_callback<String>) {
                cmdSystem.ArgCompletion_FolderExtension(args, callback, "maps/", true, ".map", null)
            }

            companion object {
                private val instance: argCompletion_t = ArgCompletion_MapName()
                fun getInstance(): argCompletion_t {
                    return instance
                }
            }
        }

        class ArgCompletion_ModelName : argCompletion_t() {
            @Throws(idException::class)
            override fun run(args: CmdArgs.idCmdArgs?, callback: void_callback<String>) {
                cmdSystem.ArgCompletion_FolderExtension(
                    args,
                    callback,
                    "models/",
                    false,
                    ".lwo",
                    ".ase",
                    ".md5mesh",
                    ".ma",
                    null
                )
            }

            companion object {
                private val instance: argCompletion_t = ArgCompletion_ModelName()


                fun getInstance(): argCompletion_t {
                    return instance
                }
            }
        }

        class ArgCompletion_SoundName : argCompletion_t() {
            @Throws(idException::class)
            override fun run(args: CmdArgs.idCmdArgs?, callback: void_callback<String>) {
                cmdSystem.ArgCompletion_FolderExtension(args, callback, "sound/", false, ".wav", ".ogg", null)
            }

            companion object {
                private val instance: argCompletion_t = ArgCompletion_SoundName()
                fun getInstance(): argCompletion_t {
                    return instance
                }
            }
        }

        class ArgCompletion_ImageName : argCompletion_t() {
            @Throws(idException::class)
            override fun run(args: CmdArgs.idCmdArgs?, callback: void_callback<String>) {
                cmdSystem.ArgCompletion_FolderExtension(
                    args,
                    callback,
                    "/",
                    false,
                    ".tga",
                    ".dds",
                    ".jpg",
                    ".pcx",
                    null
                )
            }

            companion object {
                private val instance: argCompletion_t = ArgCompletion_ImageName()


                fun getInstance(): argCompletion_t {
                    return instance
                }
            }
        }

        class ArgCompletion_VideoName : argCompletion_t() {
            @Throws(idException::class)
            override fun run(args: CmdArgs.idCmdArgs?, callback: void_callback<String>) {
                cmdSystem.ArgCompletion_FolderExtension(args, callback, "video/", false, ".roq", null)
            }

            companion object {
                private val instance: argCompletion_t = ArgCompletion_VideoName()


                fun getInstance(): argCompletion_t {
                    return instance
                }
            }
        }

        class ArgCompletion_ConfigName : argCompletion_t() {
            @Throws(idException::class)
            override fun run(args: CmdArgs.idCmdArgs?, callback: void_callback<String>) {
                cmdSystem.ArgCompletion_FolderExtension(args, callback, "/", true, ".cfg", null)
            }

            companion object {
                private val instance: argCompletion_t = ArgCompletion_ConfigName()
                fun getInstance(): argCompletion_t {
                    return instance
                }
            }
        }

        class ArgCompletion_SaveGame : argCompletion_t() {
            @Throws(idException::class)
            override fun run(args: CmdArgs.idCmdArgs?, callback: void_callback<String>) {
                cmdSystem.ArgCompletion_FolderExtension(args, callback, "SaveGames/", true, ".save", null)
            }

            companion object {
                private val instance: argCompletion_t = ArgCompletion_SaveGame()
                fun getInstance(): argCompletion_t {
                    return instance
                }
            }
        }

        class ArgCompletion_DemoName : argCompletion_t() {
            @Throws(idException::class)
            override fun run(args: CmdArgs.idCmdArgs?, callback: void_callback<String>) {
                cmdSystem.ArgCompletion_FolderExtension(args, callback, "demos/", true, ".demo", null)
            }

            companion object {
                private val instance: argCompletion_t = ArgCompletion_DemoName()
                fun getInstance(): argCompletion_t {
                    return instance
                }
            }
        }
    }

    /*
     ===============================================================================

     idCmdSystemLocal

     ===============================================================================
     */
    class commandDef_s {
        var argCompletion: argCompletion_t? = null
        var description: String = ""
        var flags: Long = 0
        var function: cmdFunction_t? = null
        var name: String = ""
        var next: commandDef_s? = null
        private fun set(last: commandDef_s) {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }
    }

    internal class idCmdSystemLocal : idCmdSystem() {
        //
        private var commands: commandDef_s? = null
        private val completionParms: idStrList
        private val completionString: idStr

        // a command stored to be executed after a reloadEngine and all associated commands have been processed
        private var postReload: CmdArgs.idCmdArgs?
        private var textBuf: ByteArray = ByteArray(MAX_CMD_BUFFER)
        private var textLength = 0

        // piggybacks on the text buffer, avoids tokenize again and screwing it up
        private val tokenizedCmds: idList<CmdArgs.idCmdArgs?>

        //
        //
        //
        private var wait = 0

        @Throws(idException::class)
        override fun Init() {
            AddCommand(
                "listCmds",
                List_f.getInstance(),
                CMD_FL_SYSTEM,
                "lists commands"
            )
            AddCommand("listSystemCmds", SystemList_f.getInstance(), CMD_FL_SYSTEM, "lists system commands")
            AddCommand(
                "listRendererCmds",
                RendererList_f.getInstance(),
                CMD_FL_SYSTEM,
                "lists renderer commands"
            )
            AddCommand("listSoundCmds", SoundList_f.getInstance(), CMD_FL_SYSTEM, "lists sound commands")
            AddCommand("listGameCmds", GameList_f.getInstance(), CMD_FL_SYSTEM, "lists game commands")
            AddCommand("listToolCmds", ToolList_f.getInstance(), CMD_FL_SYSTEM, "lists tool commands")
            AddCommand(
                "exec",
                Exec_f.getInstance(),
                CMD_FL_SYSTEM,
                "executes a config file",
                ArgCompletion_ConfigName.getInstance()
            ) //TODO:extend argCompletion_t
            AddCommand(
                "vstr",
                Vstr_f.getInstance(),
                CMD_FL_SYSTEM,
                "inserts the current value of a cvar as command text"
            )
            AddCommand("echo", Echo_f.getInstance(), CMD_FL_SYSTEM, "prints text")
            AddCommand("parse", Parse_f.getInstance(), CMD_FL_SYSTEM, "prints tokenized string")
            AddCommand(
                "wait",
                Wait_f.getInstance(),
                CMD_FL_SYSTEM,
                "delays remaining buffered commands one or more frames"
            )
            completionString.set("*")
            textLength = 0
        }

        override fun Shutdown() {

//            for (cmd = commands; cmd != null; cmd = commands) {
//                commands = commands.next;
//                cmd.name = cmd.description = null;
////                Mem_Free(cmd.name);
////                Mem_Free(cmd.description);
////		delete cmd;
//            }
            commands = null
            completionString.Clear()
            completionParms.clear()
            tokenizedCmds.Clear()
            postReload!!.Clear()
        }

        @Throws(idException::class)
        override fun AddCommand(
            cmdName: String,
            function: cmdFunction_t,
            flags: Long,
            description: String,
            argCompletion: argCompletion_t?
        ) {
            var cmd: commandDef_s?

            // fail if the command already exists
            cmd = commands
            while (cmd != null) {
                if (idStr.Cmp(cmdName, cmd.name) == 0) {
                    if (function !== cmd.function) {
                        idLib.common.Printf("idCmdSystemLocal::AddCommand: %s already defined\n", cmdName)
                    }
                    return
                }
                cmd = cmd.next
            }
            cmd = commandDef_s()
            cmd.name = cmdName //Mem_CopyString(cmdName);
            cmd.function = function
            cmd.argCompletion = argCompletion
            cmd.flags = flags
            cmd.description = description //Mem_CopyString(description);
            cmd.next = commands
            commands = cmd
        }

        override fun RemoveCommand(cmdName: String) {
            var cmd: commandDef_s?
            var last: commandDef_s?
            last = commands.also { cmd = it }
            while (cmd != null) {
                if (idStr.Cmp(cmdName, cmd.name) == 0) {
                    if (cmd == commands) { //first iteration.
                        commands = cmd.next //TODO:BOINTER. edit: check if this equals **last;
                    } else { //set last.next to last.next.next,
                        //where last.next is the current cmd. so basically setting overwriting the current node.
                        last!!.next = cmd.next
                    }
                    return
                }
                last = cmd
                cmd = cmd.next
            }
        }

        override fun RemoveFlaggedCommands(flags: Int) {
            var cmd = commands
            while (cmd != null) {
                val next = cmd.next
                cmd.description = ""
                cmd.name = ""
                cmd.function = null
                cmd.argCompletion = null
                cmd = next
            }
        }

        @Throws(idException::class)
        override fun CommandCompletion(callback: void_callback<String>) {
            var cmd: commandDef_s?
            cmd = commands
            while (cmd != null) {
                callback.run(cmd.name)
                cmd = cmd.next
            }
        }

        @Throws(idException::class)
        override fun ArgCompletion(cmdString: String, callback: void_callback<String>) {
            var cmd: commandDef_s?
            val args = CmdArgs.idCmdArgs()
            args.TokenizeString(cmdString, false)
            cmd = commands
            while (cmd != null) {
                if (null == cmd.argCompletion) {
                    cmd = cmd.next
                    continue
                }
                if (idStr.Icmp(args.Argv(0), cmd.name) == 0) {
                    cmd.argCompletion!!.run(args, callback)
                    break
                }
                cmd = cmd.next
            }
        }

        @Throws(idException::class)
        override fun BufferCommandText(exec: cmdExecution_t, text: String) {
            when (exec) {
                cmdExecution_t.CMD_EXEC_NOW -> {
                    ExecuteCommandText(text)
                }

                cmdExecution_t.CMD_EXEC_INSERT -> {
                    InsertCommandText(text)
                }

                cmdExecution_t.CMD_EXEC_APPEND -> {
                    AppendCommandText(text)
                }

                else -> {
                    idLib.common.FatalError("idCmdSystemLocal::BufferCommandText: bad exec type")
                }
            }
        }

        @Throws(idException::class)
        override fun ExecuteCommandBuffer() {
            var i: Int
            var text: CharArray? = null
            var txt: String
            var quotes: Int
            var args: CmdArgs.idCmdArgs? = CmdArgs.idCmdArgs()
            while (textLength != 0) {
                DBG_ExecuteCommandBuffer++
                if (wait != 0) {
                    // skip out while text still remains in buffer, leaving it for next frame
                    wait--
                    break
                }

                // find a \n or ; line break
                text = String(textBuf).toCharArray() //TODO:??
                quotes = 0
                i = 0
                while (i < textLength) {
                    if (text[i] == '"') {
                        quotes++
                    }
                    if (0 == quotes and 1 && text[i] == ';') {
                        break // don't break if inside a quoted string
                    }
                    if (text[i] == '\n' || text[i] == '\r') {
                        break
                    }
                    i++
                }

//                text[i] = 0;
                val bla = String(text)
                txt = bla.substring(0, i) //do not use ctos!
                if (0 == idStr.Cmp(txt, "_execTokenized")) {
                    args = tokenizedCmds[0]
                    tokenizedCmds.RemoveIndex(0)
                } else {
                    args!!.TokenizeString(txt, false)
                }

                // delete the text from the command buffer and move remaining commands down
                // this is necessary because commands (exec) can insert data at the
                // beginning of the text buffer
                if (i == textLength) {
                    textLength = 0
                } else {
                    val textBuf2 = textBuf
                    i++
                    textLength -= i
                    textBuf = ByteArray(textBuf.size) //memmove(text, text + i, textLength);
                    System.arraycopy(textBuf2, i, textBuf, 0, textLength)
                }

                // execute the command line that we have already tokenized
                ExecuteTokenizedString(args)
            }
        }

        @Throws(idException::class)
        override fun ArgCompletion_FolderExtension(
            args: CmdArgs.idCmdArgs?,
            callback: void_callback<String>,
            folder: String,
            stripFolder: Boolean,
            vararg objects: Any?
        ) {
            var i: Int
            var string: String?
            //            String extension;
//            va_list argPtr;
            string = args!!.Argv(0)
            string += " "
            string += args.Argv(1)
            if (completionString.Icmp(string) != 0) {
                val parm: idStr
                val path = idStr()
                var names: idFileList?
                completionString.set(string)
                completionParms.clear()
                parm = idStr(args.Argv(1))
                parm.ExtractFilePath(path)
                if (stripFolder || path.Length() == 0) {
                    path.set(folder).Append(path)
                }
                path.StripTrailing('/')

                // list folders
                names = FileSystem_h.fileSystem.ListFiles(path.toString(), "/", true, true)
                i = 0
                while (i < names.GetNumFiles()) {
                    var name = idStr(names.GetFile(i))
                    if (stripFolder) {
                        name.Strip(folder)
                    } else {
                        name.Strip("/")
                    }
                    name = idStr(args.Argv(0) + " $name" + "/")
                    completionParms.add(name)
                    i++
                }
                FileSystem_h.fileSystem.FreeFileList(names)

                // list files
//                va_start(argPtr, stripFolder);
//                for (extension = va_arg(argPtr, String); extension != null; extension = va_arg(argPtr, String)) {
                for (extension in objects) {
                    names = FileSystem_h.fileSystem.ListFiles(path.toString(), extension.toString(), true, true)
                    i = 0
                    while (i < names.GetNumFiles()) {
                        val name = idStr(names.GetFile(i))
                        if (stripFolder) {
                            name.Strip(folder)
                        } else {
                            name.Strip("/")
                        }
                        name.set(args.Argv(0) + " $name")
                        completionParms.add(name)
                        i++
                    }
                    FileSystem_h.fileSystem.FreeFileList(names)
                }
                //                va_end(argPtr);
            }
            i = 0
            while (i < completionParms.size()) {
                callback.run(completionParms[i].toString())
                i++
            }
        }

        @Throws(idException::class)
        override fun ArgCompletion_DeclName(args: CmdArgs.idCmdArgs?, callback: void_callback<String>, type: Int) {
            var i: Int
            val num: Int
            if (DeclManager.declManager == null) {
                return
            }
            num = DeclManager.declManager.GetNumDecls(declType_t.values()[type])
            i = 0
            while (i < num) {
                callback.run(
                    args!!.Argv(0) + " " + DeclManager.declManager.DeclByIndex(
                        declType_t.values()[type],
                        i,
                        false
                    )!!.GetName()
                )
                i++
            }
        }

        @Throws(idException::class)
        override fun BufferCommandArgs(exec: cmdExecution_t, args: CmdArgs.idCmdArgs?) {
            when (exec) {
                cmdExecution_t.CMD_EXEC_NOW -> {
                    ExecuteTokenizedString(args)
                }

                cmdExecution_t.CMD_EXEC_APPEND -> {
                    AppendCommandText("_execTokenized\n")
                    tokenizedCmds.Append(args)
                }

                else -> {
                    idLib.common.FatalError("idCmdSystemLocal::BufferCommandArgs: bad exec type")
                }
            }
        }

        @Throws(idException::class)
        override fun SetupReloadEngine(args: CmdArgs.idCmdArgs?) {
            BufferCommandText(cmdExecution_t.CMD_EXEC_APPEND, "reloadEngine\n")
            postReload = args
        }

        //
        //
        //
        @Throws(idException::class)
        override fun PostReloadEngine(): Boolean {
            if (0 == postReload!!.Argc()) {
                return false
            }
            BufferCommandArgs(cmdExecution_t.CMD_EXEC_APPEND, postReload)
            postReload!!.Clear()
            return true
        }

        fun SetWait(numFrames: Int) {
            wait = numFrames
        }

        fun GetCommands(): commandDef_s? {
            return commands
        }

        @Throws(idException::class)
        private fun ExecuteTokenizedString(args: CmdArgs.idCmdArgs?) {
            var cmd: commandDef_s?
            var prev: commandDef_s?

            // execute the command line
            if (0 == args!!.Argc()) {
                return  // no tokens
            }
            if (args.Argv(0) == "bla1") {
                args.set("map game/alphalabs1") //HACKME::11
            }

            // check registered command functions
            prev = commands.also { cmd = it }
            while (cmd != null) {

//                cmd = prev;
                if (idStr.Icmp(args.Argv(0), cmd.name) == 0) {
                    // rearrange the links so that the command will be
                    // near the head of the list next time it is used
                    if (cmd !== commands) { //no re-arranging necessary for first element.
                        prev!!.next = cmd.next
                        cmd.next = commands
                        commands = cmd
                    }
                    if (cmd.flags and (CMD_FL_CHEAT or CMD_FL_TOOL) != 0L && Session.session != null && Session.session.IsMultiplayer() && !CVarSystem.cvarSystem.GetCVarBool(
                            "net_allowCheats"
                        )
                    ) {
                        idLib.common.Printf("Command '%s' not valid in multiplayer mode.\n", cmd.name)
                        return
                    }
                    // perform the action
                    if (null == cmd.function) {
                        break
                    } else {
                        cmd.function!!.run(args)
                    }
                    return
                }
                prev = cmd
                cmd = cmd.next
            }

            // check cvars
            if (CVarSystem.cvarSystem.Command(args)) {
                return
            }
            idLib.common.Printf("Unknown command '%s'\n", args.Argv(0))
        }

        /*
         ============
         idCmdSystemLocal::ExecuteCommandText

         Tokenizes, then executes.
         ============
         */
        @Throws(idException::class)
        private fun ExecuteCommandText(text: String) {
            ExecuteTokenizedString(CmdArgs.idCmdArgs(text, false))
        }

        /*
         ============
         idCmdSystemLocal::InsertCommandText

         Adds command text immediately after the current command
         Adds a \n to the text
         ============
         */
        @Throws(idException::class)
        private fun InsertCommandText(text: String) {
            val len: Int
            var i: Int
            len = text.length + 1
            if (len + textLength > textBuf.size) {
                idLib.common.Printf("idCmdSystemLocal::InsertText: buffer overflow\n")
                return
            }

            // move the existing command text
            i = textLength - 1
            while (i >= 0) {
                textBuf[i + len] = textBuf[i]
                i--
            }

            // copy the new text in
//            memcpy(textBuf, text, len - 1);
            System.arraycopy(text.toByteArray(), 0, textBuf, 0, len - 1)

            // add a \n
            textBuf[len - 1] = '\n'.code.toByte()
            textLength += len
        }

        /*
         ============
         idCmdSystemLocal::AppendCommandText

         Adds command text at the end of the buffer, does NOT add a final \n
         ============
         */
        @Throws(idException::class)
        private fun AppendCommandText(text: String) {
            val l: Int
            l = text.length
            if (textLength + l >= textBuf.size) {
                idLib.common.Printf("idCmdSystemLocal::AppendText: buffer overflow\n")
                return
            }
            //	memcpy( textBuf + textLength, text, l );
            System.arraycopy(
                text.toByteArray(),
                0,
                textBuf,
                textLength,
                l
            ) //TODO:check 1 at the end. EDIT: it was an L ya blind fool!
            textLength += l
        }

        private class List_f private constructor() : cmdFunction_t() {
            @Throws(idException::class)
            override fun run(args: CmdArgs.idCmdArgs?) {
                ListByFlags(args, CMD_FL_ALL)
            }

            companion object {
                private val instance: cmdFunction_t = List_f()
                fun getInstance(): cmdFunction_t {
                    return instance
                }
            }
        }

        private class SystemList_f private constructor() : cmdFunction_t() {
            @Throws(idException::class)
            override fun run(args: CmdArgs.idCmdArgs?) {
                ListByFlags(args, CMD_FL_SYSTEM)
            }

            companion object {
                private val instance: cmdFunction_t = SystemList_f()
                fun getInstance(): cmdFunction_t {
                    return instance
                }
            }
        }

        private class RendererList_f private constructor() : cmdFunction_t() {
            @Throws(idException::class)
            override fun run(args: CmdArgs.idCmdArgs?) {
                ListByFlags(args, CMD_FL_RENDERER)
            }

            companion object {
                private val instance: cmdFunction_t = RendererList_f()
                fun getInstance(): cmdFunction_t {
                    return instance
                }
            }
        }

        private class SoundList_f private constructor() : cmdFunction_t() {
            @Throws(idException::class)
            override fun run(args: CmdArgs.idCmdArgs?) {
                ListByFlags(args, CMD_FL_SOUND)
            }

            companion object {
                private val instance: cmdFunction_t = SoundList_f()
                fun getInstance(): cmdFunction_t {
                    return instance
                }
            }
        }

        private class GameList_f private constructor() : cmdFunction_t() {
            @Throws(idException::class)
            override fun run(args: CmdArgs.idCmdArgs?) {
                ListByFlags(args, CMD_FL_GAME)
            }

            companion object {
                private val instance: cmdFunction_t = GameList_f()
                fun getInstance(): cmdFunction_t {
                    return instance
                }
            }
        }

        private class ToolList_f private constructor() : cmdFunction_t() {
            @Throws(idException::class)
            override fun run(args: CmdArgs.idCmdArgs?) {
                ListByFlags(args, CMD_FL_TOOL)
            }

            companion object {
                private val instance: cmdFunction_t = ToolList_f()
                fun getInstance(): cmdFunction_t {
                    return instance
                }
            }
        }

        //private	static void				Exec_f( const idCmdArgs &args );
        private class Exec_f private constructor() : cmdFunction_t() {
            @Throws(idException::class)
            override fun run(args: CmdArgs.idCmdArgs?) {
                val f = arrayOfNulls<ByteBuffer>(1)
                val len: Int
                val filename: idStr
                if (args!!.Argc() != 2) {
                    idLib.common.Printf("exec <filename> : execute a script file\n")
                    return
                }
                filename = idStr(args.Argv(1))
                filename.DefaultFileExtension(".cfg")
                len = FileSystem_h.fileSystem.ReadFile(filename.toString(),  /*reinterpret_cast<void **>*/f, null)
                if (null == f[0]) {
                    idLib.common.Printf("couldn't exec %s\n", args.Argv(1))
                    return
                }
                idLib.common.Printf("execing %s\n", args.Argv(1))
                cmdSystemLocal.BufferCommandText(cmdExecution_t.CMD_EXEC_INSERT, String(f[0]!!.array()))
                FileSystem_h.fileSystem.FreeFile(f)
            }

            companion object {
                private val instance: cmdFunction_t = Exec_f()
                fun getInstance(): cmdFunction_t {
                    return instance
                }
            }
        }

        /*
         ===============
         idCmdSystemLocal::Vstr_f

         Inserts the current value of a cvar as command text
         ===============
         */
        private class Vstr_f private constructor() : cmdFunction_t() {
            @Throws(idException::class)
            override fun run(args: CmdArgs.idCmdArgs?) {
                val v: String?
                if (args!!.Argc() != 2) {
                    idLib.common.Printf("vstr <variablename> : execute a variable command\n")
                    return
                }
                v = CVarSystem.cvarSystem.GetCVarString(args.Argv(1))
                cmdSystemLocal.BufferCommandText(cmdExecution_t.CMD_EXEC_APPEND, Str.va("%s\n", v))
            }

            companion object {
                private val instance: cmdFunction_t = Vstr_f()
                fun getInstance(): cmdFunction_t {
                    return instance
                }
            }
        }

        /*
         ===============
         idCmdSystemLocal::Echo_f

         Just prints the rest of the line to the console
         ===============
         */
        private class Echo_f private constructor() : cmdFunction_t() {
            @Throws(idException::class)
            override fun run(args: CmdArgs.idCmdArgs?) {
                var i: Int
                i = 1
                while (i < args!!.Argc()) {
                    idLib.common.Printf("%s ", args.Argv(i))
                    i++
                }
                idLib.common.Printf("\n")
            }

            companion object {
                private val instance: cmdFunction_t = Echo_f()
                fun getInstance(): cmdFunction_t {
                    return instance
                }
            }
        }

        /*
         ============
         idCmdSystemLocal::Parse_f

         This just prints out how the rest of the line was parsed, as a debugging tool.
         ============
         */
        private class Parse_f private constructor() : cmdFunction_t() {
            @Throws(idException::class)
            override fun run(args: CmdArgs.idCmdArgs?) {
                var i: Int
                i = 0
                while (i < args!!.Argc()) {
                    idLib.common.Printf("%d: %s\n", i, args.Argv(i))
                    i++
                }
            }

            companion object {
                private val instance: cmdFunction_t = Parse_f()
                fun getInstance(): cmdFunction_t {
                    return instance
                }
            }
        }

        /*
         ============
         idCmdSystemLocal::Wait_f

         Causes execution of the remainder of the command buffer to be delayed until next frame.
         ============
         */
        private class Wait_f private constructor() : cmdFunction_t() {
            override fun run(args: CmdArgs.idCmdArgs?) {
                if (args!!.Argc() == 2) {
                    cmdSystemLocal.SetWait(args.Argv(1).toInt())
                } else {
                    cmdSystemLocal.SetWait(1)
                }
            }

            companion object {
                private val instance: cmdFunction_t = Wait_f()
                fun getInstance(): cmdFunction_t {
                    return instance
                }
            }
        }

        //private	static void				PrintMemInfo_f( const idCmdArgs &args );
        private class PrintMemInfo_f private constructor() : cmdFunction_t() {
            @Throws(idException::class)
            override fun run(args: CmdArgs.idCmdArgs?) {
                ListByFlags(args, CMD_FL_SYSTEM)
            }

            companion object {
                private val instance: cmdFunction_t = PrintMemInfo_f()
                fun getInstance(): cmdFunction_t {
                    return instance
                }
            }
        }

        companion object {
            private const val MAX_CMD_BUFFER = 0x10000
            private var DBG_ExecuteCommandBuffer = 0

            @Throws(idException::class)
            private fun ListByFlags(args: CmdArgs.idCmdArgs?, cmdFlags_t: Long) {
                var i: Int
                var match: String
                var cmd: commandDef_s?
                val cmdList = idList<commandDef_s>()
                if (args!!.Argc() > 1) {
                    match = args.Args(1, -1)
                    match = match.replace(" ".toRegex(), "")
                } else {
                    match = ""
                }
                cmd = cmdSystemLocal.GetCommands()
                while (cmd != null) {
                    if (0L == cmd.flags and cmdFlags_t) {
                        cmd = cmd.next
                        continue
                    }
                    if (!match.isEmpty() && idStr(cmd.name).Filter(match, false)) {
                        cmd = cmd.next
                        continue
                    }
                    cmdList.Append(cmd)
                    cmd = cmd.next
                }
                cmdList.Sort()
                i = 0
                while (i < cmdList.Num()) {
                    cmd = cmdList[i]
                    idLib.common.Printf("  %-21s %s\n", cmd.name, cmd.description)
                    i++
                }
                idLib.common.Printf("%d commands\n", cmdList.Num())
            }
        }

        init {
            completionString = idStr()
            completionParms = idStrList()
            tokenizedCmds = idList()
            postReload = CmdArgs.idCmdArgs()
        }
    }

    /*
     ============
     idCmdSystemLocal::ListByFlags
     ============
     */
    // NOTE: the const wonkyness is required to make msvc happy
    class idListSortCompare : cmp_t<commandDef_s?> {
        override fun compare(a: commandDef_s?, b: commandDef_s?): Int {
            return idStr.Icmp(a!!.name, b!!.name)
        }
    }
}
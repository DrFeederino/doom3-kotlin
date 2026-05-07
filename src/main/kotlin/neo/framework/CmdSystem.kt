/*
 * Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
 * Translated to Kotlin by Dr. Feederino with support of Claude Code
 *
 * This file is part of the Doom 3 Kotlin project.
 * Original source: neo/framework/CmdSystem.h, neo/framework/CmdSystem.cpp
 *
 * Doom 3 Source Code is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Doom 3 Source Code is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Doom 3 Source Code.  If not, see <http://www.gnu.org/licenses/>.
 */

package neo.framework

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

object CmdSystem {

    // command flags
    const val CMD_FL_ALL: Long = -1
    val CMD_FL_CHEAT: Long = BIT(0).toLong()    // command is considered a cheat
    val CMD_FL_SYSTEM: Long = BIT(1).toLong()   // system command
    val CMD_FL_RENDERER: Long = BIT(2).toLong() // renderer command
    val CMD_FL_SOUND: Long = BIT(3).toLong()    // sound command
    val CMD_FL_GAME: Long = BIT(4).toLong()     // game command
    val CMD_FL_TOOL: Long = BIT(5).toLong()     // tool command

    private var cmdSystemLocal: idCmdSystemLocal = idCmdSystemLocal()
    var cmdSystem: idCmdSystem = cmdSystemLocal

    // NOTE: Kotlin-only, no C++ counterpart — utility for injection
    fun setCmdSystems(cmdSystem: idCmdSystem) {
        cmdSystemLocal = cmdSystem as idCmdSystemLocal
        CmdSystem.cmdSystem = cmdSystemLocal
    }

    // parameters for command buffer stuffing
    enum class cmdExecution_t {
        CMD_EXEC_NOW,       // don't return until completed
        CMD_EXEC_INSERT,    // insert at current position, but don't run yet
        CMD_EXEC_APPEND     // add to end of the command buffer (normal case)
    }

    // command function
    abstract class cmdFunction_t {
        @Throws(idException::class)
        abstract fun run(args: CmdArgs.idCmdArgs?)
    }

    // argument completion function
    abstract class argCompletion_t {
        @Throws(idException::class)
        abstract fun run(args: CmdArgs.idCmdArgs?, callback: (String) -> Unit)
        fun run(args: CmdArgs.idCmdArgs?, callback: (String) -> Unit, type: Int) {}
    }

    /*
    ===============================================================================

        idCmdSystem

    ===============================================================================
    */
    abstract class idCmdSystem {
        //
        // virtual ~idCmdSystem( void ) {}
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
            description: String
        ) {
            AddCommand(cmdName, function, flags, description, null)
        }

        // Removes a command.
        abstract fun RemoveCommand(cmdName: String)

        // Remove all commands with one of the flags set.
        abstract fun RemoveFlaggedCommands(flags: Int)

        // Command and argument completion using callback for each valid string.
        @Throws(idException::class)
        abstract fun CommandCompletion(callback: (String) -> Unit)

        @Throws(idException::class)
        abstract fun ArgCompletion(cmdString: String, callback: (String) -> Unit)

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
            callback: (String) -> Unit,
            folder: String,
            stripFolder: Boolean,
            vararg objects: Any?
        )

        // Base for decl name auto-completion.
        @Throws(idException::class)
        abstract fun ArgCompletion_DeclName(args: CmdArgs.idCmdArgs?, callback: (String) -> Unit, type: Int)

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
            override fun run(args: CmdArgs.idCmdArgs?, callback: (String) -> Unit) {
                callback(Str.va("%s 0", args!!.Argv(0)))
                callback(Str.va("%s 1", args.Argv(0)))
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
            override fun run(args: CmdArgs.idCmdArgs?, callback: (String) -> Unit) {
                for (i in min..max) {
                    callback(Str.va("%s %d", args!!.Argv(0), i))
                }
            }
        }

        class ArgCompletion_String(private val listDeclStrings: Array<String?>) : argCompletion_t() {
            @Throws(idException::class)
            override fun run(args: CmdArgs.idCmdArgs?, callback: (String) -> Unit) {
                for (decl in listDeclStrings) {
                    callback(Str.va("%s %s", args!!.Argv(0), decl!!))
                }
            }
        }

        class ArgCompletion_Decl(private val type: declType_t) : argCompletion_t() {
            @Throws(idException::class)
            override fun run(args: CmdArgs.idCmdArgs?, callback: (String) -> Unit) {
                cmdSystem.ArgCompletion_DeclName(args, callback, type.ordinal)
            }
        }

        class ArgCompletion_FileName : argCompletion_t() {
            @Throws(idException::class)
            override fun run(args: CmdArgs.idCmdArgs?, callback: (String) -> Unit) {
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
            override fun run(args: CmdArgs.idCmdArgs?, callback: (String) -> Unit) {
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
            override fun run(args: CmdArgs.idCmdArgs?, callback: (String) -> Unit) {
                cmdSystem.ArgCompletion_FolderExtension(
                    args, callback, "models/", false,
                    ".lwo", ".ase", ".md5mesh", ".ma", null
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
            override fun run(args: CmdArgs.idCmdArgs?, callback: (String) -> Unit) {
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
            override fun run(args: CmdArgs.idCmdArgs?, callback: (String) -> Unit) {
                cmdSystem.ArgCompletion_FolderExtension(
                    args, callback, "/", false,
                    ".tga", ".dds", ".jpg", ".pcx", null
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
            override fun run(args: CmdArgs.idCmdArgs?, callback: (String) -> Unit) {
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
            override fun run(args: CmdArgs.idCmdArgs?, callback: (String) -> Unit) {
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
            override fun run(args: CmdArgs.idCmdArgs?, callback: (String) -> Unit) {
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
            override fun run(args: CmdArgs.idCmdArgs?, callback: (String) -> Unit) {
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
        var next: commandDef_s? = null
        var name: String = ""
        var function: cmdFunction_t? = null
        var argCompletion: argCompletion_t? = null
        var flags: Long = 0
        var description: String = ""
    }

    internal class idCmdSystemLocal : idCmdSystem() {

        private var commands: commandDef_s? = null

        private var wait: Int = 0
        private var textLength: Int = 0
        private var textBuf: ByteArray = ByteArray(MAX_CMD_BUFFER)

        private val completionString: idStr = idStr()
        private val completionParms: idStrList = idStrList()

        // piggybacks on the text buffer, avoids tokenize again and screwing it up
        private val tokenizedCmds: idList<CmdArgs.idCmdArgs?> = idList()

        // a command stored to be executed after a reloadEngine and all associated commands have been processed
        private var postReload: CmdArgs.idCmdArgs = CmdArgs.idCmdArgs()

        /*
        ============
        idCmdSystemLocal::Init
        ============
        */
        @Throws(idException::class)
        override fun Init() {
            AddCommand("listCmds", List_f.getInstance(), CMD_FL_SYSTEM, "lists commands")
            AddCommand("listSystemCmds", SystemList_f.getInstance(), CMD_FL_SYSTEM, "lists system commands")
            AddCommand("listRendererCmds", RendererList_f.getInstance(), CMD_FL_SYSTEM, "lists renderer commands")
            AddCommand("listSoundCmds", SoundList_f.getInstance(), CMD_FL_SYSTEM, "lists sound commands")
            AddCommand("listGameCmds", GameList_f.getInstance(), CMD_FL_SYSTEM, "lists game commands")
            AddCommand("listToolCmds", ToolList_f.getInstance(), CMD_FL_SYSTEM, "lists tool commands")
            AddCommand(
                "exec", Exec_f.getInstance(), CMD_FL_SYSTEM, "executes a config file",
                ArgCompletion_ConfigName.getInstance()
            )
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

        /*
        ============
        idCmdSystemLocal::Shutdown
        ============
        */
        override fun Shutdown() {
            // In C++ this frees each command node; in Kotlin the GC handles it
            commands = null
            completionString.Clear()
            completionParms.clear()
            tokenizedCmds.Clear()
            postReload.Clear()
        }

        /*
        ============
        idCmdSystemLocal::AddCommand
        ============
        */
        @Throws(idException::class)
        override fun AddCommand(
            cmdName: String,
            function: cmdFunction_t,
            flags: Long,
            description: String,
            argCompletion: argCompletion_t?
        ) {
            // fail if the command already exists
            var cmd = commands
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
            cmd.name = cmdName
            cmd.function = function
            cmd.argCompletion = argCompletion
            cmd.flags = flags
            cmd.description = description
            cmd.next = commands
            commands = cmd
        }

        /*
        ============
        idCmdSystemLocal::RemoveCommand
        ============
        */
        override fun RemoveCommand(cmdName: String) {
            var cmd: commandDef_s?
            var last: commandDef_s?
            last = commands.also { cmd = it }
            while (cmd != null) {
                if (idStr.Cmp(cmdName, cmd!!.name) == 0) {
                    if (cmd == commands) {
                        commands = cmd!!.next
                    } else {
                        last!!.next = cmd!!.next
                    }
                    return
                }
                last = cmd
                cmd = cmd!!.next
            }
        }

        /*
        ============
        idCmdSystemLocal::RemoveFlaggedCommands
        ============
        */
        // FIX: Original code did not check flags and did not remove commands from the linked list.
        // Now properly implements the C++ double-pointer traversal: removes matching commands from the list.
        override fun RemoveFlaggedCommands(flags: Int) {
            var prev: commandDef_s? = null
            var cmd = commands
            while (cmd != null) {
                if (cmd.flags and flags.toLong() != 0L) {
                    // Remove this command from the linked list
                    if (prev == null) {
                        commands = cmd.next
                    } else {
                        prev.next = cmd.next
                    }
                    // Don't advance prev — it still points to the node before the next one
                    cmd = if (prev == null) commands else prev.next
                    continue
                }
                prev = cmd
                cmd = cmd.next
            }
        }

        /*
        ============
        idCmdSystemLocal::CommandCompletion
        ============
        */
        @Throws(idException::class)
        override fun CommandCompletion(callback: (String) -> Unit) {
            var cmd = commands
            while (cmd != null) {
                callback(cmd.name)
                cmd = cmd.next
            }
        }

        /*
        ============
        idCmdSystemLocal::ArgCompletion
        ============
        */
        @Throws(idException::class)
        override fun ArgCompletion(cmdString: String, callback: (String) -> Unit) {
            val args = CmdArgs.idCmdArgs()
            args.TokenizeString(cmdString, false)

            var cmd = commands
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

        /*
        ============
        idCmdSystemLocal::BufferCommandText
        ============
        */
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

        /*
        ============
        idCmdSystemLocal::ExecuteCommandBuffer
        ============
        */
        @Throws(idException::class)
        override fun ExecuteCommandBuffer() {
            var i: Int
            var quotes: Int
            var args: CmdArgs.idCmdArgs? = CmdArgs.idCmdArgs()

            while (textLength != 0) {

                if (wait != 0) {
                    // skip out while text still remains in buffer, leaving it for next frame
                    wait--
                    break
                }

                // find a \n or ; line break
                val text = String(textBuf, 0, textLength)
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

                val txt = text.substring(0, i)

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
                    i++
                    textLength -= i
                    System.arraycopy(textBuf, i, textBuf, 0, textLength)
                }

                // execute the command line that we have already tokenized
                ExecuteTokenizedString(args)
            }
        }

        /*
        ============
        idCmdSystemLocal::ArgCompletion_FolderExtension
        ============
        */
        @Throws(idException::class)
        override fun ArgCompletion_FolderExtension(
            args: CmdArgs.idCmdArgs?,
            callback: (String) -> Unit,
            folder: String,
            stripFolder: Boolean,
            vararg objects: Any?
        ) {
            var i: Int
            var string: String?

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
                // FIX: C++ uses NULL sentinel to terminate va_list; filter out null entries
                for (extension in objects) {
                    if (extension == null) break
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
            }
            i = 0
            while (i < completionParms.size()) {
                callback(completionParms[i].toString())
                i++
            }
        }

        /*
        ============
        idCmdSystemLocal::ArgCompletion_DeclName
        ============
        */
        @Throws(idException::class)
        override fun ArgCompletion_DeclName(args: CmdArgs.idCmdArgs?, callback: (String) -> Unit, type: Int) {
            if (DeclManager.declManager == null) {
                return
            }
            val num = DeclManager.declManager.GetNumDecls(declType_t.values()[type])
            var i = 0
            while (i < num) {
                callback(
                    args!!.Argv(0) + " " + DeclManager.declManager.DeclByIndex(
                        declType_t.values()[type], i, false
                    )!!.GetName()
                )
                i++
            }
        }

        /*
        ============
        idCmdSystemLocal::BufferCommandArgs
        ============
        */
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

        /*
        ============
        idCmdSystemLocal::SetupReloadEngine
        ============
        */
        @Throws(idException::class)
        override fun SetupReloadEngine(args: CmdArgs.idCmdArgs?) {
            BufferCommandText(cmdExecution_t.CMD_EXEC_APPEND, "reloadEngine\n")
            postReload = args ?: CmdArgs.idCmdArgs()
        }

        /*
        ============
        idCmdSystemLocal::PostReloadEngine
        ============
        */
        @Throws(idException::class)
        override fun PostReloadEngine(): Boolean {
            if (0 == postReload.Argc()) {
                return false
            }
            BufferCommandArgs(cmdExecution_t.CMD_EXEC_APPEND, postReload)
            postReload.Clear()
            return true
        }

        fun SetWait(numFrames: Int) {
            wait = numFrames
        }

        fun GetCommands(): commandDef_s? {
            return commands
        }

        /*
        ============
        idCmdSystemLocal::ExecuteTokenizedString
        ============
        */
        @Throws(idException::class)
        private fun ExecuteTokenizedString(args: CmdArgs.idCmdArgs?) {
            var cmd: commandDef_s?
            var prev: commandDef_s?

            // execute the command line
            if (0 == args!!.Argc()) {
                return // no tokens
            }

            // check registered command functions
            prev = commands.also { cmd = it }
            while (cmd != null) {

                if (idStr.Icmp(args.Argv(0), cmd!!.name) == 0) {
                    // rearrange the links so that the command will be
                    // near the head of the list next time it is used
                    if (cmd !== commands) {
                        prev!!.next = cmd!!.next
                        cmd!!.next = commands
                        commands = cmd
                    }

                    if (cmd!!.flags and (CMD_FL_CHEAT or CMD_FL_TOOL) != 0L
                        && Session.session != null && Session.session.IsMultiplayer()
                        && !CVarSystem.cvarSystem.GetCVarBool("net_allowCheats")
                    ) {
                        idLib.common.Printf("Command '%s' not valid in multiplayer mode.\n", cmd!!.name)
                        return
                    }
                    // perform the action
                    if (null == cmd!!.function) {
                        break
                    } else {
                        cmd!!.function!!.run(args)
                    }
                    return
                }
                prev = cmd
                cmd = cmd!!.next
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
            val len: Int = text.length + 1

            if (len + textLength > textBuf.size) {
                idLib.common.Printf("idCmdSystemLocal::InsertText: buffer overflow\n")
                return
            }

            // move the existing command text
            var i = textLength - 1
            while (i >= 0) {
                textBuf[i + len] = textBuf[i]
                i--
            }

            // copy the new text in
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
            val l: Int = text.length

            if (textLength + l >= textBuf.size) {
                idLib.common.Printf("idCmdSystemLocal::AppendText: buffer overflow\n")
                return
            }
            System.arraycopy(text.toByteArray(), 0, textBuf, textLength, l)
            textLength += l
        }

        /*
        ============
        idCmdSystemLocal::List_f
        ============
        */
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

        /*
        ============
        idCmdSystemLocal::SystemList_f
        ============
        */
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

        /*
        ============
        idCmdSystemLocal::RendererList_f
        ============
        */
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

        /*
        ============
        idCmdSystemLocal::SoundList_f
        ============
        */
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

        /*
        ============
        idCmdSystemLocal::GameList_f
        ============
        */
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

        /*
        ============
        idCmdSystemLocal::ToolList_f
        ============
        */
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

        /*
        ===============
        idCmdSystemLocal::Exec_f
        ===============
        */
        private class Exec_f private constructor() : cmdFunction_t() {
            @Throws(idException::class)
            override fun run(args: CmdArgs.idCmdArgs?) {
                val f = arrayOfNulls<ByteBuffer>(1)

                if (args!!.Argc() != 2) {
                    idLib.common.Printf("exec <filename> : execute a script file\n")
                    return
                }

                val filename = idStr(args.Argv(1))
                filename.DefaultFileExtension(".cfg")
                FileSystem_h.fileSystem.ReadFile(filename.toString(), f, null)
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
                if (args!!.Argc() != 2) {
                    idLib.common.Printf("vstr <variablename> : execute a variable command\n")
                    return
                }

                val v = CVarSystem.cvarSystem.GetCVarString(args.Argv(1))
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
                var i = 1
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
                var i = 0
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

        companion object {
            private const val MAX_CMD_BUFFER = 0x10000

            /*
            ============
            idCmdSystemLocal::ListByFlags
            ============
            */
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
                    // FIX: Filter condition was inverted — C++ skips when Filter() == 0 (no match),
                    // meaning only matching commands are shown. The Kotlin code was skipping matches.
                    if (match.isNotEmpty() && !idStr(cmd.name).Filter(match, false)) {
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
                    idLib.common.Printf("  %-21s %s\n", cmd!!.name, cmd.description)
                    i++
                }
                idLib.common.Printf("%d commands\n", cmdList.Num())
            }
        }
    }

    // NOTE: the const wonkyness is required to make msvc happy
    class idListSortCompare : cmp_t<commandDef_s?> {
        override fun compare(a: commandDef_s?, b: commandDef_s?): Int {
            return idStr.Icmp(a!!.name, b!!.name)
        }
    }
}

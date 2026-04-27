package neo.framework

import neo.TempDump
import neo.TempDump.TODO_Exception
import neo.framework.CVarSystem.idCVar
import neo.framework.CmdSystem.cmdFunction_t
import neo.framework.CmdSystem.idCmdSystem.ArgCompletion_FileName
import neo.framework.CmdSystem.idCmdSystem.ArgCompletion_Integer
import neo.framework.DeclEntityDef.idDeclEntityDef
import neo.framework.DeclManager.declType_t
import neo.framework.DeclManager.idDecl
import neo.framework.File_h.fsOrigin_t
import neo.framework.File_h.idFile
import neo.framework.File_h.idFile_InZip
import neo.framework.File_h.idFile_Permanent
import neo.idlib.CmdArgs
import neo.idlib.Dict_h.idDict
import neo.idlib.LittleLong
import neo.idlib.MAX_STRING_CHARS
import neo.idlib.Text.Lexer
import neo.idlib.Text.Lexer.idLexer
import neo.idlib.Text.Parser.idParser
import neo.idlib.Text.Str
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Token
import neo.idlib.Text.Token.idToken
import neo.idlib.containers.CInt
import neo.idlib.containers.List.idList
import neo.idlib.containers.idHashIndex
import neo.idlib.containers.idStrList
import neo.idlib.hashing.MD4_BlockChecksum
import neo.idlib.idLib
import neo.sys.*
import neo.sys.win_main.Sys_EnterCriticalSection
import neo.sys.win_main.Sys_LeaveCriticalSection
import neo.sys.win_main.Sys_TriggerEvent
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.*
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

object FileSystem_h {
    /*
     ===============================================================================

     File System

     No stdio calls should be used by any part of the game, because of all sorts
     of directory and separator char issues. Throughout the game a forward slash
     should be used as a separator. The file system takes care of the conversion
     to an OS specific separator. The file system treats all file and directory
     names as case insensitive.

     The following cvars store paths used by the file system:

     "fs_basepath"		path to local install, read-only
     "fs_savepath"		path to config, save game, etc. files, read & write
     "fs_cdpath"			path to cd, read-only
     "fs_devpath"		path to files created during development, read & write

     The base path for file saving can be set to "fs_savepath" or "fs_devpath".

     ===============================================================================
     */
    const val FILE_NOT_FOUND_TIMESTAMP = -0x1
    const val MAX_OSPATH = 256
    const val MAX_PURE_PAKS = 128
    val ADDON_CONFIG: String = "addon.conf"
    val BINARY_CONFIG: String = "binary.conf"

    /*
     =============================================================================

     DOOM FILESYSTEM

     All of Doom's data access is through a hierarchical file system, but the contents of
     the file system can be transparently merged from several sources.

     A "relativePath" is a reference to game file data, which must include a terminating zero.
     "..", "\\", and ":" are explicitly illegal in qpaths to prevent any references
     outside the Doom directory system.

     The "base path" is the path to the directory holding all the game directories and
     usually the executable. It defaults to the current directory, but can be overridden
     with "+set fs_basepath c:\doom" on the command line. The base path cannot be modified
     at all after startup.

     The "save path" is the path to the directory where game files will be saved. It defaults
     to the base path, but can be overridden with a "+set fs_savepath c:\doom" on the
     command line. Any files that are created during the game (demos, screenshots, etc.) will
     be created reletive to the save path.

     The "cd path" is the path to an alternate hierarchy that will be searched if a file
     is not located in the base path. A user can do a partial install that copies some
     data to a base path created on their hard drive and leave the rest on the cd. It defaults
     to the current directory, but it can be overridden with "+set fs_cdpath g:\doom" on the
     command line.

     The "dev path" is the path to an alternate hierarchy where the editors and tools used
     during development (Radiant, AF editor, dmap, runAAS) will write files to. It defaults to
     the cd path, but can be overridden with a "+set fs_devpath c:\doom" on the command line.

     If a user runs the game directly from a CD, the base path would be on the CD. This
     should still function correctly, but all file writes will fail (harmlessly).

     The "base game" is the directory under the paths where data comes from by default, and
     can be either "base" or "demo".

     The "current game" may be the same as the base game, or it may be the name of another
     directory under the paths that should be searched for files before looking in the base
     game. The game directory is set with "+set fs_game myaddon" on the command line. This is
     the basis for addons.

     No other directories outside of the base game and current game will ever be referenced by
     filesystem functions.

     To save disk space and speed up file loading, directory trees can be collapsed into zip
     files. The files use a ".pk4" extension to prevent users from unzipping them accidentally,
     but otherwise they are simply normal zip files. A game directory can have multiple zip
     files of the form "pak0.pk4", "pak1.pk4", etc. Zip files are searched in decending order
     from the highest number to the lowest, and will always take precedence over the filesystem.
     This allows a pk4 distributed as a patch to override all existing data.

     Because we will have updated executables freely available online, there is no point to
     trying to restrict demo / oem versions of the game with code changes. Demo / oem versions
     should be exactly the same executables as release versions, but with different data that
     automatically restricts where game media can come from to prevent add-ons from working.

     After the paths are initialized, Doom will look for the product.txt file. If not found
     and verified, the game will run in restricted mode. In restricted mode, only files
     contained in demo/pak0.pk4 will be available for loading, and only if the zip header is
     verified to not have been modified. A single exception is made for DoomConfig.cfg. Files
     can still be written out in restricted mode, so screenshots and demos are allowed.
     Restricted mode can be tested by setting "+set fs_restrict 1" on the command line, even
     if there is a valid product.txt under the basepath or cdpath.

     If the "fs_copyfiles" cvar is set to 1, then every time a file is sourced from the cd
     path, it will be copied over to the save path. This is a development aid to help build
     test releases and to copy working sets of files.

     If the "fs_copyfiles" cvar is set to 2, any file found in fs_cdpath that is newer than
     it's fs_savepath version will be copied to fs_savepath (in addition to the fs_copyfiles 1
     behaviour).

     If the "fs_copyfiles" cvar is set to 3, files from both basepath and cdpath will be copied
     over to the save path. This is useful when copying working sets of files mainly from base
     path with an additional cd path (which can be a slower network drive for instance).

     If the "fs_copyfiles" cvar is set to 4, files that exist in the cd path but NOT the base path
     will be copied to the save path

     NOTE: fs_copyfiles and case sensitivity. On fs_caseSensitiveOS 0 filesystems ( win32 ), the
     copied files may change casing when copied over.

     The relative path "sound/newstuff/test.wav" would be searched for in the following places:

     for save path, dev path, base path, cd path:
     for current game, base game:
     search directory
     search zip files

     downloaded files, to be written to save path + current game's directory

     The filesystem can be safely shutdown and reinitialized with different
     basedir / cddir / game combinations, but all other subsystems that rely on it
     (sound, video) must also be forced to restart.


     "fs_caseSensitiveOS":
     This cvar is set on operating systems that use case sensitive filesystems (Linux and OSX)
     It is a common situation to have the media reference filenames, whereas the file on disc
     only matches in a case-insensitive way. When "fs_caseSensitiveOS" is set, the filesystem
     will always do a case insensitive search.
     IMPORTANT: This only applies to files, and not to directories. There is no case-insensitive
     matching of directories. All directory names should be lowercase, when "com_developer" is 1,
     the filesystem will warn when it catches bad directory situations (regardless of the
     "fs_caseSensitiveOS" setting)
     When bad casing in directories happen and "fs_caseSensitiveOS" is set, BuildOSPath will
     attempt to correct the situation by forcing the path to lowercase. This assumes the media
     is stored all lowercase.

     "additional mod path search":
     fs_game_base can be used to set an additional search path
     in search order, fs_game, fs_game_base, BASEGAME
     for instance to base a mod of D3 + D3XP assets, fs_game mymod, fs_game_base d3xp

     =============================================================================
     */
    // define to fix special-cases for GetPackStatus so that files that shipped in
    // the wrong place for Doom 3 don't break pure servers.
    const val DOOM3_PURE_SPECIAL_CASES = true
    const val FILE_HASH_SIZE = 1024
    const val FSFLAG_BINARY_ONLY = 1 shl 3
    const val FSFLAG_PURE_NOREF = 1 shl 2
    const val FSFLAG_SEARCH_ADDONS = 1 shl 4

    //
    // search flags when opening a file
    const val FSFLAG_SEARCH_DIRS = 1 shl 0
    const val FSFLAG_SEARCH_PAKS = 1 shl 1

    //
    // 3 search path (fs_savepath fs_basepath fs_cdpath)
    // + .jpg and .tga
    const val MAX_CACHED_DIRS = 6

    //
    // how many OSes to handle game paks for ( we don't have to know them precisely )
    const val MAX_GAME_OS = 6

    //
    const val MAX_ZIPPED_FILE_NAME = 2048
    val pureExclusions1: Array<pureExclusion_s> = arrayOf(
        pureExclusion_s(0, 0, null, "/", excludeExtension.getInstance()),
        pureExclusion_s(0, 0, null, "\\", excludeExtension.getInstance()),
        pureExclusion_s(0, 0, null, ".pda", excludeExtension.getInstance()),
        pureExclusion_s(0, 0, null, ".gui", excludeExtension.getInstance()),
        pureExclusion_s(0, 0, null, ".pd", excludeExtension.getInstance()),
        pureExclusion_s(0, 0, null, ".lang", excludeExtension.getInstance()),
        pureExclusion_s(0, 0, "sound/VO", ".ogg", excludePathPrefixAndExtension.getInstance()),
        pureExclusion_s(
            0,
            0,
            "sound/VO",
            ".wav",
            excludePathPrefixAndExtension.getInstance()
        ),  // add any special-case files or paths for pure servers here
        pureExclusion_s(0, 0, "sound/ed/marscity/vo_intro_cutscene.ogg", null, excludeFullName.getInstance()),
        pureExclusion_s(0, 0, "sound/weapons/soulcube/energize_01.ogg", null, excludeFullName.getInstance()),
        pureExclusion_s(0, 0, "sound/xian/creepy/vocal_fx", ".ogg", excludePathPrefixAndExtension.getInstance()),
        pureExclusion_s(0, 0, "sound/xian/creepy/vocal_fx", ".wav", excludePathPrefixAndExtension.getInstance()),
        pureExclusion_s(0, 0, "sound/feedback", ".ogg", excludePathPrefixAndExtension.getInstance()),
        pureExclusion_s(0, 0, "sound/feedback", ".wav", excludePathPrefixAndExtension.getInstance()),
        pureExclusion_s(0, 0, "guis/assets/mainmenu/chnote.tga", null, excludeFullName.getInstance()),
        pureExclusion_s(0, 0, "sound/levels/alphalabs2/uac_better_place.ogg", null, excludeFullName.getInstance()),
        pureExclusion_s(0, 0, "textures/bigchars.tga", null, excludeFullName.getInstance()),
        pureExclusion_s(0, 0, "dds/textures/bigchars.dds", null, excludeFullName.getInstance()),
        pureExclusion_s(0, 0, "fonts", ".tga", excludePathPrefixAndExtension.getInstance()),
        pureExclusion_s(0, 0, "dds/fonts", ".dds", excludePathPrefixAndExtension.getInstance()),
        pureExclusion_s(0, 0, "default.cfg", null, excludeFullName.getInstance()),  // russian zpak001.pk4
        pureExclusion_s(0, 0, "fonts", ".dat", excludePathPrefixAndExtension.getInstance()),
        pureExclusion_s(0, 0, "guis/temp.guied", null, excludeFullName.getInstance()),
        pureExclusion_s(0, 0, null, null, null)
    )
    val pureExclusions2: Array<pureExclusion_s> = arrayOf(
        pureExclusion_s(0, 0, null, "/", excludeExtension.getInstance()),
        pureExclusion_s(0, 0, null, "\\", excludeExtension.getInstance()),
        pureExclusion_s(0, 0, null, ".pda", excludeExtension.getInstance()),
        pureExclusion_s(0, 0, null, ".gui", excludeExtension.getInstance()),
        pureExclusion_s(0, 0, null, ".pd", excludeExtension.getInstance()),
        pureExclusion_s(0, 0, null, ".lang", excludeExtension.getInstance()),
        pureExclusion_s(0, 0, "sound/VO", ".ogg", excludePathPrefixAndExtension.getInstance()),
        pureExclusion_s(0, 0, "sound/VO", ".wav", excludePathPrefixAndExtension.getInstance()),
        pureExclusion_s(0, 0, null, null, null)
    )
    val pureExclusions: Array<pureExclusion_s> = if (DOOM3_PURE_SPECIAL_CASES) pureExclusions1 else pureExclusions2
    var initExclusions: idInitExclusions? = null
    private var fileSystemLocal: idFileSystemLocal = idFileSystemLocal()


    var fileSystem: idFileSystem = fileSystemLocal //TODO:make a [] pointer of this?? NO BOI
    fun setFileSystems(fileSystem: idFileSystem) {
        fileSystemLocal = fileSystem as idFileSystemLocal
        FileSystem_h.fileSystem = fileSystemLocal
    }

    enum class binaryStatus_t {
        BINARY_UNKNOWN,
        BINARY_YES,
        BINARY_NO
    }

    enum class dlMime_t {
        FILE_EXEC,
        FILE_OPEN
    }

    enum class dlStatus_t {
        DL_WAIT,  // waiting in the list for beginning of the download
        DL_INPROGRESS,  // in progress
        DL_DONE,  // download completed, success
        DL_ABORTING,  // this one can be set during a download, it will force the next progress callback to abort - then will go to DL_FAILED
        DL_FAILED
    }

    enum class dlType_t {
        DLTYPE_URL,
        DLTYPE_FILE
    }

    enum class findFile_t {
        FIND_NO,
        FIND_YES,
        FIND_ADDON
    }

    // modes for OpenFileByMode. used as bit mask internally
    enum class fsMode_t {
        FS_READ,
        FS_WRITE,
        FS_APPEND
    }

    enum class fsPureReply_t {
        PURE_OK,  // we are good to connect as-is
        PURE_RESTART,  // restart required
        PURE_MISSING,  // pak files missing on the client
        PURE_NODLL // no DLL could be extracted
    }

    enum class pureStatus_t {
        PURE_UNKNOWN,  // need to run the pak through GetPackStatus
        PURE_NEUTRAL,  // neutral regarding pureness. gets in the pure list if referenced
        PURE_ALWAYS,  // always referenced - for pak* named files, unless NEVER
        PURE_NEVER // VO paks. may be referenced, won't be in the pure lists
    }

    class urlDownload_s {
        var dlerror //[ MAX_STRING_CHARS ];
                : String = ""
        var dlnow = 0
        var dlstatus = 0
        var dltotal = 0
        var status: dlStatus_t = dlStatus_t.DL_WAIT
        var url: idStr = idStr()

        constructor()

        companion object {
            @Transient
            val SIZE: Int = (idStr.SIZE
                    + 8 * MAX_STRING_CHARS
                    + Integer.SIZE
                    + Integer.SIZE
                    + Integer.SIZE
                    + TempDump.CPP_class.Enum.SIZE)
        }
    }

    class fileDownload_s {

        var buffer: ByteBuffer? = null


        var length = 0


        var position = 0

        constructor()

        companion object {
            @Transient
            val SIZE = (Integer.SIZE
                    + Integer.SIZE
                    + TempDump.CPP_class.Pointer.SIZE) //void * buffer
        }
    }

    class backgroundDownload_s {
        @Volatile

        var completed = false


        var f: idFile? = null


        var file: fileDownload_s = fileDownload_s()


        var next // set by the fileSystem
                : backgroundDownload_s? = null


        var opcode: dlType_t = dlType_t.DLTYPE_URL


        var url: urlDownload_s = urlDownload_s()

        constructor()

        companion object {
            @Transient

            val SIZE = (TempDump.CPP_class.Pointer.SIZE //backgroundDownload_s next
                    + TempDump.CPP_class.Enum.SIZE
                    + TempDump.CPP_class.Pointer.SIZE //idFile f
                    + fileDownload_s.SIZE
                    + urlDownload_s.SIZE
                    + TempDump.CPP_class.Bool.SIZE) //TODO:volatile?
        }
    }

    // file list for directory listings
    class idFileList {
        //	friend class idFileSystemLocal;
        val basePath: idStr = idStr()
        val list: idStrList = idStrList()

        fun GetBasePath(): String {
            return basePath.toString()
        }

        fun GetNumFiles(): Int {
            return list.size()
        }

        fun GetFile(index: Int): String {
            return list[index].toString()
        }

        fun GetList(): idStrList {
            return list
        }
    }

    // mod list
    class idModList {
        val descriptions: idStrList = idStrList()
        val mods: idStrList = idStrList()

        fun GetNumMods(): Int {
            return mods.size()
        }

        fun GetMod(index: Int): String {
            return mods[index].toString()
        }

        fun GetDescription(index: Int): String {
            return descriptions[index].toString()
        }

    }

    abstract class idFileSystem {
        //	public abstract					~idFileSystem() {}
        // Initializes the file system.
        abstract fun Init()

        // Restarts the file system.
        abstract fun Restart()

        // Shutdown the file system.
        abstract fun Shutdown(reloading: Boolean)

        // Returns true if the file system is initialized.
        abstract fun IsInitialized(): Boolean

        // Returns true if we are doing an fs_copyfiles.
        abstract fun PerformingCopyFiles(): Boolean

        // Returns a list of mods found along with descriptions
        // 'mods' contains the directory names to be passed to fs_game
        // 'descriptions' contains a free form string to be used in the UI
        abstract fun ListMods(): idModList

        // Frees the given mod list
        abstract fun FreeModList(modList: idModList)

        // Lists files with the given extension in the given directory.
        // Directory should not have either a leading or trailing '/'
        // The returned files will not include any directories or '/' unless fullRelativePath is set.
        // The extension must include a leading dot and may not contain wildcards.
        // If extension is "/", only subdirectories will be returned.
        abstract fun ListFiles(relativePath: String, extension: String): idFileList
        abstract fun ListFiles(relativePath: String, extension: String, sort: Boolean): idFileList
        abstract fun ListFiles(
            relativePath: String,
            extension: String,
            sort: Boolean,
            fullRelativePath: Boolean
        ): idFileList

        abstract fun ListFiles(
            relativePath: String,
            extension: String,
            sort: Boolean,
            fullRelativePath: Boolean,
            gamedir: String?
        ): idFileList

        // Lists files in the given directory and all subdirectories with the given extension.
        // Directory should not have either a leading or trailing '/'
        // The returned files include a full relative path.
        // The extension must include a leading dot and may not contain wildcards.
        abstract fun ListFilesTree(relativePath: String, extension: String): idFileList
        abstract fun ListFilesTree(relativePath: String, extension: String, sort: Boolean): idFileList
        abstract fun ListFilesTree(
            relativePath: String,
            extension: String,
            sort: Boolean,
            gamedir: String?
        ): idFileList

        // Frees the given file list.
        abstract fun FreeFileList(fileList: idFileList)

        // Converts a relative path to a full OS path.
        abstract fun OSPathToRelativePath(OSPath: String): String

        // Converts a full OS path to a relative path.
        abstract fun RelativePathToOSPath(relativePath: String, basePath: String /* = "fs_devpath"*/): String
        fun RelativePathToOSPath(relativePath: String): String { //TODO:return Path
            return RelativePathToOSPath(relativePath, "fs_devpath")
        }

        fun RelativePathToOSPath(relativePath: idStr): String {
            return RelativePathToOSPath(relativePath.toString())
        }

        // Builds a full OS path from the given components.
        abstract fun BuildOSPath(base: String, game: String, relativePath: String): String

        // Creates the given OS path for as far as it doesn't exist already.
        abstract fun CreateOSPath(OSPath: String)
        fun CreateOSPath(OSPath: idStr) {
            CreateOSPath(OSPath.toString())
        }

        // Returns true if a file is in a pak file.
        abstract fun FileIsInPAK(relativePath: String): Boolean

        // Returns a space separated string containing the checksums of all referenced pak files.
        // will call SetPureServerChecksums internally to restrict itself
        abstract fun UpdatePureServerChecksums()

        // setup the mapping of OS -> game pak checksum
        abstract fun UpdateGamePakChecksums(): Boolean

        // 0-terminated list of pak checksums
        // if pureChecksums[ 0 ] == 0, all data sources will be allowed
        // otherwise, only pak files that match one of the checksums will be checked for files
        // with the sole exception of .cfg files.
        // the function tries to configure pure mode from the paks already referenced and this new list
        // it returns wether the switch was successfull, and sets the missing checksums
        // the process is verbosive when fs_debug 1
        abstract fun SetPureServerChecksums(
            pureChecksums: IntArray,
            gamePakChecksum: Int,
            missingChecksums: IntArray,
            missingGamePakChecksum: IntArray
        ): fsPureReply_t

        // fills a 0-terminated list of pak checksums for a client
        // if OS is -1, give the current game pak checksum. if >= 0, lookup the game pak table (server only)
        abstract fun GetPureServerChecksums(checksums: IntArray, OS: Int, gamePakChecksum: CInt?)

        // before doing a restart, force the pure list and the search order
        // if the given checksum list can't be completely processed and set, will error out
        abstract fun SetRestartChecksums(pureChecksums: IntArray, gamePakChecksum: Int)

        // equivalent to calling SetPureServerChecksums with an empty list
        abstract fun ClearPureChecksums()

        // get a mask of supported OSes. if not pure, returns -1
        abstract fun GetOSMask(): Int

        // Reads a complete file.
        // Returns the length of the file, or -1 on failure.
        // A null buffer will just return the file length without loading.
        // A null timestamp will be ignored.
        // As a quick check for existance. -1 length == not present.
        // A 0 byte will always be appended at the end, so string ops are safe.
        // The buffer should be considered read-only, because it may be cached for other uses.
        abstract fun ReadFile(relativePath: String, buffer: Array<ByteBuffer?>?, timestamp: LongArray?): Int
        abstract fun ReadFile(relativePath: String, buffer: Array<ByteBuffer?>?): Int
        fun ReadFile(name: idStr, buffer: Array<ByteBuffer?>?, timeStamp: LongArray?): Int {
            return ReadFile(name.toString(), buffer, timeStamp)
        }

        fun ReadFile(name: idStr, buffer: Array<ByteBuffer?>?): Int {
            return ReadFile(name.toString(), buffer)
        }

        // Frees the memory allocated by ReadFile.
        @Deprecated("")
        abstract fun FreeFile(buffer: Array<ByteBuffer?>)

        // Writes a complete file, will create any needed subdirectories.
        // Returns the length of the file, or -1 on failure.
        abstract fun WriteFile(
            relativePath: String,
            buffer: ByteBuffer,
            size: Int /*, final String basePath = "fs_savepath" */
        ): Int

        abstract fun WriteFile(
            relativePath: String,
            buffer: ByteBuffer,
            size: Int,
            basePath: String /* = "fs_savepath" */
        ): Int

        // Removes the given file.
        abstract fun RemoveFile(relativePath: String)
        abstract fun OpenFileRead(relativePath: String): idFile?
        abstract fun OpenFileRead(relativePath: String, allowCopyFiles: Boolean): idFile?

        // Opens a file for reading.
        abstract fun OpenFileRead(relativePath: String, allowCopyFiles: Boolean, gamedir: String?): idFile?
        fun OpenFileWrite(relativePath: String): idFile? {
            return OpenFileWrite(relativePath, "fs_savepath")
        }

        // Opens a file for writing, will create any needed subdirectories.
        abstract fun OpenFileWrite(relativePath: String, basePath: String): idFile?

        // Opens a file for writing at the end.
        abstract fun OpenFileAppend(filename: String, sync: Boolean, basePath: String /* = "fs_basepath"*/): idFile?
        abstract fun OpenFileAppend(filename: String, sync: Boolean /* = "fs_basepath"*/): idFile?
        abstract fun OpenFileAppend(filename: String /*, boolean sync*/): idFile?

        // Opens a file for reading, writing, or appending depending on the value of mode.
        abstract fun OpenFileByMode(relativePath: String, mode: fsMode_t): idFile?

        // Opens a file for reading from a full OS path.
        abstract fun OpenExplicitFileRead(OSPath: String): idFile?

        // Opens a file for writing to a full OS path.
        abstract fun OpenExplicitFileWrite(OSPath: String): idFile?

        // Closes a file.
        abstract fun CloseFile(f: idFile)

        // Returns immediately, performing the read from a background thread.
        abstract fun BackgroundDownload(bgl: backgroundDownload_s)

        // resets the bytes read counter
        abstract fun ResetReadCount()

        // retrieves the current read count
        abstract fun GetReadCount(): Int

        // adds to the read count
        abstract fun AddToReadCount(c: Int)

        // look for a dynamic module
        abstract fun FindDLL(basename: String, dllPath: CharArray, updateChecksum: Boolean)

        // case sensitive filesystems use an internal directory cache
        // the cache is cleared when calling OpenFileWrite and RemoveFile
        // in some cases you may need to use this directly
        abstract fun ClearDirCache()

        // is D3XP installed? even if not running it atm
        abstract fun HasD3XP(): Boolean

        // are we using D3XP content ( through a real d3xp run or through a double mod )
        abstract fun RunningD3XP(): Boolean

        // don't use for large copies - allocates a single memory block for the copy
        abstract fun CopyFile(fromOSPath: String, toOSPath: String)

        // lookup a relative path, return the size or 0 if not found
        abstract fun ValidateDownloadPakForChecksum(checksum: Int, path: CharArray, isGamePak: Boolean): Int
        abstract fun MakeTemporaryFile(): idFile

        // make downloaded pak files known so pure negociation works next time
        abstract fun AddZipFile(path: String): Int

        // look for a file in the loaded paks or the addon paks
        // if the file is found in addons, FS's internal structures are ready for a reloadEngine
        abstract fun FindFile(path: String, scheduleAddons: Boolean): findFile_t

        // get map/addon decls and take into account addon paks that are not on the search list
        // the decl 'name' is in the "path" entry of the dict
        abstract fun GetNumMaps(): Int
        abstract fun GetMapDecl(i: Int): idDict?
        abstract fun FindMapScreenshot(path: String, buf: StringBuffer, len: Int)

        // ignore case and seperator char distinctions
        abstract fun FilenameCompare(s1: String, s2: String): Boolean
        fun FilenameCompare(s1: idStr, s2: idStr): Boolean {
            return FilenameCompare(s1.toString(), s2.toString())
        }
    }

    abstract class pureExclusionFunc_t {
        abstract fun run(excl: pureExclusion_s, l: Int, name: idStr): Boolean
    }

    class pureExclusion_s(
        var nameLen: Int,
        var extLen: Int,
        var name: String?,
        var ext: String?,
        var func: pureExclusionFunc_t?
    )

    internal class excludeExtension : pureExclusionFunc_t() {
        override fun run(excl: pureExclusion_s, l: Int, name: idStr): Boolean {
            return l > excl.extLen && 0 == idStr.Icmp(name.toString().substring(l - excl.extLen), excl.ext!!)
        }

        companion object {
            private val instance: pureExclusionFunc_t = excludeExtension()
            fun getInstance(): pureExclusionFunc_t {
                return instance
            }
        }
    }

    internal class excludePathPrefixAndExtension : pureExclusionFunc_t() {
        override fun run(excl: pureExclusion_s, l: Int, name: idStr): Boolean {
            // FIX: C++ checks `l > excl.nameLen`, not `l > excl.extLen`
            return l > excl.nameLen && 0 == idStr.Icmp(
                name.toString().substring(l - excl.extLen),
                excl.ext!!
            ) && 0 == name.IcmpPrefixPath(excl.name!!)
        }

        companion object {
            private val instance: pureExclusionFunc_t = excludePathPrefixAndExtension()
            fun getInstance(): pureExclusionFunc_t {
                return instance
            }
        }
    }

    internal class excludeFullName : pureExclusionFunc_t() {
        override fun run(excl: pureExclusion_s, l: Int, name: idStr): Boolean {
            return l == excl.nameLen && 0 == name.Icmp(excl.name!!)
        }

        companion object {
            private val instance: pureExclusionFunc_t = excludeFullName()
            fun getInstance(): pureExclusionFunc_t {
                return instance
            }
        }
    }

    class fileInPack_s {
        var entry //
                : ZipEntry? = null
        var name // name of the file
                : idStr = idStr()
        var next // next file in the hash
                : fileInPack_s? = null
        var pos // file info position in zip
                = 0
    }

    class addonInfo_t {
        val depends: idList<Int> = idList()
        val mapDecls: idList<idDict> = idList()
    }

    class pack_t {
        var addon // this is an addon pack - addon_search tells if it's 'active'
                = false
        var addon_info: addonInfo_t? = null
        var addon_search // is in the search list
                = false
        var binary: binaryStatus_t = binaryStatus_t.BINARY_UNKNOWN
        var buildBuffer: Array<fileInPack_s> = Array(0) { fileInPack_s() }
        var checksum = 0
        var   /*unzFile*/handle: ZipFile? = null
        var hashTable: Array<fileInPack_s?> = arrayOfNulls<fileInPack_s?>(FILE_HASH_SIZE)
        var isNew // for downloaded paks
                = false
        var length = 0
        var numfiles = 0
        var pakFilename // c:\doom\base\pak0.pk4
                : idStr = idStr()
        var pureStatus: pureStatus_t = pureStatus_t.PURE_UNKNOWN
        var referenced = false
    }

    internal class directory_t {
        val gamedir // base
                : idStr = idStr()
        val path // c:\doom
                : idStr = idStr()
    }

    internal class searchpath_s {
        var dir: directory_t? = null
        var next: searchpath_s? = null
        var pack // only one of pack / dir will be non NULL
                : pack_t? = null
    }

    internal class idDEntry : idStrList() {
        private val directory: idStr = idStr()
        private val extension: idStr = idStr()

        //public	virtual				~idDEntry() {}
        fun Matches(directory: String, extension: String): Boolean {
            return (0 == this.directory.Icmp(directory)
                    && 0 == this.extension.Icmp(extension))
        }

        fun Init(directory: String, extension: String, list: idStrList) {
            this.directory.set(directory)
            this.extension.set(extension)
            super.set(list)
        }

        override fun clear() {
            directory.Clear()
            extension.Clear()
            super.clear()
        }
    }

    class idFileSystemLocal : idFileSystem() {
        //
        private val dir_cache // fifo
                : Array<idDEntry>

        //
        private val gamePakForOS: IntArray = IntArray(MAX_GAME_OS)
        private val addonChecksums // list of checksums that should go to the search list directly ( for restarts )
                : idList<Int>

        //
        private var addonPaks // not loaded up, but we saw them
                : searchpath_s?

        //
        private var backgroundDownloads: backgroundDownload_s? = null
        private val backgroundThread: xthreadInfo = xthreadInfo()

        @Volatile
        private var backgroundDownloadExit: Boolean = false

        //
        private var d3xp // 0: didn't check, -1: not installed, 1: installed
                : Int
        private val defaultBackgroundDownload: backgroundDownload_s? = null
        private var dir_cache_count: Int
        private var dir_cache_index: Int
        private var gameDLLChecksum // the checksum of the last loaded game DLL
                = 0
        private val gameFolder // this will be a single name without separators
                : idStr
        private var gamePakChecksum // the checksum of the pak holding the loaded game DLL
                = 0
        private var loadCount // total files read
                = 0
        private var loadStack // total files in memory
                = 0
        private var loadedFileFromDir // set to true once a file was loaded from a directory - can't switch to pure anymore
                : Boolean
        private var mapDict // for GetMapDecl
                : idDict
        private var readCount // total bytes read
                = 0
        private val restartChecksums // used during a restart to set things in right order
                : idList<Int>
        private var restartGamePakChecksum: Int
        private var searchPaths: searchpath_s? = null
        private val serverPaks: idList<pack_t>

        /*
         ================
         idFileSystemLocal::Init

         Called only at inital startup, not when the filesystem
         is resetting due to a game change
         ================
         */
        override fun Init() {
            // allow command line parms to override our defaults
            // we have to specially handle this, because normal command
            // line variable sets don't happen until after the filesystem
            // has already been initialized
            idLib.common.StartupVariable("fs_basepath", false)
            idLib.common.StartupVariable("fs_savepath", false)
            idLib.common.StartupVariable("fs_cdpath", false)
            idLib.common.StartupVariable("fs_devpath", false)
            idLib.common.StartupVariable("fs_game", false)
            idLib.common.StartupVariable("fs_game_base", false)
            idLib.common.StartupVariable("fs_copyfiles", false)
            idLib.common.StartupVariable("fs_restrict", false)
            idLib.common.StartupVariable("fs_searchAddons", false)
            if (!ID_ALLOW_D3XP) {
                if (fs_game.GetString() != null && 0 == idStr.Icmp(fs_game.GetString()!!, "d3xp")) {
                    fs_game.SetString(null)
                }
                if (fs_game_base.GetString() != null && 0 == idStr.Icmp(fs_game_base.GetString()!!, "d3xp")) {
                    fs_game_base.SetString(null)
                }
            }
            if (fs_basepath.GetString()!!.isEmpty()) {
                fs_basepath.SetString(win_main.Sys_DefaultBasePath())
            }
            if (fs_savepath.GetString()!!.isEmpty()) {
                fs_savepath.SetString(win_main.Sys_DefaultSavePath())
            }
            if (fs_cdpath.GetString()!!.isEmpty()) {
                fs_cdpath.SetString(win_main.Sys_DefaultCDPath())
            }
            if (fs_devpath.GetString()!!.isEmpty()) {
                if (WIN32) {
                    fs_devpath.SetString(
                        if (fs_cdpath.GetString()!!.isNotEmpty()) fs_cdpath.GetString() else fs_basepath.GetString()
                    )
                } else {
                    fs_devpath.SetString(fs_savepath.GetString())
                }
            }

            // try to start up normally
            Startup()

            // see if we are going to allow add-ons
            SetRestrictions()

            // spawn a thread to handle background file reads
            StartBackgroundDownloadThread()

            // if we can't find default.cfg, assume that the paths are
            // busted and error out now, rather than getting an unreadable
            // graphics screen when the font fails to load
            // Dedicated servers can run with no outside files at all
            if (ReadFile("default.cfg", null, null) <= 0) {
                idLib.common.FatalError("Couldn't load default.cfg")
            }
        }

        fun StartBackgroundDownloadThread() {
            if (backgroundThread.threadHandle != null) {
                idLib.common.Printf("background thread already running\n")
                return
            }
            backgroundDownloadExit = false
            win_main.Sys_CreateThread(
                Companion::BackgroundDownloadThreadFn,
                null,
                backgroundThread,
                "backgroundDownload"
            )
            if (backgroundThread.threadHandle == null) {
                idLib.common.Warning("idFileSystemLocal::StartBackgroundDownloadThread: failed")
            }
        }

        override fun Restart() {
            // free anything we currently have loaded
            Shutdown(true)
            Startup()

            // see if we are going to allow add-ons
            SetRestrictions()

            // if we can't find default.cfg, assume that the paths are
            // busted and error out now, rather than getting an unreadable
            // graphics screen when the font fails to load
            if (ReadFile("default.cfg", null, null) <= 0) {
                idLib.common.FatalError("Couldn't load default.cfg")
            }
        }

        /*
         ================
         idFileSystemLocal::Shutdown

         Frees all resources and closes all files
         ================
         */
        override fun Shutdown(reloading: Boolean) {
            // stop the background download thread
            if (backgroundThread.threadHandle != null) {
                backgroundDownloadExit = true
                Sys_TriggerEvent() // wake the thread so it can exit
                win_main.Sys_DestroyThread(backgroundThread)
            }

            var sp: searchpath_s?
            var next: searchpath_s?
            var loop: searchpath_s?
            gameFolder.Clear()
            serverPaks.Clear()
            if (!reloading) {
                restartChecksums.Clear()
                addonChecksums.Clear()
            }
            loadedFileFromDir = false
            gameDLLChecksum = 0
            gamePakChecksum = 0
            ClearDirCache()

            // free everything - loop through searchPaths and addonPaks
            loop = searchPaths
            while (loop != null) {
                sp = loop
                while (sp != null) {
                    next = sp.next
                    if (sp.pack != null) {
                        try {
                            //                        unzClose(sp.pack.handle);
                            sp.pack!!.handle!!.close()
                        } catch (ex: IOException) {
                            Logger.getLogger(FileSystem_h::class.java.name).log(Level.SEVERE, null, ex)
                        }
                        //				delete [] sp.pack.buildBuffer;
                        if (sp.pack!!.addon_info != null) {
                            sp.pack!!.addon_info!!.mapDecls.DeleteContents(true)
                            //					delete sp.pack.addon_info;
                            sp.pack!!.addon_info = null
                        }
                        //				delete sp.pack;
                        sp.pack = null
                    }
                    if (sp.dir != null) {
//				delete sp.dir;
                        sp.dir = null
                    }
                    sp = next
                }
                loop = if (loop == searchPaths) addonPaks else null
            }

            // any FS_ calls will now be an error until reinitialized
            searchPaths = null
            addonPaks = null
            CmdSystem.cmdSystem.RemoveCommand("path")
            CmdSystem.cmdSystem.RemoveCommand("dir")
            CmdSystem.cmdSystem.RemoveCommand("dirtree")
            CmdSystem.cmdSystem.RemoveCommand("touchFile")
            mapDict.Clear()
        }

        override fun IsInitialized(): Boolean {
            return searchPaths != null
        }

        override fun PerformingCopyFiles(): Boolean {
            return fs_copyfiles.GetInteger() > 0
        }

        override fun ListMods(): idModList {
            var i: Int
            val desc = ByteBuffer.allocate(MAX_DESCRIPTION)
            val dirs = idStrList()
            val pk4s = idStrList()
            val list = idModList()
            val search = arrayOf(
                fs_savepath.GetString()!!,
                fs_devpath.GetString()!!,
                fs_basepath.GetString()!!,
                fs_cdpath.GetString()!!
            )
            var isearch: Int
            isearch = 0
            while (isearch < 4) {
                dirs.clear()
                pk4s.clear()
                // scan for directories
                ListOSFiles(search[isearch], "/", dirs)
                dirs.remove(idStr("."))
                dirs.remove(idStr(".."))
                dirs.remove(idStr("base"))
                dirs.remove(idStr("pb"))

                // see if there are any pk4 files in each directory
                i = 0
                while (i < dirs.size()) {
                    val gamepath = idStr(BuildOSPath(search[isearch], dirs[i].toString(), ""))
                    ListOSFiles(gamepath.toString(), ".pk4", pk4s)
                    if (pk4s.size() != 0) {
                        // FIX: C++ `if (!list->mods.Find(dirs[i]))` checks for NOT found (NULL pointer = falsy).
                        // Kotlin Find() returns null when not found, not 0. Was `0 == Find()` which is always
                        // false when null, preventing any mod from being added to the list.
                        if (null == list.mods.Find(dirs[i])) {
                            // DG: ignore d3xp, it's added explicitly later, if available
                            if (dirs[i].Icmp("d3xp") != 0) {
                                list.mods.add(dirs[i])
                            }
                        }
                    }
                    i++
                }
                isearch++
            }
            list.mods.sort()

            // read the descriptions for each mod - search all paths
            i = 0
            while (i < list.mods.size()) {
                isearch = 0
                while (isearch < 4) {
                    val descfile = idStr(BuildOSPath(search[isearch], list.mods[i].toString(), "description.txt"))
                    val f = OpenOSFile(descfile.toString(), "r")
                    if (f != null) {
                        try {
                            // FIX: clear ByteBuffer before each read — C++ uses stack-local char[256]
                            // that gets overwritten by fgets each iteration
                            desc.clear()
                            val bytesRead = f.read(desc)
                            if (bytesRead > 0) {
                                // FIX: use only bytes actually read, not entire 256-byte array.
                                // Also mimic C++ fgets: only take first line, trim newlines/nulls.
                                val descStr = String(desc.array(), 0, bytesRead)
                                    .substringBefore('\n').trimEnd('\r', '\u0000')
                                list.descriptions.add(idStr(descStr))
                                f.close()
                                break
                            } else {
                                idLib.common.DWarning("Error reading %s", descfile.toString())
                                f.close()
                                isearch++
                                continue
                            }
                        } catch (ex: IOException) {
                            Logger.getLogger(FileSystem_h::class.java.name).log(Level.SEVERE, null, ex)
                        }
                    }
                    isearch++
                }
                if (isearch == 4) {
                    list.descriptions.add(list.mods[i])
                }
                i++
            }

            list.mods.insert(idStr(""))
            list.descriptions.insert(idStr("Doom 3 (base game)"))

            // DG: if installed, add d3xp with useful description, right below the base game
            if (HasD3XP()) {
                list.mods.insert(idStr("d3xp"), 1)
                list.descriptions.insert(idStr("Resurrection Of Evil (d3xp)"), 1)
            }

            assert(list.mods.size() == list.descriptions.size())
            return list
        }

        override fun FreeModList(modList: idModList) {
//	delete modList;
        }

        override fun ListFiles(relativePath: String, extension: String): idFileList {
            return ListFiles(relativePath, extension, false)
        }

        override fun ListFiles(relativePath: String, extension: String, sort: Boolean): idFileList {
            return ListFiles(relativePath, extension, sort, false)
        }

        override fun ListFiles(
            relativePath: String,
            extension: String,
            sort: Boolean,
            fullRelativePath: Boolean
        ): idFileList {
            return ListFiles(relativePath, extension, sort, fullRelativePath, null)
        }

        override fun ListFiles(
            relativePath: String,
            extension: String,
            sort: Boolean,
            fullRelativePath: Boolean,
            gamedir: String?
        ): idFileList {
            val hashIndex = idHashIndex(4096, 4096)
            val extensionList = idStrList()
            val fileList = idFileList()
            fileList.basePath.set(relativePath)
            GetExtensionList(extension, extensionList)
            GetFileList(relativePath, extensionList, fileList.list, hashIndex, fullRelativePath, gamedir)
            if (sort) {
                idStrList.idStrListSortPaths(fileList.list)
            }
            return fileList
        }

        override fun ListFilesTree(relativePath: String, extension: String): idFileList {
            return ListFilesTree(relativePath, extension, false)
        }

        override fun ListFilesTree(relativePath: String, extension: String, sort: Boolean): idFileList {
            return ListFilesTree(relativePath, extension, sort, null)
        }

        override fun ListFilesTree(
            relativePath: String,
            extension: String,
            sort: Boolean,
            gamedir: String?
        ): idFileList {
            val hashIndex = idHashIndex(4096, 4096)
            val extensionList = idStrList()
            val fileList = idFileList()
            fileList.basePath.set(relativePath)
            fileList.list.SetGranularity(4096)
            GetExtensionList(extension, extensionList)
            GetFileListTree(relativePath, extensionList, fileList.list, hashIndex, gamedir)
            if (sort) {
                idStrList.idStrListSortPaths(fileList.list)
            }
            return fileList
        }

        override fun FreeFileList(fileList: idFileList) {
//            delete fileList;
        }

        /*
         ================
         idFileSystemLocal::OSPathToRelativePath

         takes a full OS path, as might be found in data from a media creation
         program, and converts it to a relativePath by stripping off directories

         Returns false if the osPath tree doesn't match any of the existing
         search paths.

         ================
         */
        override fun OSPathToRelativePath(OSPath: String): String {
            val relativePath: String //=new char[MAX_STRING_CHARS];
            var s: Int
            var base: Int

            // skip a drive letter?
            // search for anything with "base" in it
            // Ase files from max may have the form of:
            // "//Purgatory/purgatory/doom/base/models/mapobjects/bitch/hologirl.tga"
            // which won't match any of our drive letter based search paths

            // DG: dhewm3 removed ID_DEMO_BUILD branch and ignoreWarning
            // look for the first complete directory name
            base = OSPath.indexOf(Licensee.BASE_GAMEDIR)
            while (base != -1) {
                var c1: Char = '\u0000'
                var c2: Char
                if (base > 0) {
                    c1 = OSPath[base - 1]
                }
                c2 = OSPath[base + Licensee.BASE_GAMEDIR.length]
                if ((c1 == '/' || c1 == '\\') && (c2 == '/' || c2 == '\\')) {
                    break
                }
                base = OSPath.indexOf(Licensee.BASE_GAMEDIR, base + 1)
            }
            // fs_game and fs_game_base support - look for first complete name with a mod path
            // ( fs_game searched before fs_game_base )
            var fsgame: String = ""
            var iGame: Int
            iGame = 0
            while (iGame < 2) {
                if (iGame == 0) {
                    fsgame = fs_game.GetString()!!
                } else if (iGame == 1) {
                    fsgame = fs_game_base.GetString()!!
                }
                if (-1 == base && fsgame.isNotEmpty()) {
                    base = OSPath.indexOf(fsgame)
                    while (base != -1) {
                        var c1: Char = '\u0000'
                        var c2: Char
                        if (base > 0) {
                            c1 = OSPath[base - 1]
                        }
                        c2 = OSPath[base + fsgame.length]
                        if ((c1 == '/' || c1 == '\\') && (c2 == '/' || c2 == '\\')) {
                            break
                        }
                        base = OSPath.indexOf(fsgame, base + 1)
                    }
                }
                iGame++
            }
            if (base > 0) {
                // DG: dhewm3 added .pk4/ path handling for paths inside pk4 files
                s = OSPath.indexOf(".pk4/", base)
                if (s != -1) {
                    s += 4 // skip ".pk4", but not the following '/', that'll be skipped below
                } else {
                    s = OSPath.indexOf('/', base)
                    if (s < 0) {
                        s = OSPath.indexOf('\\', base)
                    }
                }
                if (s != -1) {
//                    strcpy(relativePath, s + 1);
                    relativePath = OSPath.substring(s + 1)
                    if (fs_debug.GetInteger() > 1) {
                        idLib.common.Printf("idFileSystem::OSPathToRelativePath: %s becomes %s\n", OSPath, relativePath)
                    }
                    return relativePath
                }
            }
            // DG: dhewm3 removed ignoreWarning
            idLib.common.Warning("idFileSystem::OSPathToRelativePath failed on %s", OSPath)
            //            strcpy(relativePath, "");
            return ""
        }

        /*
         =====================
         idFileSystemLocal::RelativePathToOSPath

         Returns a fully qualified path that can be used with stdio libraries
         =====================
         */
        override fun RelativePathToOSPath(relativePath: String, basePath: String): String {
            var path = CVarSystem.cvarSystem.GetCVarString(basePath)
            if (path.isEmpty()) {
                path = fs_savepath.GetString()!!
            }
            return BuildOSPath(path, gameFolder.toString(), relativePath)
        }

        override fun BuildOSPath(base: String, game: String, relativePath: String): String {
            val OSPath = StringBuilder(MAX_STRING_CHARS)
            val newPath: idStr
            if (fs_caseSensitiveOS.GetBool() || Common.com_developer.GetBool()) {
                // extract the path, make sure it's all lowercase
                val testPath: idStr
                val fileName: idStr

                testPath = idStr(String.format("%s/%s", game, relativePath))
                testPath.StripFilename()
                if (testPath.HasUpper()) {
                    idLib.common.DPrintf("Non-portable: path contains uppercase characters: %s\n", testPath)

                    // attempt a fixup on the fly
                    if (fs_caseSensitiveOS.GetBool()) {
                        testPath.ToLower()
                        fileName = idStr(relativePath)
                        fileName.StripPath()
                        //				sprintf( newPath, "%s/%s/%s", base, testPath.c_str(), fileName.c_str() );
                        newPath = idStr(String.format("%s/%s/%s", base, testPath, fileName))
                        ReplaceSeparators(newPath)
                        idLib.common.DPrintf("Fixed up to %s\n", newPath)
                        idStr.Copynz(OSPath, newPath.toString())
                        return OSPath.toString()
                    }
                }
            }
            val strBase = idStr(base)
            strBase.StripTrailing('/')
            strBase.StripTrailing('\\')
            //	sprintf( newPath, "%s/%s/%s", strBase.c_str(), game, relativePath );
            newPath = idStr(String.format("%s/%s/%s", strBase, game, relativePath))
            ReplaceSeparators(newPath)
            idStr.Copynz(OSPath, newPath.toString())
            return OSPath.toString()
        }

        /*
         ============
         idFileSystemLocal::CreateOSPath

         Creates any directories needed to store the given filename
         ============
         */
        override fun CreateOSPath(OSPath: String) {
            var ofs: Int

            // make absolutely sure that it can't back up the path
            // FIXME: what about c: ?
            if (OSPath.contains("..") || OSPath.contains("::")) {
                if (_DEBUG) {
                    idLib.common.DPrintf("refusing to create relative path \"%s\"\n", OSPath)
                }
                return
            }
            val path = idStr(OSPath)
            ofs = 1
            while (ofs < path.Length()) {
                if (path[ofs] == PATHSEPERATOR_CHAR) {
                    // create the directory
                    // FIX: C++ uses null-termination (*ofs = 0) which doesn't work in Kotlin strings.
                    // Use substring to pass only the path up to this separator.
                    win_main.Sys_Mkdir(idStr(path.toString().substring(0, ofs)))
                }
                ofs++
            }
        }

        override fun FileIsInPAK(relativePath: String): Boolean {
            var relativePath = relativePath
            var search: searchpath_s?
            var pak: pack_t?
            var pakFile: fileInPack_s?
            val hash: Long
            if (null == searchPaths) {
                idLib.common.FatalError("Filesystem call made without initialization\n")
            }
            if (null == relativePath) {
                idLib.common.FatalError("idFileSystemLocal::FileIsInPAK: NULL 'relativePath' parameter passed\n")
                return false
            }

            // qpaths are not supposed to have a leading slash
            if (relativePath[0] == '/' || relativePath[0] == '\\') {
//		relativePath++;
                relativePath = relativePath.substring(1)
            }

            // make absolutely sure that it can't back up the path.
            // The searchpaths do guarantee that something will always
            // be prepended, so we don't need to worry about "c:" or "//limbo"
            if (relativePath.contains("..") || relativePath.contains("::")) {
                return false
            }

            //
            // search through the path, one element at a time
            //
            hash = HashFileName(relativePath)
            search = searchPaths
            while (search != null) {

                // is the element a pak file?
                if (search.pack != null && search.pack!!.hashTable[hash.toInt()] != null) {

                    // disregard if it doesn't match one of the allowed pure pak files - or is a localization file
                    if (serverPaks.Num() != 0) {
                        GetPackStatus(search.pack!!)
                        if (search.pack!!.pureStatus != pureStatus_t.PURE_NEVER && null == serverPaks.Find(search.pack!!)) {
                            search = search.next
                            continue  // not on the pure server pak list
                        }
                    }

                    // look through all the pak file elements
                    pak = search.pack!!
                    pakFile = pak.hashTable[hash.toInt()]
                    do {
                        // case and separator insensitive comparisons
                        if (FilenameCompare(pakFile!!.name.toString(), relativePath)) {
                            return true
                        }
                        pakFile = pakFile.next
                    } while (pakFile != null)
                }
                search = search.next
            }
            return false
        }

        override fun UpdatePureServerChecksums() {
            var search: searchpath_s?
            var i: Int
            var status: pureStatus_t?
            serverPaks.Clear()
            search = searchPaths
            while (search != null) {

                // is the element a referenced pak file?
                if (null == search.pack) {
                    search = search.next
                    continue
                }
                status = GetPackStatus(search.pack!!)
                if (status == pureStatus_t.PURE_NEVER) {
                    search = search.next
                    continue
                }
                if (status == pureStatus_t.PURE_NEUTRAL && !search.pack!!.referenced) {
                    search = search.next
                    continue
                }
                serverPaks.Append(search.pack!!)
                if (serverPaks.Num() >= MAX_PURE_PAKS) {
                    idLib.common.FatalError("MAX_PURE_PAKS ( %d ) exceeded\n", MAX_PURE_PAKS)
                }
                search = search.next
            }
            if (fs_debug.GetBool()) {
                var checks = ""
                i = 0
                while (i < serverPaks.Num()) {
                    checks += Str.va("%x ", serverPaks[i].checksum)
                    i++
                }
                idLib.common.Printf("set pure list - %d paks ( %s)\n", serverPaks.Num(), checks)
            }
        }

        override fun UpdateGamePakChecksums(): Boolean {
            var search: searchpath_s?
            var pakFile: fileInPack_s?
            val confHash: Int
            var confFile: idFile?
            var buf: ByteBuffer
            var lexConf: idLexer
            val token = idToken()
            var id: Int
            confHash = HashFileName(BINARY_CONFIG).toInt()

//	memset( gamePakForOS, 0, sizeof( gamePakForOS ) );
            search = searchPaths
            while (search != null) {
                if (null == search.pack) {
                    search = search.next
                    continue
                }
                search.pack!!.binary = binaryStatus_t.BINARY_NO
                pakFile = search.pack!!.hashTable[confHash]
                while (pakFile != null) {
                    if (FilenameCompare(pakFile.name.toString(), BINARY_CONFIG)) {
                        search.pack!!.binary = binaryStatus_t.BINARY_YES
                        confFile = ReadFileFromZip(search.pack!!, pakFile, BINARY_CONFIG)
                        //				buf = new char[ confFile.Length() + 1 ];
                        confFile.Read(ByteBuffer.allocate(confFile.Length()).also { buf = it }, confFile.Length())
                        //				buf[ confFile.Length() ] = '\0';
                        lexConf = idLexer(String(buf.array()), confFile.Length(), confFile.GetFullPath())
                        while (lexConf.ReadToken(token)) {
                            if (token.IsNumeric()) {
                                id = token.toString().toInt()
                                if (id < MAX_GAME_OS && 0 == gamePakForOS[id]) {
                                    if (fs_debug.GetBool()) {
                                        idLib.common.Printf(
                                            "Adding game pak checksum for OS %d: %s 0x%x\n",
                                            id,
                                            confFile.GetFullPath(),
                                            search.pack!!.checksum
                                        )
                                    }
                                    gamePakForOS[id] = search.pack!!.checksum
                                }
                            }
                        }
                        CloseFile(confFile)
                        //				delete lexConf;
//				delete[] buf;
                    }
                    pakFile = pakFile.next
                }
                search = search.next
            }

            // some sanity checks on the game code references
            // make sure that at least the local OS got a pure reference
            if (0 == gamePakForOS[BUILD_OS_ID]) {
                idLib.common.Warning("No game code pak reference found for the local OS")
                return false
            }
            if (!CVarSystem.cvarSystem.GetCVarBool("net_serverAllowServerMod")
                && gamePakChecksum != gamePakForOS[BUILD_OS_ID]
            ) {
                idLib.common.Warning("The current game code doesn't match pak files (net_serverAllowServerMod is off)")
                return false
            }
            return true
        }

        /*
         =====================
         idFileSystemLocal::SetPureServerChecksums
         set the pure paks according to what the server asks
         if that's not possible, identify why and build an answer
         can be:
         loadedFileFromDir - some files were loaded from directories instead of paks (a restart in pure pak-only is required)
         missing/wrong checksums - some pak files would need to be installed/updated (downloaded for instance)
         some pak files currently referenced are not referenced by the server
         wrong order - if the pak order doesn't match, means some stuff could have been loaded from somewhere else
         server referenced files are prepended to the list if possible ( that doesn't break pureness )
         DLL:
         the checksum of the pak containing the DLL is maintained seperately, the server can send different replies by OS
         =====================
         */
        override fun SetPureServerChecksums(
            pureChecksums: IntArray,
            _gamePakChecksum: Int,
            missingChecksums: IntArray,
            missingGamePakChecksum: IntArray
        ): fsPureReply_t {
            var pack: pack_t?
            var i: Int
            var j: Int
            var imissing: Int
            var success = true
            var canPrepend = true
            val dllName = arrayOf("")
            val dllHash: Int
            var pakFile: fileInPack_s?
            idLib.sys.DLL_GetFileName("game", dllName, MAX_OSPATH)
            dllHash = HashFileName(dllName[0]).toInt()
            imissing = 0
            missingChecksums[0] = 0
            missingGamePakChecksum[0] = 0
            if (pureChecksums[0] == 0) {
                ClearPureChecksums()
                return fsPureReply_t.PURE_OK
            }
            if (0 == serverPaks.Num()) {
                // there was no pure lockdown yet - lock to what we already have
                UpdatePureServerChecksums()
            }
            i = 0
            j = 0
            while (pureChecksums[i] != 0) {
                if (j < serverPaks.Num() && serverPaks[j].checksum == pureChecksums[i]) {
                    canPrepend = false // once you start matching into the list there is no prepending anymore
                    i++
                    j++ // the pak is matched, is in the right order, continue..
                } else {
                    pack = GetPackForChecksum(pureChecksums[i], true)
                    if (pack != null && pack.addon && !pack.addon_search) {
                        // this is an addon pack, and it's not on our current search list
                        // setting success to false meaning that a restart including this addon is required
                        if (fs_debug.GetBool()) {
                            idLib.common.Printf(
                                "pak %s checksumed 0x%x is on addon list. Restart required.\n",
                                pack.pakFilename.toString(),
                                pack.checksum
                            )
                        }
                        success = false
                    }
                    if (pack != null && pack.isNew) {
                        // that's a downloaded pack, we will need to restart
                        if (fs_debug.GetBool()) {
                            idLib.common.Printf(
                                "pak %s checksumed 0x%x is a newly downloaded file. Restart required.\n",
                                pack.pakFilename.toString(),
                                pack.checksum
                            )
                        }
                        success = false
                    }
                    if (pack != null) {
                        if (canPrepend) {
                            // we still have a chance
                            if (fs_debug.GetBool()) {
                                idLib.common.Printf(
                                    "prepend pak %s checksumed 0x%x at index %d\n",
                                    pack.pakFilename.toString(),
                                    pack.checksum,
                                    j
                                )
                            }
                            // NOTE: there is a light possibility this adds at the end of the list if UpdatePureServerChecksums didn't set anything
                            serverPaks.Insert(pack, j)
                            i++
                            j++ // continue..
                        } else {
                            success = false
                            if (fs_debug.GetBool()) {
                                // verbose the situation
                                if (serverPaks.Find(pack) != null) {
                                    idLib.common.Printf(
                                        "pak %s checksumed 0x%x is in the pure list at wrong index. Current index is %d, found at %d\n",
                                        pack.pakFilename.toString(),
                                        pack.checksum,
                                        j,
                                        serverPaks.FindIndex(pack)
                                    )
                                } else {
                                    idLib.common.Printf(
                                        "pak %s checksumed 0x%x can't be added to pure list because of search order\n",
                                        pack.pakFilename.toString(),
                                        pack.checksum
                                    )
                                }
                            }
                            i++ // advance server checksums only
                        }
                    } else {
                        // didn't find a matching checksum
                        success = false
                        missingChecksums[imissing++] = pureChecksums[i]
                        missingChecksums[imissing] = 0
                        if (fs_debug.GetBool()) {
                            idLib.common.Printf("checksum not found - 0x%x\n", pureChecksums[i])
                        }
                        i++ // advance the server checksums only
                    }
                }
            }
            while (j < serverPaks.Num()) {
                success = false // just in case some extra pak files are referenced at the end of our local list
                if (fs_debug.GetBool()) {
                    idLib.common.Printf(
                        "pak %s checksumed 0x%x is an extra reference at the end of local pure list\n",
                        serverPaks[j].pakFilename.toString(),
                        serverPaks[j].checksum
                    )
                }
                j++
            }

            // DLL checksuming
            if (0 == _gamePakChecksum) { // dhewm3/original DOOM 3 protocol does not send game code pak checksum.
                // Skip DLL validation in this case (match dhewm3 behavior).
            } else {
                assert(gameDLLChecksum != 0)
                if (ID_FAKE_PURE) {
                    gamePakChecksum = _gamePakChecksum
                }
                if (_gamePakChecksum != gamePakChecksum) { // current DLL is wrong, search for a pak with the approriate checksum
                    // ( search all paks, the pure list is not relevant here )
                    pack = GetPackForChecksum(_gamePakChecksum)
                    if (null == pack) {
                        if (fs_debug.GetBool()) {
                            idLib.common.Printf("missing the game code pak ( 0x%x )\n", _gamePakChecksum)
                        } // if there are other paks missing they have also been marked above
                        missingGamePakChecksum[0] = _gamePakChecksum
                        return fsPureReply_t.PURE_MISSING
                    } // if assets paks are missing, don't try any of the DLL restart / NODLL
                    if (imissing != 0) {
                        return fsPureReply_t.PURE_MISSING
                    } // we have a matching pak
                    if (fs_debug.GetBool()) {
                        idLib.common.Printf(
                            "server's game code pak candidate is '%s' ( 0x%x )\n",
                            pack.pakFilename.toString(),
                            pack.checksum
                        )
                    } // make sure there is a valid DLL for us
                    if (pack.hashTable[dllHash] != null) {
                        pakFile = pack.hashTable[dllHash]
                        while (pakFile != null) {
                            if (FilenameCompare(pakFile.name.toString(), dllName[0])) {
                                gamePakChecksum =
                                    _gamePakChecksum // this will be used to extract the DLL in pure mode FindDLL
                                return fsPureReply_t.PURE_RESTART
                            }
                            pakFile = pakFile.next
                        }
                    }
                    idLib.common.Warning(
                        "media is misconfigured. server claims pak '%s' ( 0x%x ) has media for us, but '%s' is not found\n",
                        pack.pakFilename.toString(),
                        pack.checksum,
                        dllName[0]
                    )
                    return fsPureReply_t.PURE_NODLL
                }
            }

            // we reply to missing after DLL check so it can be part of the list
            if (imissing != 0) {
                return fsPureReply_t.PURE_MISSING
            }

            // one last check
            if (loadedFileFromDir) {
                success = false
                if (fs_debug.GetBool()) {
                    idLib.common.Printf("SetPureServerChecksums: there are files loaded from dir\n")
                }
            }
            return if (success) fsPureReply_t.PURE_OK else fsPureReply_t.PURE_RESTART
        }

        override fun GetPureServerChecksums(checksums: IntArray, OS: Int, _gamePakChecksum: CInt?) {
            var i: Int
            i = 0
            while (i < serverPaks.Num()) {
                checksums[i] = serverPaks[i].checksum
                i++
            }
            checksums[i] = 0
            if (_gamePakChecksum != null) {
                if (OS >= 0) {
                    _gamePakChecksum._val = gamePakForOS[OS]
                } else {
                    _gamePakChecksum._val = gamePakChecksum
                }
            }
        }

        override fun SetRestartChecksums(pureChecksums: IntArray, gamePakChecksum: Int) {
            var i: Int
            var pack: pack_t?
            restartChecksums.Clear()
            i = 0
            // FIX: C++ uses `while ( pureChecksums[ i ] )` (0-terminated sentinel),
            // not `while (i < pureChecksums.size)` which processes the entire array
            while (i < pureChecksums.size && pureChecksums[i] != 0) {
                pack = GetPackForChecksum(pureChecksums[i], true)
                if (null == pack) {
                    idLib.common.FatalError(
                        "SetRestartChecksums failed: no pak for checksum 0x%x\n",
                        pureChecksums[i]
                    )
                    return
                }
                if (pack.addon && addonChecksums.FindIndex(pack.checksum) < 0) {
                    // can't mark it pure if we're not even gonna search it :-)
                    addonChecksums.Append(pack.checksum)
                }
                restartChecksums.Append(pureChecksums[i])
                i++
            }
            restartGamePakChecksum = gamePakChecksum
        }

        override fun ClearPureChecksums() {
            idLib.common.DPrintf("Cleared pure server lock\n")
            serverPaks.Clear()
        }

        override fun GetOSMask(): Int {
            var i: Int
            var ret = 0
            i = 0
            while (i < MAX_GAME_OS) {
                if (fileSystemLocal.gamePakForOS[i] != 0) {
                    ret = ret or (1 shl i)
                }
                i++
            }
            return if (0 == ret) {
                -1
            } else ret
        }

        /*
         ============
         idFileSystemLocal::ReadFile

         Filename are relative to the search path
         a null buffer will just return the file length and time without loading
         timestamp can be NULL if not required
         ============
         */
        override fun ReadFile(relativePath: String, buffer: Array<ByteBuffer?>?, timestamp: LongArray?): Int {
            val f: idFile?
            val buf: ByteBuffer?
            val len = CInt()
            val isConfig: Boolean
            if (searchPaths == null) {
                idLib.common.FatalError("Filesystem call made without initialization\n")
            }
            if (relativePath == null || relativePath.isEmpty()) {
                idLib.common.FatalError("idFileSystemLocal::ReadFile with empty name\n")
            }
            if (timestamp != null) {
                timestamp[0] = FILE_NOT_FOUND_TIMESTAMP.toLong()
            }
            if (buffer != null) {
                buffer[0] = null //TODO:
            }

//            buf = null;	// quiet compiler warning
            // if this is a .cfg file and we are playing back a journal, read
            // it from the journal file
            if (relativePath.endsWith(".cfg")) {
//            if (relativePath.indexOf(".cfg") == relativePath.length() - 4) {
                isConfig = true
                if (EventLoop.eventLoop.JournalLevel() == 2) {
                    var r: Int
                    loadCount++
                    loadStack++
                    idLib.common.DPrintf("Loading %s from journal file.\n", relativePath)
                    len._val = 0
                    r = EventLoop.eventLoop.com_journalDataFile!!.ReadInt(len)
                    val r_bits = r * 8
                    if (r_bits != Integer.SIZE) {
                        buffer!![0] = null
                        return -1
                    }
                    buf = ByteBuffer.allocate(len._val + 1) // Heap.Mem_ClearedAlloc(len + 1);
                    buffer!![0] = buf
                    r = EventLoop.eventLoop.com_journalDataFile!!.Read(buf, len._val)
                    if (r != len._val) {
                        idLib.common.FatalError("Read from journalDataFile failed")
                    }

                    // guarantee that it will have a trailing 0 for string operations
                    buf.put(len._val, 0.toByte())
                    return len._val
                }
            } else {
                isConfig = false
            }

            // look for it in the filesystem or pack files
            f = OpenFileRead(relativePath, buffer != null)
            if (f == null) {
                if (buffer != null) {
                    buffer[0] = null
                }
                return -1
            }
            len._val = f.Length()
            if (timestamp != null) {
                timestamp[0] = f.Timestamp()
            }
            if (null == buffer) {
                CloseFile(f)
                return len._val
            }
            loadCount++
            loadStack++
            buf = ByteBuffer.allocate(len._val + 1) // Heap.Mem_ClearedAlloc(len + 1);
            buffer[0] = buf
            f.Read(buf, len._val)

            // guarantee that it will have a trailing 0 for string operations
//            buf.put(len[0], (byte) 0);
            CloseFile(f)

            // if we are journalling and it is a config file, write it to the journal file
            if (isConfig && EventLoop.eventLoop.JournalLevel() == 1) {
                idLib.common.DPrintf("Writing %s to journal file.\n", relativePath)
                EventLoop.eventLoop.com_journalDataFile!!.WriteInt(len._val)
                EventLoop.eventLoop.com_journalDataFile!!.Write(buf, len._val)
                EventLoop.eventLoop.com_journalDataFile!!.Flush()
            }
            return len._val
        }

        override fun ReadFile(relativePath: String, buffer: Array<ByteBuffer?>?): Int {
            return ReadFile(relativePath, buffer, null)
        }

        override fun FreeFile(buffer: Array<ByteBuffer?>) {
            if (null == searchPaths) {
                idLib.common.FatalError("Filesystem call made without initialization\n")
            }
            if (buffer.isEmpty()) { // TODO: make sure its fine, could be deleted at all if we want to preserve null safety
                idLib.common.FatalError("idFileSystemLocal::FreeFile( null )")
            }
            loadStack--

//            Heap.Mem_Free(buffer);
            //buffer[0] = null
        }

        /*
         ============
         idFileSystemLocal::WriteFile

         Filenames are relative to the search path
         ============
         */
        override fun WriteFile(relativePath: String, buffer: ByteBuffer, size: Int): Int {
            return WriteFile(relativePath, buffer, size, "fs_savepath")
        }

        override fun WriteFile(
            relativePath: String,
            buffer: ByteBuffer,
            size: Int,
            basePath: String /*"fs_savepath"*/
        ): Int {
            var size = size
            val f: idFile?
            if (null == searchPaths) {
                idLib.common.FatalError("Filesystem call made without initialization\n")
            }
            if (null == relativePath || null == buffer) {
                idLib.common.FatalError("idFileSystemLocal::WriteFile: NULL parameter")
            }
            f = this.OpenFileWrite(relativePath, basePath)
            if (null == f) {
                idLib.common.Printf("Failed to open %s\n", relativePath)
                return -1
            }
            size = f.Write(buffer, size)
            CloseFile(f)
            return size
        }

        override fun RemoveFile(relativePath: String) {
            var OSPath: idStr
            if (fs_devpath.GetString()!!.isNotEmpty()) {
                OSPath = idStr(BuildOSPath(fs_devpath.GetString()!!, gameFolder.toString(), relativePath))
                win_main.remove(OSPath)
            }
            OSPath = idStr(BuildOSPath(fs_savepath.GetString()!!, gameFolder.toString(), relativePath))
            win_main.remove(OSPath)
            ClearDirCache()
        }

        /*
         ===========
         idFileSystemLocal::OpenFileReadFlags

         Finds the file in the search path, following search flag recommendations
         Returns filesize and an open FILE pointer.
         Used for streaming data out of either a
         separate file or a ZIP file.
         ===========
         */

        fun OpenFileReadFlags(
            relativePath: String,
            searchFlags: Int,
            foundInPak: Array<pack_t?>? = null /*= NULL*/,
            allowCopyFiles: Boolean = true /*= true*/,
            gamedir: String? = null /*= NULL*/
        ): idFile? {
            var relativePath = relativePath
            var search: searchpath_s?
            var netpath: idStr
            var pak: pack_t
            var pakFile: fileInPack_s?
            var dir: directory_t?
            val hash: Long
            var fp: FileChannel?
            if (null == searchPaths) {
                idLib.common.FatalError("Filesystem call made without initialization\n")
            }
            if (relativePath.isEmpty()) {
                idLib.common.FatalError("idFileSystemLocal::OpenFileRead: null 'relativePath' parameter passed\n")
            }
            if (foundInPak != null) {
                foundInPak[0] = null
            }

            // qpaths are not supposed to have a leading slash
            if (relativePath[0] == '/' || relativePath[0] == '\\') { //TODO: regex
                relativePath = relativePath.substring(1)
            }

            // make absolutely sure that it can't back up the path.
            // The searchpaths do guarantee that something will always
            // be prepended, so we don't need to worry about "c:" or "//limbo"
            if (relativePath.contains("..") || relativePath.contains("::")) { //TODO: regex
                return null
            }

            // edge case
            if (relativePath.isEmpty() /*[0] == '\0'*/) {
                return null
            }

            // make sure the doomkey file is only readable by game at initialization
            // any other time the key should only be accessed in memory using the provided functions
            if (idLib.common.IsInitialized() && (idStr.Icmp(
                    relativePath,
                    Licensee.CDKEY_FILE
                ) == 0 || idStr.Icmp(relativePath, Licensee.XPKEY_FILE) == 0)
            ) {
                return null
            }

            //
            // search through the path, one element at a time
            //
            hash = HashFileName(relativePath)
            search = searchPaths
            while (search != null) {
                if (search.dir != null && searchFlags and FSFLAG_SEARCH_DIRS != 0) {
                    // check a file in the directory tree

                    // if we are running restricted, the only files we
                    // will allow to come from the directory are .cfg files
                    if (fs_restrict.GetBool() || serverPaks.Num() != 0) {
                        if (!FileAllowedFromDir(relativePath)) {
                            search = search.next
                            continue
                        }
                    }
                    dir = search.dir!!
                    if (gamedir != null && !gamedir.isEmpty()) {
                        if (dir.gamedir.toString() != gamedir) {
                            search = search.next
                            continue
                        }
                    }
                    netpath = idStr(BuildOSPath(dir.path.toString(), dir.gamedir.toString(), relativePath))
                    fp = OpenOSFileCorrectName(netpath, "rb")
                    if (fp == null) {
                        search = search.next
                        continue
                    }
                    val file = idFile_Permanent()
                    file.o = fp
                    file.name.set(relativePath)
                    file.fullPath.set(netpath)
                    file.mode = 1 shl TempDump.etoi(fsMode_t.FS_READ)
                    file.fileSize = DirectFileLength(file.o!!).toInt()
                    if (fs_debug.GetInteger() != 0) {
                        idLib.common.Printf(
                            "idFileSystem::OpenFileRead: %s (found in '%s/%s')\n",
                            relativePath,
                            dir.path.toString(),
                            dir.gamedir.toString()
                        )
                    }
                    if (!loadedFileFromDir && !FileAllowedFromDir(relativePath)) {
                        if (restartChecksums.Num() != 0) {
                            idLib.common.FatalError(
                                "'%s' loaded from directory: Failed to restart with pure mode restrictions for server connect",
                                relativePath
                            )
                        }
                        idLib.common.DPrintf(
                            "filesystem: switching to pure mode will require a restart. '%s' loaded from directory.\n",
                            relativePath
                        )
                        loadedFileFromDir = true
                    }

                    // if fs_copyfiles is set
                    if (allowCopyFiles && fs_copyfiles.GetInteger() != 0) {
                        val copypath: idStr
                        val name = idStr()
                        copypath = idStr(BuildOSPath(fs_savepath.GetString()!!, dir.gamedir.toString(), relativePath))
                        netpath.ExtractFileName(name)
                        copypath.StripFilename()
                        copypath.Append(PATHSEPERATOR_STR)
                        copypath.Append(name)
                        val isFromCDPath = 0 == dir.path.Cmp(fs_cdpath.GetString()!!)
                        val isFromSavePath = 0 == dir.path.Cmp(fs_savepath.GetString()!!)
                        val isFromBasePath = 0 == dir.path.Cmp(fs_basepath.GetString()!!)
                        when (fs_copyfiles.GetInteger()) {
                            1 ->                                 // copy from cd path only
                                if (isFromCDPath) {
                                    CopyFile(netpath.toString(), copypath.toString())
                                }

                            2 ->                                 // from cd path + timestamps
                                if (isFromCDPath) {
                                    CopyFile(netpath.toString(), copypath.toString())
                                } else if (isFromSavePath || isFromBasePath) {
                                    val sourcepath: idStr
                                    sourcepath =
                                        idStr(
                                            BuildOSPath(
                                                fs_cdpath.GetString()!!,
                                                dir.gamedir.toString(),
                                                relativePath
                                            )
                                        )
                                    val t1 = win_main.Sys_FileTimeStamp(sourcepath.toString())
                                    val t2 = win_main.Sys_FileTimeStamp(copypath.toString())
                                    if (t1 > t2) {
                                        CopyFile(sourcepath.toString(), copypath.toString())
                                    }
                                }

                            3 -> if (isFromCDPath || isFromBasePath) {
                                CopyFile(netpath.toString(), copypath.toString())
                            }

                            4 -> if (isFromCDPath && !isFromBasePath) {
                                CopyFile(netpath.toString(), copypath.toString())
                            }
                        }
                    }
                    return file
                } else if (search.pack != null && searchFlags and FSFLAG_SEARCH_PAKS != 0) {
                    if (null == search.pack!!.hashTable[hash.toInt()]) {
                        search = search.next
                        continue
                    }

                    // disregard if it doesn't match one of the allowed pure pak files
                    if (serverPaks.Num() != 0) {
                        GetPackStatus(search.pack!!)
                        if (search.pack!!.pureStatus != pureStatus_t.PURE_NEVER && null == serverPaks.Find(search.pack!!)) {
                            search = search.next
                            continue  // not on the pure server pak list
                        }
                    }

                    // look through all the pak file elements
                    pak = search.pack!!
                    if (searchFlags and FSFLAG_BINARY_ONLY != 0) {
                        // make sure this pak is tagged as a binary file
                        if (pak.binary == binaryStatus_t.BINARY_UNKNOWN) {
                            var confHash: Int
                            //					fileInPack_s	pakFile;
                            confHash = HashFileName(BINARY_CONFIG).toInt()
                            pak.binary = binaryStatus_t.BINARY_NO
                            pakFile = search.pack!!.hashTable[confHash]
                            while (pakFile != null) {
                                if (FilenameCompare(pakFile.name.toString(), BINARY_CONFIG)) {
                                    pak.binary = binaryStatus_t.BINARY_YES
                                    break
                                }
                                pakFile = pakFile.next
                            }
                        }
                        if (pak.binary == binaryStatus_t.BINARY_NO) {
                            search = search.next
                            continue  // not a binary pak, skip
                        }
                    }
                    pakFile = pak.hashTable[hash.toInt()]
                    while (pakFile != null) {

                        // case and separator insensitive comparisons
                        if (FilenameCompare(pakFile.name.toString(), relativePath)) {
                            val file = ReadFileFromZip(pak, pakFile, relativePath)
                            if (foundInPak != null) {
                                foundInPak[0] = pak
                            }
                            if (!pak.referenced && 0 == searchFlags and FSFLAG_PURE_NOREF) {
                                // mark this pak referenced
                                if (fs_debug.GetInteger() != 0) {
                                    idLib.common.Printf(
                                        "idFileSystem::OpenFileRead: %s . adding %s to referenced paks\n",
                                        relativePath,
                                        pak.pakFilename.toString()
                                    )
                                }
                                pak.referenced = true
                            }
                            if (fs_debug.GetInteger() != 0) {
                                idLib.common.Printf(
                                    "idFileSystem::OpenFileRead: %s (found in '%s')\n",
                                    relativePath,
                                    pak.pakFilename.toString()
                                )
                            }
                            return file
                        }
                        pakFile = pakFile.next
                    }
                }
                search = search.next
            }
            if (searchFlags and FSFLAG_SEARCH_ADDONS != 0) {
                search = addonPaks
                while (search != null) {
                    assert(search.pack != null)
                    //			fileInPack_s	pakFile;
                    pak = search.pack!!
                    pakFile = pak.hashTable[hash.toInt()]
                    while (pakFile != null) {
                        if (FilenameCompare(pakFile.name.toString(), relativePath)) {
                            val file = ReadFileFromZip(pak, pakFile, relativePath)
                            if (foundInPak != null) {
                                foundInPak[0] = pak
                            }
                            // we don't toggle pure on paks found in addons - they can't be used without a reloadEngine anyway
                            if (fs_debug.GetInteger() != 0) {
                                idLib.common.Printf(
                                    "idFileSystem::OpenFileRead: %s (found in addon pk4 '%s')\n",
                                    relativePath,
                                    search.pack!!.pakFilename.toString()
                                )
                            }
                            return file
                        }
                        pakFile = pakFile.next
                    }
                    search = search.next
                }
            }
            if (fs_debug.GetInteger() != 0) {
                idLib.common.Printf("Can't find %s\n", relativePath)
            }
            return null
        }

        override fun OpenFileRead(relativePath: String): idFile? {
            return OpenFileRead(relativePath, true)
        }

        override fun OpenFileRead(relativePath: String, allowCopyFiles: Boolean): idFile? {
            return OpenFileRead(relativePath, allowCopyFiles, null)
        }

        override fun OpenFileRead(relativePath: String, allowCopyFiles: Boolean, gamedir: String?): idFile? {
            return OpenFileReadFlags(
                relativePath,
                FSFLAG_SEARCH_DIRS or FSFLAG_SEARCH_PAKS,
                null,
                allowCopyFiles,
                gamedir
            )
        }

        override fun OpenFileWrite(relativePath: String, basePath: String): idFile? {
            var path: String
            val OSpath: String
            val f: idFile_Permanent
            if (null == searchPaths) {
                idLib.common.FatalError("Filesystem call made without initialization\n")
            }
            path = CVarSystem.cvarSystem.GetCVarString(basePath)
            if (path.isEmpty()) { //TODO:check null
                path = fs_savepath.GetString()!!
            }
            OSpath = BuildOSPath(path, gameFolder.toString(), relativePath)
            if (fs_debug.GetInteger() != 0) {
                idLib.common.Printf("idFileSystem::OpenFileWrite: %s\n", OSpath)
            }

            // if the dir we are writing to is in our current list, it will be outdated
            // so just flush everything
            ClearDirCache()
            idLib.common.DPrintf("writing to: %s\n", OSpath)
            CreateOSPath(OSpath)
            f = idFile_Permanent()
            f.o = OpenOSFile(OSpath, "wb")
            if (f.o == null) {
//		delete f;
                return null
            }
            f.name.set(relativePath)
            f.fullPath.set(OSpath)
            f.mode = 1 shl fsMode_t.FS_WRITE.ordinal
            f.handleSync = false
            f.fileSize = 0
            return f
        }

        override fun OpenFileAppend(filename: String): idFile? {
            return OpenFileAppend(filename, false)
        }

        override fun OpenFileAppend(filename: String, sync: Boolean): idFile? {
            return OpenFileAppend(filename, sync, "fs_basepath")
        }

        override fun OpenFileAppend(filename: String, sync: Boolean, basePath: String /*= "fs_basepath"*/): idFile? {
            var path: String
            val OSpath: String
            val f: idFile_Permanent
            if (null == searchPaths) {
                idLib.common.FatalError("Filesystem call made without initialization\n")
            }
            path = CVarSystem.cvarSystem.GetCVarString(basePath)
            // FIX: C++ checks `if ( !path[0] )` meaning "if path is empty", not "if path is NOT empty"
            if (path.isEmpty()) {
                path = fs_savepath.GetString()!!
            }
            OSpath = BuildOSPath(path, gameFolder.toString(), filename)
            CreateOSPath(OSpath)
            if (fs_debug.GetInteger() != 0) {
                idLib.common.Printf("idFileSystem::OpenFileAppend: %s\n", OSpath)
            }
            f = idFile_Permanent()
            f.o = OpenOSFile(OSpath, "ab")
            if (f.o == null) {
//		delete f;
                return null
            }
            f.name.set(filename)
            f.fullPath.set(OSpath)
            f.mode = (1 shl TempDump.etoi(fsMode_t.FS_WRITE)) + (1 shl TempDump.etoi(fsMode_t.FS_APPEND))
            f.handleSync = sync
            f.fileSize = DirectFileLength(f.o!!).toInt()
            return f
        }

        override fun OpenFileByMode(relativePath: String, mode: fsMode_t): idFile? {
            when (mode) {
                fsMode_t.FS_READ -> return OpenFileRead(relativePath)
                fsMode_t.FS_WRITE -> return OpenFileWrite(relativePath)
                fsMode_t.FS_APPEND -> return OpenFileAppend(relativePath, true)
            }
            //idLib.common.FatalError("idFileSystemLocal::OpenFileByMode: bad mode")
        }

        override fun OpenExplicitFileRead(OSPath: String): idFile? {
            val f: idFile_Permanent
            if (null == searchPaths) {
                idLib.common.FatalError("Filesystem call made without initialization\n")
            }
            if (fs_debug.GetInteger() != 0) {
                idLib.common.Printf("idFileSystem::OpenExplicitFileRead: %s\n", OSPath)
            }
            idLib.common.DPrintf("idFileSystem::OpenExplicitFileRead - reading from: %s\n", OSPath)
            f = idFile_Permanent()
            f.o = OpenOSFile(OSPath, "rb")
            if (f.o == null) {
//		delete f;
                return null
            }
            f.name.set(OSPath)
            f.fullPath.set(OSPath)
            f.mode = 1 shl TempDump.etoi(fsMode_t.FS_READ)
            f.handleSync = false
            f.fileSize = DirectFileLength(f.o!!).toInt()
            return f
        }

        override fun OpenExplicitFileWrite(OSPath: String): idFile? {
            val f: idFile_Permanent
            if (null == searchPaths) {
                idLib.common.FatalError("Filesystem call made without initialization\n")
            }
            if (fs_debug.GetInteger() != 0) {
                idLib.common.Printf("idFileSystem::OpenExplicitFileWrite: %s\n", OSPath)
            }
            idLib.common.DPrintf("writing to: %s\n", OSPath)
            CreateOSPath(OSPath)
            f = idFile_Permanent()
            f.o = OpenOSFile(OSPath, "wb")
            if (f.o == null) {
//		delete f;
                return null
            }
            f.name.set(OSPath)
            f.fullPath.set(OSPath)
            f.mode = 1 shl TempDump.etoi(fsMode_t.FS_WRITE)
            f.handleSync = false
            f.fileSize = 0
            return f
        }

        override fun CloseFile(f: idFile) {
            if (null == searchPaths) {
                idLib.common.FatalError("Filesystem call made without initialization\n")
            }
            if (f is idFile_Permanent) {
                f.Close()
            }
        }

        override fun BackgroundDownload(bgl: backgroundDownload_s) {
            if (bgl.opcode == dlType_t.DLTYPE_FILE) {
                if ( /*dynamic_cast<idFile_Permanent *>*/bgl.f != null) {
                    // add the bgl to the background download list
                    Sys_EnterCriticalSection()
                    bgl.next = backgroundDownloads
                    backgroundDownloads = bgl
                    Sys_TriggerEvent()
                    Sys_LeaveCriticalSection()
                } else {
                    // read zipped file directly
                    bgl.f!!.Seek(bgl.file.position.toLong(), fsOrigin_t.FS_SEEK_SET)
                    bgl.f!!.Read(bgl.file.buffer!!, bgl.file.length)
                    bgl.completed = true
                }
            } else {
                Sys_EnterCriticalSection()
                bgl.next = backgroundDownloads
                backgroundDownloads = bgl
                Sys_TriggerEvent()
                Sys_LeaveCriticalSection()
            }
        }

        override fun ResetReadCount() {
            readCount = 0
        }

        override fun GetReadCount(): Int {
            return readCount
        }

        override fun AddToReadCount(c: Int) {
            readCount += c
        }

        override fun FindDLL(basename: String, _dllPath: CharArray, updateChecksum: Boolean) {
            var updateChecksum = updateChecksum
            var dllFile: idFile? = null
            //            char[] __dllName = new char[MAX_OSPATH];
            val __dllName = arrayOf<String>("")
            var dllPath = idStr()
            val dllHash: Long
            val inPak = arrayOfNulls<pack_t>(1)
            val pak: pack_t?
            var pakFile: fileInPack_s?
            var dllName: String = ""
            idLib.sys.DLL_GetFileName("" + basename, __dllName, MAX_OSPATH)
            dllHash = HashFileName(__dllName[0])

// #if ID_FAKE_PURE
            // if ( 1 ) {
// #else
            if (0 == serverPaks.Num()) {
// #endif
                // from executable directory first - this is handy for developement
                dllName = __dllName[0]
                dllPath.set(win_main.Sys_EXEPath())
                dllPath.StripFilename()
                dllPath.AppendPath(dllName)
                dllFile = OpenExplicitFileRead(dllPath.toString())
            }
            if (null == dllFile) {
                if (0 == serverPaks.Num()) {
                    // not running in pure mode, try to extract from a pak file first
                    dllFile = OpenFileReadFlags(
                        dllName,
                        FSFLAG_SEARCH_PAKS or FSFLAG_PURE_NOREF or FSFLAG_BINARY_ONLY,
                        inPak
                    )
                    if (dllFile != null) {
                        idLib.common.Printf("found DLL in pak file: %s\n", dllFile.GetFullPath())
                        dllPath = idStr(RelativePathToOSPath(dllName, "fs_savepath"))
                        CopyFile(dllFile, dllPath.toString())
                        CloseFile(dllFile)
                        dllFile = OpenFileReadFlags(dllName, FSFLAG_SEARCH_DIRS)
                        if (null == dllFile) {
                            idLib.common.Error("DLL extraction to fs_savepath failed\n")
                        } else if (updateChecksum) {
                            gameDLLChecksum = GetFileChecksum(dllFile)
                            gamePakChecksum = inPak[0]!!.checksum
                            updateChecksum = false // don't try again below
                        }
                    } else {
                        // didn't find a source in a pak file, try in the directory
                        dllFile = OpenFileReadFlags(dllName, FSFLAG_SEARCH_DIRS)
                        if (dllFile != null) {
                            if (updateChecksum) {
                                val gameDLLChecksum = intArrayOf(GetFileChecksum(dllFile).also { gameDLLChecksum = it })
                                // see if we can mark a pak file
                                pak = FindPakForFileChecksum(dllName, gameDLLChecksum, false)
                                this.gameDLLChecksum = gameDLLChecksum[0]
                                gamePakChecksum = pak?.checksum ?: 0
                                updateChecksum = false
                            }
                        }
                    }
                } else {
                    // we are in pure mode. this path to be reached only for game DLL situations
                    // with a code pak checksum given by server
                    assert(gamePakChecksum != 0)
                    assert(updateChecksum)
                    pak = GetPackForChecksum(gamePakChecksum)
                    if (null == pak) {
                        // not supposed to happen, bug in pure code?
                        idLib.common.Warning("FindDLL in pure mode: game pak not found ( 0x%x )\n", gamePakChecksum)
                    } else {
                        // extract and copy
                        pakFile = pak.hashTable[dllHash.toInt()]
                        while (pakFile != null) {
                            if (FilenameCompare(pakFile.name.toString(), dllName)) {
                                dllFile = ReadFileFromZip(pak, pakFile, dllName)
                                idLib.common.Printf("found DLL in game pak file: %s\n", pak.pakFilename.toString())
                                dllPath = idStr(RelativePathToOSPath(dllName, "fs_savepath"))
                                CopyFile(dllFile, dllPath.toString())
                                CloseFile(dllFile)
                                dllFile = OpenFileReadFlags(dllName, FSFLAG_SEARCH_DIRS)
                                if (null == dllFile) {
                                    idLib.common.Error("DLL extraction to fs_savepath failed\n")
                                } else {
                                    gameDLLChecksum = GetFileChecksum(dllFile)
                                    updateChecksum = false // don't try again below
                                }
                            }
                            pakFile = pakFile.next
                        }
                    }
                }
            }
            if (updateChecksum) {
                gameDLLChecksum = dllFile?.let { GetFileChecksum(it) } ?: 0
                gamePakChecksum = 0
            }
            if (dllFile != null) {
                dllPath = idStr(dllFile.GetFullPath())
                CloseFile(dllFile)
                //                dllFile = null;
            } else {
                dllPath = idStr()
            }
            idStr.snPrintf(_dllPath, MAX_OSPATH, dllPath.toString())
        }

        override fun ClearDirCache() {
            var i: Int
            dir_cache_index = 0
            dir_cache_count = 0
            i = 0
            while (i < MAX_CACHED_DIRS) {
                dir_cache[i].clear()
                i++
            }
        }

        override fun HasD3XP(): Boolean {
            var i: Int
            val dirs = idStrList() /*, pk4s*/
            var gamepath: String?
            if (d3xp == -1) {
                return false
            } else if (d3xp == 1) {
                return true
            }

            // check for d3xp's d3xp/pak000.pk4 in any search path
            // checking wether the pak is loaded by checksum wouldn't be enough:
            // we may have a different fs_game right now but still need to reply that it's installed
            val search = arrayOf(
                fs_savepath.GetString()!!,
                fs_devpath.GetString()!!,
                fs_basepath.GetString()!!,
                fs_cdpath.GetString()!!
            )
            var pakfile: idFile?
            i = 0
            while (i < 4) {
                pakfile = OpenExplicitFileRead(BuildOSPath(search[i], "d3xp", "pak000.pk4"))
                if (pakfile != null) {
                    CloseFile(pakfile)
                    d3xp = 1
                    return true
                }
                i++
            }
            // if we didn't find a pk4 file then the user might have unpacked so look for default.cfg file
            // that's the old way mostly used during developement. don't think it hurts to leave it there
            ListOSFiles(fs_basepath.GetString()!!, "/", dirs)
            i = 0
            while (i < dirs.size()) {
                if (dirs[i].Icmp("d3xp") == 0) {
                    gamepath = BuildOSPath(fs_savepath.GetString()!!, dirs[i].toString(), "default.cfg")
                    val cfg = OpenExplicitFileRead(gamepath)
                    if (cfg != null) {
                        CloseFile(cfg)
                        d3xp = 1
                        return true
                    }
                }
                i++
            }
            //#endif
            d3xp = -1
            return false
        }

        override fun RunningD3XP(): Boolean {
            // TODO: mark the checksum of the gold XP and check for it being referenced ( for double mod support )
            // a simple fs_game check should be enough for now..
            return (0 == idStr.Icmp(fs_game.GetString()!!, "d3xp")
                    || 0 == idStr.Icmp(fs_game_base.GetString()!!, "d3xp"))
        }

        /*
         =================
         idFileSystemLocal::CopyFile

         Copy a fully specified file from one place to another
         =================
         */
        override fun CopyFile(fromOSPath: String, toOSPath: String) {
            var f: FileChannel?
            val len: Long
            val buf: ByteBuffer?
            idLib.common.Printf("copy %s to %s\n", fromOSPath, toOSPath)
            f = OpenOSFile(fromOSPath, "rb")
            if (null == f) {
                return
            }
            try {
//            fseek(f, 0, SEEK_END);
//            len = ftell(f);
//            fseek(f, 0, SEEK_SET);
                len = f.size()
                buf = ByteBuffer.allocate(len.toInt())
                if (f.read(buf).toLong() != len) {
//            if (fread(buf, 1, len, f) != len) {
                    idLib.common.FatalError("short read in idFileSystemLocal::CopyFile()\n")
                }
                f.close()
                CreateOSPath(toOSPath)
                f = OpenOSFile(toOSPath, "wb")
                if (null == f) {
                    idLib.common.Printf("could not create destination file\n")
                    //                Heap.Mem_Free(buf);
                    return
                }
                if (f.write(buf).toLong() != len) {
//            if (fwrite(buf, 1, len, f) != len) {
                    idLib.common.FatalError("short write in idFileSystemLocal::CopyFile()\n")
                }
                f.close()
                //            Heap.Mem_Free(buf);
            } catch (ex: IOException) {
                Logger.getLogger(FileSystem_h::class.java.name).log(Level.SEVERE, null, ex)
            }
        }

        override fun ValidateDownloadPakForChecksum(checksum: Int, path: CharArray, isBinary: Boolean): Int {
            var i: Int
            val testList = idStrList()
            val name: idStr
            val relativePath = idStr()
            val pakBinary: Boolean
            val pak = GetPackForChecksum(checksum)
            if (null == pak) {
                return 0
            }

            // validate this pak for a potential download
            // ignore pak*.pk4 for download. those are reserved to distribution and cannot be downloaded
            name = pak.pakFilename
            name.StripPath()
            if (name.toString().startsWith("pak")) {
                idLib.common.DPrintf("%s is not a donwloadable pak\n", pak.pakFilename.toString())
                return 0
            }
            assert(pak.binary != binaryStatus_t.BINARY_UNKNOWN)
            pakBinary = pak.binary == binaryStatus_t.BINARY_YES
            if (isBinary != pakBinary) {
                idLib.common.DPrintf("%s binary flag mismatch\n", pak.pakFilename.toString())
                return 0
            }

            // extract a path that includes the fs_game: != OSPathToRelativePath
            testList.add(fs_savepath.GetString()!!)
            testList.add(fs_devpath.GetString()!!)
            testList.add(fs_basepath.GetString()!!)
            testList.add(fs_cdpath.GetString()!!)
            i = 0
            while (i < testList.size()) {
                if (testList[i].Length() != 0
                    && testList[i].Icmpn(pak.pakFilename.toString(), testList[i].Length()) == 0
                ) {
                    relativePath.set(pak.pakFilename.toString().substring(testList[i].Length() + 1))
                    break
                }
                i++
            }
            if (i == testList.size()) {
                idLib.common.Warning(
                    "idFileSystem::ValidateDownloadPak: failed to extract relative path for %s",
                    pak.pakFilename.toString()
                )
                return 0
            }
            idStr.Copynz(path, relativePath.data, MAX_STRING_CHARS)
            return pak.length
        }

        override fun MakeTemporaryFile(): idFile {
            var f: FileChannel?
            try {
                f = win_main.tmpfile()
                //            if (NOT(f)) {
            } catch (e: IOException) {
                idLib.common.Warning(
                    "idFileSystem::MakeTemporaryFile failed: %s",
                    e.message!!
                ) // strerror(System.err));
                f = null
            }
            val file = idFile_Permanent()
            file.o = f
            file.name.set("<tempfile>")
            file.fullPath.set("<tempfile>")
            file.mode = (1 shl TempDump.etoi(fsMode_t.FS_READ)) + (1 shl TempDump.etoi(fsMode_t.FS_WRITE))
            file.fileSize = 0
            return file
        }

        /*
         ===============
         idFileSystemLocal::AddZipFile
         adds a downloaded pak file to the list so we can work out what we have and what we still need
         the isNew flag is set to true, indicating that we cannot add this pak to the search lists without a restart
         ===============
         */
        override fun AddZipFile(path: String): Int {
            val fullpath = idStr(fs_savepath.GetString()!!)
            val pak: pack_t?
            val search: searchpath_s
            var last: searchpath_s?
            fullpath.AppendPath(path)
            pak = LoadZipFile(fullpath.toString())
            if (null == pak) {
                idLib.common.Warning("AddZipFile %s failed\n", path)
                return 0
            }
            // insert the pak at the end of the search list - temporary until we restart
            pak.isNew = true
            search = searchpath_s()
            search.dir = null
            search.pack = pak
            search.next = null
            last = searchPaths
            while (last!!.next != null) {
                last = last.next
            }
            last.next = search
            idLib.common.Printf("Appended pk4 %s with checksum 0x%x\n", pak.pakFilename.toString() + pak.checksum)
            return pak.checksum
        }

        override fun FindFile(path: String, scheduleAddons: Boolean): findFile_t {
            val pak = arrayOfNulls<pack_t?>(1)
            // FIX: Capture the returned file so it can be closed (resource leak)
            val f = OpenFileReadFlags(
                path,
                FSFLAG_SEARCH_DIRS or FSFLAG_SEARCH_PAKS or FSFLAG_SEARCH_ADDONS,
                pak
            )
                ?: return findFile_t.FIND_NO
            if (null == pak[0]) {
                // found in FS, not even in paks
                CloseFile(f)
                return findFile_t.FIND_YES
            }
            // marking addons for inclusion on reload - may need to do that even when already in the search path
            if (scheduleAddons && pak[0]!!.addon && addonChecksums.FindIndex(pak[0]!!.checksum) < 0) {
                addonChecksums.Append(pak[0]!!.checksum)
            }
            // an addon that's not on search list yet? that will require a restart
            CloseFile(f)
            return if (pak[0]!!.addon && !pak[0]!!.addon_search) {
                findFile_t.FIND_ADDON
            } else findFile_t.FIND_YES
        }

        /*
         ===============
         idFileSystemLocal::GetNumMaps
         account for actual decls and for addon maps
         ===============
         */
        override fun GetNumMaps(): Int {
            var i: Int
            var search: searchpath_s? = null
            var ret = DeclManager.declManager.GetNumDecls(declType_t.DECL_MAPDEF)

            // add to this all addon decls - coming from all addon packs ( searched or not )
            i = 0
            while (i < 2) {
                if (i == 0) {
                    search = searchPaths
                } else if (i == 1) {
                    search = addonPaks
                }
                while (search != null) {
                    if (null == search.pack || !search.pack!!.addon || null == search.pack!!.addon_info) {
                        search = search.next
                        continue
                    }
                    ret += search.pack!!.addon_info!!.mapDecls.Num()
                    search = search.next
                }
                i++
            }
            return ret
        }

        /*
         ===============
         idFileSystemLocal::GetMapDecl
         retrieve the decl dictionary, add a 'path' value
         ===============
         */
        override fun GetMapDecl(idecl: Int): idDict? {
            var idecl = idecl
            var i: Int
            val mapDecl: idDecl?
            val mapDef: idDeclEntityDef?
            val numdecls = DeclManager.declManager.GetNumDecls(declType_t.DECL_MAPDEF)
            var search: searchpath_s? = null
            if (idecl < numdecls) {
                mapDecl = DeclManager.declManager.DeclByIndex(declType_t.DECL_MAPDEF, idecl)
                mapDef = mapDecl as idDeclEntityDef?
                if (null == mapDef) {
                    idLib.common.Error("idFileSystemLocal::GetMapDecl %d: not found\n", idecl)
                    return null
                }
                mapDict.set(mapDef.dict)
                mapDict.Set("path", mapDef.GetName())
                return mapDict
            }
            idecl -= numdecls
            i = 0
            while (i < 2) {
                if (i == 0) {
                    search = searchPaths
                } else if (i == 1) {
                    search = addonPaks
                }
                while (search != null) {
                    if (null == search.pack || !search.pack!!.addon || null == search.pack!!.addon_info) {
                        search = search.next
                        continue
                    }
                    // each addon may have a bunch of map decls
                    if (idecl < search.pack!!.addon_info!!.mapDecls.Num()) {
                        mapDict.set(search.pack!!.addon_info!!.mapDecls[idecl])
                        return mapDict
                    }
                    idecl -= search.pack!!.addon_info!!.mapDecls.Num()
                    assert(idecl >= 0)
                    search = search.next
                }
                i++
            }
            return null
        }

        override fun FindMapScreenshot(path: String, buf: StringBuffer, len: Int) {
            val file: idFile?
            val mapname = idStr(path)
            mapname.StripPath()
            mapname.StripFileExtension()
            idStr.snPrintf(buf, len, "guis/assets/splash/%s.tga", mapname.toString())
            if (ReadFile(buf.toString(), null, null) == -1) {
                // try to extract from an addon
                file = OpenFileReadFlags(buf.toString(), FSFLAG_SEARCH_ADDONS)
                if (file != null) {
                    // save it out to an addon splash directory
                    val dlen = file.Length()
                    var data = ByteBuffer.allocate(dlen)
                    file.Read(data, dlen)
                    CloseFile(file)
                    idStr.snPrintf(buf, len, "guis/assets/splash/addon/%s.tga", mapname.toString())
                    WriteFile(buf.toString(), data, dlen)
                    data = null
                } else {
                    buf.append("guis/assets/splash/pdtempa".substring(0, len))
                }
            }
        }

        /*
         ===========
         idFileSystemLocal::FilenameCompare

         Ignore case and separator char distinctions
         ===========
         */
        override fun FilenameCompare(s1: String, s2: String): Boolean {
            // normalize '\\' and ':' to '/' before comparing, matching C++ behavior
            val n1 = s1.replace('\\', '/').replace(':', '/')
            val n2 = s2.replace('\\', '/').replace(':', '/')
            return n1.equals(n2, ignoreCase = true)
        }

        /*
         ====================
         idFileSystemLocal::ReplaceSeparators

         Fix things up differently for win/unix/mac
         ====================
         */
        private fun ReplaceSeparators(path: idStr, sep: Char = PATHSEPERATOR_CHAR) {
            path.data = path.data.replace('\\', sep).replace('/', sep)
        }

        /*
         ================
         idFileSystemLocal::HashFileName

         return a hash value for the filename
         ================
         */
        private fun HashFileName(fname: String): Long {
            var i: Int
            var hash: Long
            var letter: Char
            hash = 0
            i = 0
            while (i < fname.length) {
                letter = idStr.ToLower(fname[i])
                if (letter == '.') {
                    break // don't include extension
                }
                if (letter == '\\') {
                    letter = '/' // damn path names
                }
                hash += letter.code.toLong() * (i + 119)
                i++
            }
            hash = hash and (FILE_HASH_SIZE - 1L)
            return hash
        }

        /*
         ===============
         idFileSystemLocal::ListOSFiles

         call to the OS for a listing of files in an OS directory
         optionally, perform some caching of the entries
         ===============
         */
        private fun ListOSFiles(directory: String, extension: String, list: idStrList): Int {
            var extension = extension
            var list = list
            var i: Int
            var j: Int
            val ret: Int
            // no need, better to call it with empty string then null
//            if (null == extension) {
//                extension = ""
//            }
            if (!fs_caseSensitiveOS.GetBool()) {
                return Sys_ListFiles(directory, extension, list)
            }

            // try in cache
            i = dir_cache_index - 1
            while (i >= dir_cache_index - dir_cache_count) {
                j = (i + MAX_CACHED_DIRS) % MAX_CACHED_DIRS
                if (dir_cache[j].Matches(directory, extension)) {
                    if (fs_debug.GetInteger() != 0) {
                        //common.Printf( "idFileSystemLocal::ListOSFiles: cache hit: %s\n", directory );
                    }
                    list = dir_cache[j]
                    return list.size()
                }
                i--
            }
            if (fs_debug.GetInteger() != 0) {
                //common.Printf( "idFileSystemLocal::ListOSFiles: cache miss: %s\n", directory );
            }
            ret = Sys_ListFiles(directory, extension, list)
            if (ret == -1) {
                return -1
            }

            // push a new entry
            dir_cache[dir_cache_index].Init(directory, extension, list)
            dir_cache_index = (dir_cache_index + 1) % MAX_CACHED_DIRS
            if (dir_cache_count < MAX_CACHED_DIRS) {
                dir_cache_count++
            }
            return ret
        }

        /*
         ================
         idFileSystemLocal::OpenOSFile
         optional caseSensitiveName is set to case sensitive file name as found on disc (fs_caseSensitiveOS only)
         ================
         */
        private fun OpenOSFile(
            fileName: String,
            mode: String?,
            caseSensitiveName: idStr? = null /*= NULL*/
        ): FileChannel? {
            var i: Int
            var fp: Path?
            val fpath: idStr
            var entry: idStr
            val list = idStrList()

//if( __MWERKS__&&
// WIN32 ){
//	// some systems will let you fopen a directory
//	struct stat buf;
//	if ( stat( fileName, &buf ) != -1 && !S_ISREG(buf.st_mode) ) {
//		return NULL;
//	}
//}
            fp = try {
                Paths.get(fileName) //fp = fopen(fileName, mode);
            } catch (e: InvalidPathException) {
                //log something.
                Paths.get("/" + UUID.randomUUID())
            }
            if (Files.notExists(fp, LinkOption.NOFOLLOW_LINKS)
                && fs_caseSensitiveOS.GetBool()
            ) {
                fpath = idStr(fileName)
                fpath.StripFilename()
                fpath.StripTrailing(PATHSEPERATOR_CHAR)
                if (ListOSFiles(fpath.toString(), "", list) == -1) {
                    return null
                }
                i = 0
                while (i < list.size()) {
                    entry = idStr(fpath.toString() + PATHSEPERATOR_CHAR + list[i].toString())
                    if (0 == entry.Icmp(fileName)) {
                        fp = Paths.get(entry.toString()) //fp = fopen(entry, mode);
                        if (Files.exists(fp, LinkOption.NOFOLLOW_LINKS)) {
                            if (caseSensitiveName != null) {
                                caseSensitiveName.set(entry)
                                caseSensitiveName.StripPath()
                            }
                            if (fs_debug.GetInteger() != 0) {
                                idLib.common.Printf(
                                    "idFileSystemLocal::OpenFileRead: changed %s to %s\n",
                                    fileName,
                                    entry
                                )
                            }
                            break
                        } else {
                            // not supposed to happen if ListOSFiles is doing it's job correctly
                            idLib.common.Warning(
                                "idFileSystemLocal::OpenFileRead: fs_caseSensitiveOS 1 could not open %s",
                                entry
                            )
                        }
                    }
                    i++
                }
            } else if (caseSensitiveName != null) {
                caseSensitiveName.set(fileName)
                caseSensitiveName.StripPath()
            }
            try {
                //                return new FileInputStream(fp.toFile()).getChannel();
                return FileChannel.open(fp, TempDump.fopenOptions(mode))
            } catch (ex: NoSuchFileException) { //TODO:turn exceptions back on.
//                Logger.getLogger(FileSystem_h.class.getName()).log(Level.WARNING, null, ex);
            } catch (ex: IOException) {
                Logger.getLogger(FileSystem_h::class.java.name).log(Level.SEVERE, null, ex)
            }
            return null
        }

        private fun OpenOSFileCorrectName(path: idStr, mode: String?): FileChannel? {
            val caseName = idStr()
            val f = OpenOSFile(path.toString(), mode, caseName)
            if (f != null) {
                path.StripFilename()
                path.Append(PATHSEPERATOR_STR)
                path.Append(caseName)
            }
            return f
        }

        private fun DirectFileLength(o: FileChannel): Long {
            try {
                //            int pos;
//            int end;
//
//            pos = ftell(o);
//            fseek(o, 0, SEEK_END);
//            end = ftell(o);
//            fseek(o, pos, SEEK_SET);
                return o.size()
            } catch (ex: IOException) {
                Logger.getLogger(FileSystem_h::class.java.name).log(Level.SEVERE, null, ex)
            }
            return -1
        }

        fun CopyFile(src: idFile, toOSPath: String) {
            val f: FileChannel?
            val len: Int
            val buf: ByteBuffer?
            idLib.common.Printf("copy %s to %s\n", src.GetName(), toOSPath)
            src.Seek(0, fsOrigin_t.FS_SEEK_END)
            len = src.Tell()
            src.Seek(0, fsOrigin_t.FS_SEEK_SET)
            buf = ByteBuffer.allocate(len) //Mem_Alloc(len);
            if (src.Read(buf, len) != len) {
                idLib.common.FatalError("Short read in idFileSystemLocal::CopyFile()\n")
            }
            CreateOSPath(toOSPath)
            f = OpenOSFile(toOSPath, "wb")
            if (null == f) {
                idLib.common.Printf("could not create destination file\n")
                //                Heap.Mem_Free(buf);
                return
            }
            try {
                if (f.write(buf) != len) {
//            if (fwrite(buf, 1, len, f) != len) {
                    idLib.common.FatalError("Short write in idFileSystemLocal::CopyFile()\n")
                }
                f.close()
                //            Heap.Mem_Free(buf);
            } catch (ex: IOException) {
                Logger.getLogger(FileSystem_h::class.java.name).log(Level.SEVERE, null, ex)
            }
        }

        private fun AddUnique(name: String, list: idStrList, hashIndex: idHashIndex): Int {
            var i: Int
            val hashKey: Int
            hashKey = hashIndex.GenerateKey(name.toCharArray())
            i = hashIndex.First(hashKey)
            while (i >= 0) {
                if (list[i].Icmp(name) == 0) {
                    return i
                }
                i = hashIndex.Next(i)
            }
            i = list.add(idStr(name))
            hashIndex.Add(hashKey, i)
            return i
        }

        private fun GetExtensionList(extension: String, extensionList: idStrList) {
            var s: Int
            var e: Int
            val l: Int
            l = extension.length
            s = 0
            while (true) {
                e = idStr.FindChar(extension, '|', s, l)
                s = if (e != -1) {
                    extensionList.add(idStr(extension, s, e))
                    e + 1
                } else {
                    extensionList.add(idStr(extension, s, l))
                    break
                }
            }
        }

        /*
         ===============
         idFileSystemLocal::GetFileList

         Does not clear the list first so this can be used to progressively build a file list.
         When 'sort' is true only the new files added to the list are sorted.
         ===============
         */
        private fun GetFileList(
            relativePath: String,
            extensions: idStrList,
            list: idStrList,
            hashIndex: idHashIndex,
            fullRelativePath: Boolean,
            gamedir: String? /*= NULL*/
        ): Int {
            var search: searchpath_s?
            var buildBuffer: Array<fileInPack_s>
            var i: Int
            var j: Int
            var pathLength: Int
            var length: Int
            var name: String
            var pak: pack_t
            var work: idStr
            if (null == searchPaths) {
                idLib.common.FatalError("Filesystem call made without initialization\n")
            }
            if (0 == extensions.size()) {
                return 0
            }
            if (relativePath.isEmpty()) {
                return 0
            }
            pathLength = relativePath.length
            if (pathLength != 0) {
                pathLength++ // for the trailing '/'
            }

            // search through the path, one element at a time, adding to list
            search = searchPaths
            while (search != null) {
                if (search.dir != null) {
                    if (gamedir != null && !gamedir.isEmpty()) {
                        if (search.dir!!.gamedir.toString() != gamedir) {
                            search = search.next
                            continue
                        }
                    }
                    val sysFiles = idStrList()
                    var netpath: idStr
                    netpath =
                        idStr(BuildOSPath(search.dir!!.path.toString(), search.dir!!.gamedir.toString(), relativePath))
                    i = 0
                    while (i < extensions.size()) {


                        // scan for files in the filesystem
                        ListOSFiles(netpath.toString(), extensions[i].toString(), sysFiles)

                        // if we are searching for directories, remove . and ..
                        if (extensions[i].toString() == "/") { // && extensions.oGet(i).oGet(1) == 0) {//TODO:==0?????
                            sysFiles.remove(idStr("."))
                            sysFiles.remove(idStr(".."))
                        }
                        j = 0
                        while (j < sysFiles.size()) {

                            // unique the match
                            if (fullRelativePath) {
                                work = idStr(relativePath)
                                work.Append("/")
                                work.Append(sysFiles[j])
                                AddUnique(work.toString(), list, hashIndex)
                            } else {
                                AddUnique(sysFiles[j].toString(), list, hashIndex)
                            }
                            j++
                        }
                        i++
                    }
                } else if (search.pack != null) {
                    // look through all the pak file elements

                    // exclude any extra packs if we have server paks to search
                    if (serverPaks.Num() != 0) {
                        GetPackStatus(search.pack!!)
                        // FIX: C++ `!serverPaks.Find()` checks for NOT found (NULL = falsy).
                        // Kotlin Find() returns null when not found, not 0.
                        if (search.pack!!.pureStatus != pureStatus_t.PURE_NEVER && null == serverPaks.Find(search.pack!!)) {
                            search = search.next
                            continue  // not on the pure server pak list
                        }
                    }
                    pak = search.pack!!
                    buildBuffer = pak.buildBuffer
                    i = 0
                    while (i < pak.numfiles) {
                        length = buildBuffer[i].name.Length()

                        // if the name is not long anough to at least contain the path
                        if (length <= pathLength) {
                            i++
                            continue
                        }
                        name = buildBuffer[i].name.toString()

                        // check for a path match without the trailing '/'
                        if (pathLength > 0 && idStr.Icmpn(name, relativePath, pathLength - 1) != 0) {
                            i++
                            continue
                        }

                        // ensure we have a path, and not just a filename containing the path
                        if (name.length == pathLength || name[pathLength - 1] != '/') {
                            i++
                            continue
                        }

                        // make sure the file is not in a subdirectory
                        j = pathLength /*name.[j+1] != '\0'*/
                        while (j < name.length) {
                            if (name[j] == '/') {
                                break
                            }
                            j++
                        }
                        if (j + 1 < name.length) {
                            i++
                            continue
                        }

                        // check for extension match
                        j = 0
                        while (j < extensions.size()) {
                            if (length >= extensions[j].Length() && extensions[j]
                                    .Icmp(name.substring(length - extensions[j].Length())) == 0
                            ) {
                                break
                            }
                            j++
                        }
                        if (j >= extensions.size()) {
                            i++
                            continue
                        }

                        // unique the match
                        if (fullRelativePath) {
                            work = idStr(relativePath)
                            work.Append("/")
                            work.Append(name.substring(pathLength))
                            work.StripTrailing('/')
                            AddUnique(work.toString(), list, hashIndex)
                        } else {
                            work = idStr(name.substring(pathLength))
                            work.StripTrailing('/')
                            AddUnique(work.toString(), list, hashIndex)
                        }
                        i++
                    }
                }
                search = search.next
            }
            return list.size()
        }

        private fun GetFileListTree(
            relativePath: String,
            extensions: idStrList,
            list: idStrList,
            hashIndex: idHashIndex,
            gamedir: String? /*= NULL*/
        ): Int {
            var i: Int
            val slash = idStrList()
            val folders = idStrList(128)
            val folderHashIndex = idHashIndex(1024, 128)

            // recurse through the subdirectories
            slash.add(idStr("/"))
            GetFileList(relativePath, slash, folders, folderHashIndex, true, gamedir)
            i = 0
            while (i < folders.size()) {
                if (folders[i][0] == '.') {
                    i++
                    continue
                }
                if (folders[i].Icmp(relativePath) == 0) {
                    i++
                    continue
                }
                GetFileListTree(folders[i].toString(), extensions, list, hashIndex, gamedir)
                i++
            }

            // list files in the current directory
            GetFileList(relativePath, extensions, list, hashIndex, true, gamedir)
            return list.size()
        }

        /*
         ================
         idFileSystemLocal::AddGameDirectory

         Sets gameFolder, adds the directory to the head of the search paths, then loads any pk4 files.
         ================
         */
        private fun AddGameDirectory(path: String, dir: String) {
            var i: Int
            var search: searchpath_s?
            var pak: pack_t?
            var pakfile: idStr
            val pakfiles = idStrList()

            // check if the search path already exists
            search = searchPaths
            while (search != null) {

                // if this element is a pak file
                if (null == search.dir) {
                    search = search.next
                    continue
                }
                if (search.dir!!.path.Cmp(path) == 0 && search.dir!!.gamedir.Cmp(dir) == 0) {
                    return
                }
                search = search.next
            }
            gameFolder.set(dir)

            //
            // add the directory to the search path
            //
            search = searchpath_s()
            search.dir = directory_t()
            search.pack = null
            search.dir!!.path.set(path)
            search.dir!!.gamedir.set(dir)
            search.next = searchPaths
            searchPaths = search

            // find all pak files in this directory
            pakfile = idStr(BuildOSPath(path, dir, ""))
            //            pakfile.oSet(pakfile.Length() - 1, (char) 0);	// strip the trailing slash
            ListOSFiles(pakfile.toString(), ".pk4", pakfiles)

            // sort them so that later alphabetic matches override
            // earlier ones. This makes pak1.pk4 override pak0.pk4
            pakfiles.sort()
            i = 0
            while (i < pakfiles.size()) {
                pakfile = idStr(BuildOSPath(path, dir, pakfiles[i].toString()))
                pak = LoadZipFile(pakfile.toString())
                if (null == pak) {
                    i++
                    continue
                }
                // insert the pak after the directory it comes from
                search = searchpath_s()
                search.dir = null
                search.pack = pak
                search.next = searchPaths!!.next
                searchPaths!!.next = search
                idLib.common.Printf("Loaded pk4 %s with checksum 0x%x\n", pakfile.toString(), pak.checksum)
                i++
            }
        }

        /*
         ================
         idFileSystemLocal::SetupGameDirectories

         Takes care of the correct search order.
         ================
         */
        private fun SetupGameDirectories(gameName: String) {
            // setup cdpath
            if (!fs_cdpath.GetString()!!.isEmpty()) {
                AddGameDirectory(fs_cdpath.GetString()!!, gameName)
            }

            // setup basepath
            if (!fs_basepath.GetString()!!.isEmpty()) {
                AddGameDirectory(fs_basepath.GetString()!!, gameName)
            }

            // setup devpath
            if (!fs_devpath.GetString()!!.isEmpty()) {
                AddGameDirectory(fs_devpath.GetString()!!, gameName)
            }

            // setup savepath
            if (!fs_savepath.GetString()!!.isEmpty()) {
                AddGameDirectory(fs_savepath.GetString()!!, gameName)
            }
        }

        private fun Startup() {
            var search: searchpath_s?
            var i: Int
            var pak: pack_t
            var addon_index: Int
            idLib.common.Printf("----- Initializing File System -----\n")
            if (restartChecksums.Num() != 0) {
                idLib.common.Printf("restarting in pure mode with %d pak files\n", restartChecksums.Num())
            }
            if (addonChecksums.Num() != 0) {
                idLib.common.Printf(
                    "restarting filesystem with %d addon pak file(s) to include\n",
                    addonChecksums.Num()
                )
            }
            SetupGameDirectories(Licensee.BASE_GAMEDIR)

            // fs_game_base override
            if (!fs_game_base.GetString()!!.isEmpty() && idStr.Icmp(
                    fs_game_base.GetString()!!,
                    Licensee.BASE_GAMEDIR
                ) != 0
            ) {
                SetupGameDirectories(fs_game_base.GetString()!!)
            }

            // fs_game override
            if (!fs_game.GetString()!!.isEmpty() && idStr.Icmp(
                    fs_game.GetString()!!,
                    Licensee.BASE_GAMEDIR
                ) != 0 && idStr.Icmp(fs_game.GetString()!!, fs_game_base.GetString()!!) != 0
            ) {
                SetupGameDirectories(fs_game.GetString()!!)
            }

            // currently all addons are in the search list - deal with filtering out and dependencies now
            // scan through and deal with dependencies
            search = searchPaths
            while (search != null) {
                if (null == search.pack || !search.pack!!.addon) {
                    search = search.next
                    continue
                }
                pak = search.pack!!
                if (fs_searchAddons.GetBool()) {
                    // when we have fs_searchAddons on we should never have addonChecksums
                    assert(0 == addonChecksums.Num())
                    pak.addon_search = true
                    search = search.next
                    continue
                }
                addon_index = addonChecksums.FindIndex(pak.checksum)
                if (addon_index >= 0) {
                    assert(
                        !pak.addon_search // any pak getting flagged as addon_search should also have been removed from addonChecksums already
                    )
                    pak.addon_search = true
                    addonChecksums.RemoveIndex(addon_index)
                    FollowAddonDependencies(pak)
                }
                search = search.next
            }

            // now scan to filter out addons not marked addon_search
            search = searchPaths
            while (search != null) {
                if (null == search.pack || !search.pack!!.addon) {
                    search = search.next
                    continue
                }
                assert(null == search.dir)
                pak = search.pack!!
                if (pak.addon_search) {
                    idLib.common.Printf(
                        "Addon pk4 %s with checksum 0x%x is on the search list\n",
                        pak.pakFilename.toString(),
                        pak.checksum
                    )
                    search = search.next
                } else {
                    // remove from search list, put in addons list
                    val paksearch = search
                    search = search.next
                    paksearch.next = addonPaks
                    addonPaks = paksearch
                    idLib.common.Printf(
                        "Addon pk4 %s with checksum 0x%x is on addon list\n",
                        pak.pakFilename.toString(),
                        pak.checksum
                    )
                }
            }
            assert(0 == addonChecksums.Num())
            addonChecksums.Clear() // just in case
            if (restartChecksums.Num() != 0) {
                search = searchPaths
                while (search != null) {
                    if (null == search.pack) {
                        search = search.next
                        continue
                    }
                    if (restartChecksums.FindIndex(search.pack!!.checksum).also { i = it } != -1) {
                        if (i == 0) {
                            // this pak is the next one in the pure search order
                            serverPaks.Append(search.pack!!)
                            restartChecksums.RemoveIndex(0)
                            if (0 == restartChecksums.Num()) {
                                break // early out, we're done
                            }
                            search = search.next
                            continue
                        } else {
                            // this pak will be on the pure list, but order is not right yet
                            var aux: searchpath_s?
                            aux = search.next
                            if (null == aux) {
                                // last of the list can't be swapped back
                                if (fs_debug.GetBool()) {
                                    idLib.common.Printf(
                                        "found pure checksum %x at index %d, but the end of search path is reached\n",
                                        search.pack!!.checksum,
                                        i
                                    )
                                    val checks = idStr()
                                    checks.Clear()
                                    i = 0
                                    while (i < serverPaks.Num()) {
                                        checks.Append(Str.va("%p ", serverPaks[i]))
                                        i++
                                    }
                                    idLib.common.Printf("%d pure paks - %s \n", serverPaks.Num(), checks.toString())
                                    checks.Clear()
                                    i = 0
                                    while (i < restartChecksums.Num()) {
                                        checks.Append(Str.va("%x ", restartChecksums[i]))
                                        i++
                                    }
                                    idLib.common.Printf(
                                        "%d paks left - %s\n",
                                        restartChecksums.Num(),
                                        checks.toString()
                                    )
                                }
                                idLib.common.FatalError("Failed to restart with pure mode restrictions for server connect")
                            }
                            // put this search path at the end of the list
                            var search_end: searchpath_s?
                            search_end = search.next
                            while (search_end!!.next != null) {
                                search_end = search_end.next
                            }
                            search_end.next = search
                            search = search.next
                            search_end.next!!.next = null
                            continue
                        }
                    }
                    // this pak is not on the pure list
                    search = search.next
                }
                // the list must be empty
                if (restartChecksums.Num() != 0) {
                    if (fs_debug.GetBool()) {
                        val checks = idStr()
                        checks.Clear()
                        i = 0
                        while (i < serverPaks.Num()) {
                            checks.Append(Str.va("%p ", serverPaks[i]))
                            i++
                        }
                        idLib.common.Printf("%d pure paks - %s \n", serverPaks.Num(), checks)
                        checks.Clear()
                        i = 0
                        while (i < restartChecksums.Num()) {
                            checks.Append(Str.va("%x ", restartChecksums[i]))
                            i++
                        }
                        idLib.common.Printf("%d paks left - %s\n", restartChecksums.Num(), checks)
                    }
                    idLib.common.FatalError("Failed to restart with pure mode restrictions for server connect")
                }
                // also the game pak checksum
                // we could check if the game pak is actually present, but we would not be restarting if there wasn't one @ first pure check
                gamePakChecksum = restartGamePakChecksum
            }

            // add our commands
            CmdSystem.cmdSystem.AddCommand(
                "dir",
                Dir_f.getInstance(),
                CmdSystem.CMD_FL_SYSTEM,
                "lists a folder",
                ArgCompletion_FileName.getInstance()
            )
            CmdSystem.cmdSystem.AddCommand(
                "dirtree",
                DirTree_f.getInstance(),
                CmdSystem.CMD_FL_SYSTEM,
                "lists a folder with subfolders"
            )
            CmdSystem.cmdSystem.AddCommand("path", Path_f.getInstance(), CmdSystem.CMD_FL_SYSTEM, "lists search paths")
            CmdSystem.cmdSystem.AddCommand(
                "touchFile",
                TouchFile_f.getInstance(),
                CmdSystem.CMD_FL_SYSTEM,
                "touches a file"
            )
            CmdSystem.cmdSystem.AddCommand(
                "touchFileList",
                TouchFileList_f.getInstance(),
                CmdSystem.CMD_FL_SYSTEM,
                "touches a list of files"
            )

            // print the current search paths
            Path_f.getInstance().run(CmdArgs.idCmdArgs())
            idLib.common.Printf("file system initialized.\n")
            idLib.common.Printf("--------------------------------------\n")
        }

        /*
         ===================
         idFileSystemLocal::SetRestrictions

         Looks for product keys and restricts media add on ability
         if the full version is not found
         ===================
         */
        private fun SetRestrictions() {
            if (ID_DEMO_BUILD) {
                idLib.common.Printf("\nRunning in restricted demo mode.\n\n")
                // make sure that the pak file has the header checksum we expect
                var search: searchpath_s?
                search = searchPaths
                while (search != null) {
                    if (search.pack != null) {
                        // a tiny attempt to keep the checksum from being scannable from the exe
                        if ((search.pack!!.checksum xor -0x7bd97bca).toLong() != DemoChecksum.DEMO_PAK_CHECKSUM xor -0x7bd97bca) {
                            idLib.common.FatalError(
                                "Corrupted %s: 0x%x",
                                search.pack!!.pakFilename.toString(),
                                search.pack!!.checksum
                            )
                        }
                    }
                    search = search.next
                }
                CVarSystem.cvarSystem.SetCVarBool("fs_restrict", true)
            }
        }

        private fun FileAllowedFromDir(path: String?): Boolean {
            if (path == null || path.isEmpty()) {
                return false
            }
            if ( // for config files
                path.endsWith(".cfg") ||  // for journal files
                path.endsWith(".dat") ||  // dynamic modules are handled a different way for pure
                path.endsWith("dll") ||
                path.endsWith(".so") ||
                path.endsWith(".dylib") ||
                path.endsWith(".scriptcfg") ||  // configuration script, such as map cycle
                ID_PURE_ALLOWDDS && path.endsWith("dds")
            ) {
                // note: cd and xp keys, as well as config.spec are opened through an explicit OS path and don't hit this
                return true
            }
            // savegames
            if (path.startsWith("savegames")
                && (path.endsWith(".tga") || path.endsWith(".txt") || path.endsWith(".save"))
            ) {
                return true
            }
            // screen shots
            if (path.startsWith("screenshots") && path.endsWith(".tga")) {
                return true
            }
            // objective tgas
            return if (path.startsWith("maps/game")
                && path.endsWith(".tga")
            ) {
                true
            } else path.startsWith("guis/assets/splash/addon")
                    && path.endsWith(".tga")
            // splash screens extracted from addons
        }

        private fun GetPackForChecksum(checksum: Int, searchAddons: Boolean = false /*= false*/): pack_t? {
            var search: searchpath_s?
            search = searchPaths
            while (search != null) {
                if (null == search.pack) {
                    search = search.next
                    continue
                }
                if (search.pack!!.checksum == checksum) {
                    return search.pack
                }
                search = search.next
            }
            if (searchAddons) {
                search = addonPaks
                while (search != null) {
                    assert(search.pack != null && search.pack!!.addon)
                    if (search.pack!!.checksum == checksum) {
                        return search.pack
                    }
                    search = search.next
                }
            }
            return null
        }

        private fun FindPakForFileChecksum(
            relativePath: String,
            findChecksum: IntArray,
            bReference: Boolean
        ): pack_t? {
            var search: searchpath_s?
            var pak: pack_t
            var pakFile: fileInPack_s?
            val hash: Int
            assert(0 == serverPaks.Num())
            hash = HashFileName(relativePath).toInt()
            search = searchPaths
            while (search != null) {
                if (search.pack != null && search.pack!!.hashTable[hash] != null) {
                    pak = search.pack!!
                    pakFile = pak.hashTable[hash]
                    while (pakFile != null) {
                        if (FilenameCompare(pakFile.name.toString(), relativePath)) {
                            val file = ReadFileFromZip(pak, pakFile, relativePath)
                            if (findChecksum[0] == GetFileChecksum(file)) {
                                if (fs_debug.GetBool()) {
                                    idLib.common.Printf(
                                        "found '%s' with checksum 0x%x in pak '%s'\n",
                                        relativePath,
                                        findChecksum[0],
                                        pak.pakFilename.toString()
                                    )
                                }
                                if (bReference) {
                                    pak.referenced = true
                                    // FIXME: use dependencies for pak references
                                }
                                CloseFile(file)
                                return pak
                            } else if (fs_debug.GetBool()) {
                                idLib.common.Printf(
                                    "'%s' in pak '%s' has != checksum %x\n",
                                    relativePath,
                                    pak.pakFilename,
                                    GetFileChecksum(file)
                                )
                            }
                            CloseFile(file)
                        }
                        pakFile = pakFile.next
                    }
                }
                search = search.next
            }
            if (fs_debug.GetBool()) {
                idLib.common.Printf("no pak file found for '%s' checksumed %x\n", relativePath, findChecksum[0])
            }
            return null
        }

        //							// some files can be obtained from directories without compromising si_pure
        private fun LoadZipFile(zipfile: String): pack_t? {
            val buildBuffer: Array<fileInPack_s>
            val pack: pack_t
            val uf: ZipFile
            //            int err;
//            unz_global_info gi;
            var filename_inzip: String //= new char[MAX_ZIPPED_FILE_NAME];
            //            unz_file_info file_info;
            var i: Int
            var hash: Long
            var fs_numHeaderLongs: Int
            val fs_headerLongs: IntArray
            val f: FileChannel?
            val len: Int
            val confHash: Int
            var pakFile: fileInPack_s?
            f = OpenOSFile(zipfile, "rb")
            if (null == f) {
                return null
            }
            try {
                //            fseek(f, 0, SEEK_END);
                len = f.size().toInt()
                f.close()
                fs_numHeaderLongs = 0
                uf = ZipFile(zipfile)

//            err = unzGetGlobalInfo(uf, gi);
//
//            if (err != UNZ_OK) {
//                return null;
//            }
//
                buildBuffer = Array(uf.size()) { fileInPack_s() } //int) gi.number_entry];
                pack = pack_t()
                i = 0
                while (i < FILE_HASH_SIZE) {
                    pack.hashTable[i] = null
                    i++
                }
                pack.pakFilename = idStr(zipfile)
                pack.handle = uf
                pack.numfiles = uf.size() //gi.number_entry;
                pack.buildBuffer = buildBuffer
                pack.referenced = false
                pack.binary = binaryStatus_t.BINARY_UNKNOWN
                pack.addon = false
                pack.addon_search = false
                pack.addon_info = null
                pack.pureStatus = pureStatus_t.PURE_UNKNOWN
                pack.isNew = false
                pack.length = len

//            unzGoToFirstFile(uf);
                fs_headerLongs =
                    IntArray(uf.size()) // gi.number_entry];//Mem_ClearedAlloc(gi.number_entry sizeof(int));
                val entries = uf.entries()
                i = 0
                while (i < uf.size() /*gi.number_entry*/) {

                    // go to the next file in the zip
                    val entry = entries.nextElement()
                    //                err = unzGetCurrentFileInfo(uf, file_info, filename_inzip, sizeof(filename_inzip), null, 0, null, 0);
//                if (err != UNZ_OK) {
//                    break;
//                }
//                if (file_info.uncompressed_size > 0) {
//                    fs_headerLongs[fs_numHeaderLongs++] = LittleLong(file_info.crc);
//                }
                    filename_inzip = entry.name
                    if (entry.size > 0) {
                        fs_headerLongs[fs_numHeaderLongs++] = LittleLong(entry.crc)
                    }
                    hash = HashFileName(filename_inzip)
                    buildBuffer[i] = fileInPack_s()
                    buildBuffer[i].name = idStr(filename_inzip)
                    buildBuffer[i].name.ToLower()
                    buildBuffer[i].name.BackSlashesToSlashes()
                    // store the file position in the zip
//                unzGetCurrentFileInfoPosition(uf, buildBuffer[i].pos);
                    buildBuffer[i].pos = i
                    // add the file to the hash
                    buildBuffer[i].next = pack.hashTable[hash.toInt()]
                    pack.hashTable[hash.toInt()] = buildBuffer[i]
                    buildBuffer[i].entry = entry //TODO:remove all the other shit
                    i++
                }

                // check if this is an addon pak
                pack.addon = false
                confHash = HashFileName(ADDON_CONFIG).toInt()
                pakFile = pack.hashTable[confHash]
                while (pakFile != null) {
                    if (FilenameCompare(pakFile.name.toString(), ADDON_CONFIG)) {
                        pack.addon = true
                        val file = ReadFileFromZip(pack, pakFile, ADDON_CONFIG)
                        // may be just an empty file if you don't bother about the mapDef
                        if (file != null && file.Length() != 0) {
                            val buf: ByteBuffer?
                            buf = ByteBuffer.allocate(file.Length() + 1)
                            file.Read( /*(void *)*/buf, file.Length())
                            buf.put(file.Length(), '\u0000'.code.toByte())
                            pack.addon_info = ParseAddonDef(String(buf.array()), file.Length())
                            //				delete[] buf;
                        }
                        file.let { CloseFile(it) }
                        break
                    }
                    pakFile = pakFile.next
                }
                pack.checksum = MD4_BlockChecksum(fs_headerLongs, fs_numHeaderLongs * 4).toInt()
                pack.checksum = LittleLong(pack.checksum)

//            Mem_Free(fs_headerLongs);
                return pack
            } catch (ex: IOException) {
                Logger.getLogger(FileSystem_h::class.java.name).log(Level.SEVERE, null, ex)
            }
            return null
        }

        // searches all the paks, no pure check
        private fun ReadFileFromZip(pak: pack_t, pakFile: fileInPack_s, relativePath: String): idFile_InZip {
            val fp: File
            val file = idFile_InZip()

            // open a new file on the pakfile
            fp = File(pak.pakFilename.toString()) //TODO: check this shit
            if (!fp.exists()) {
                idLib.common.FatalError("Couldn't reopen %s", pak.pakFilename.toString())
            }
            file.z = pakFile.entry!!
            file.name.set(relativePath)
            file.fullPath.set(pak.pakFilename)
            file.zipFilePos = pakFile.pos
            file.fileSize = pakFile.entry!!.size.toInt()
            return file
        }

        private fun GetFileChecksum(file: idFile): Int {
            val len: Int
            val ret: Int
            val buf: ByteBuffer?
            file.Seek(0, fsOrigin_t.FS_SEEK_END)
            len = file.Tell()
            file.Seek(0, fsOrigin_t.FS_SEEK_SET)
            buf = ByteBuffer.allocate(len)
            if (file.Read(buf, len) != len) {
                idLib.common.FatalError("Short read in idFileSystemLocal::GetFileChecksum()\n")
            }
            ret = 0 //new BigInteger(MD4_BlockChecksum(buf, len)).intValue();
            //            Mem_Free(buf);
            return ret
        }

        // searches all the paks, no pure check
        private fun GetPackStatus(pak: pack_t): pureStatus_t {
            var i: Int
            var l: Int
            var hashindex: Int
            var file: fileInPack_s?
            var abrt: Boolean
            val name = idStr()
            if (pak.pureStatus != pureStatus_t.PURE_UNKNOWN) {
                return pak.pureStatus
            }

            // check content for PURE_NEVER
            i = 0
            hashindex = 0
            while (hashindex < FILE_HASH_SIZE) {
                abrt = false
                // FIX: C++ just assigns `file = pak->hashTable[hashindex]` (a local pointer).
                // The Kotlin code was doing `pak.buildBuffer[hashindex] = pak.hashTable[hashindex]!!`
                // which crashes with NPE when hashTable entry is null (most entries are null).
                file = pak.hashTable[hashindex]
                while (file != null) {
                    abrt = true
                    l = file.name.Length()
                    var j = 0
                    while (pureExclusions[j].func != null) {
                        if (pureExclusions[j].func!!.run(pureExclusions[j], l, file.name)) {
                            abrt = false
                            break
                        }
                        j++
                    }
                    if (abrt) {
                        idLib.common.DPrintf(
                            "pak '%s' candidate for pure: '%s'\n",
                            pak.pakFilename.toString(),
                            file.name.toString()
                        )
                        break
                    }
                    file =  /*pak.buildBuffer =*/file.next //TODO:check this assignment.
                    i++
                }
                if (abrt) {
                    break
                }
                hashindex++
            }
            if (i == pak.numfiles) {
                pak.pureStatus = pureStatus_t.PURE_NEVER
                return pureStatus_t.PURE_NEVER
            }

            // check pak name for PURE_ALWAYS
            pak.pakFilename.ExtractFileName(name)
            if (0 == name.IcmpPrefixPath("pak")) {
                pak.pureStatus = pureStatus_t.PURE_ALWAYS
                return pureStatus_t.PURE_ALWAYS
            }
            pak.pureStatus = pureStatus_t.PURE_NEUTRAL
            return pureStatus_t.PURE_NEUTRAL
        }

        private fun ParseAddonDef(buf: String, len: Int): addonInfo_t? {
            val src = idLexer()
            val token = idToken()
            val token2 = idToken()
            val info: addonInfo_t
            src.LoadMemory(buf, len, "<addon.conf>")
            src.SetFlags(DeclManager.DECL_LEXER_FLAGS)
            if (!src.SkipUntilString("addonDef")) {
                src.Warning("ParseAddonDef: no addonDef")
                return null
            }
            if (!src.ReadToken(token)) {
                src.Warning("Expected {")
                return null
            }
            info = addonInfo_t()
            // read addonDef
            while (true) {
                if (!src.ReadToken(token)) {
//			delete info;
                    return null
                }
                // FIX: C++ `!token.Icmp("}")` returns true when token IS "}".
                // Kotlin had `!= "}"` which is the opposite — breaks immediately on non-"}" tokens.
                if (token.toString() == "}") {
                    break
                }
                if (token.type != Token.TT_STRING) {
                    src.Warning("Expected quoted string, but found '%s'", token.toString())
                    //			delete info;
                    return null
                }
                var checksum: Int

                // FIX: C++ uses sscanf with "%x" format to parse hex checksums.
                // Kotlin had `String.format("%x", token).toInt()` which is nonsensical.
                try {
                    checksum = java.lang.Long.decode(token.toString()).toInt()
                } catch (e: NumberFormatException) {
                    src.Warning("Could not parse checksum '%s'", token.toString())
                    //			delete info;
                    return null
                }
                info.depends.Append(checksum)
            }
            // read any number of mapDef entries
            while (true) {
                if (!src.SkipUntilString("mapDef")) {
                    return info
                }
                if (!src.ReadToken(token)) {
                    src.Warning("Expected map path")
                    info.mapDecls.DeleteContents(true)
                    //			delete info;
                    return null
                }
                val dict = idDict()
                dict.Set("path", token.toString())
                if (!src.ReadToken(token)) {
                    src.Warning("Expected {")
                    info.mapDecls.DeleteContents(true)
                    //			delete dict;
//			delete info;
                    return null
                }
                while (true) {
                    if (!src.ReadToken(token)) {
                        break
                    }
                    // FIX: Same inverted comparison as above — C++ `!token.Icmp("}")` means equal
                    if (token.toString() == "}") {
                        break
                    }
                    if (token.type != Token.TT_STRING) {
                        src.Warning("Expected quoted string, but found '%s'", token.toString())
                        info.mapDecls.DeleteContents(true)
                        //				delete dict;
//				delete info;
                        return null
                    }
                    if (!src.ReadToken(token2)) {
                        src.Warning("Unexpected end of file")
                        info.mapDecls.DeleteContents(true)
                        //				delete dict;
//				delete info;
                        return null
                    }
                    if (dict.FindKey(token.toString()) != null) {
                        src.Warning("'%s' already defined", token.toString())
                    }
                    dict.Set(token, token2)
                }
                info.mapDecls.Append(dict)
            }
            //            assert (false);
//            return null;
        }

        private fun FollowAddonDependencies(pak: pack_t) {
            assert(pak != null)
            if (null == pak.addon_info || 0 == pak.addon_info!!.depends.Num()) {
                return
            }
            var i: Int
            val num = pak.addon_info!!.depends.Num()
            i = 0
            while (i < num) {
                val deppak = GetPackForChecksum(pak.addon_info!!.depends[i], true)
                if (deppak != null) {
                    // make sure it hasn't been marked for search already
                    if (!deppak.addon_search) {
                        // must clean addonChecksums as we go
                        val addon_index = addonChecksums.FindIndex(deppak.checksum)
                        if (addon_index >= 0) {
                            addonChecksums.RemoveIndex(addon_index)
                        }
                        deppak.addon_search = true
                        idLib.common.Printf(
                            "Addon pk4 %s 0x%x depends on pak %s 0x%x, will be searched\n",
                            pak.pakFilename.toString(),
                            pak.checksum,
                            deppak.pakFilename.toString(),
                            deppak.checksum
                        )
                        FollowAddonDependencies(deppak)
                    }
                } else {
                    idLib.common.Printf(
                        "Addon pk4 %s 0x%x depends on unknown pak 0x%x\n",
                        pak.pakFilename.toString(),
                        pak.checksum,
                        pak.addon_info!!.depends[i]
                    )
                }
                i++
            }
        }

        class Dir_f private constructor() : cmdFunction_t() {
            override fun run(args: CmdArgs.idCmdArgs?) {
                val relativePath: idStr
                val extension: idStr
                val fileList: idFileList?
                var i: Int
                if (args!!.Argc() < 2 || args.Argc() > 3) {
                    idLib.common.Printf("usage: dir <directory> [extension]\n")
                    return
                }
                if (args.Argc() == 2) {
                    relativePath = idStr(args.Argv(1))
                    extension = idStr()
                } else {
                    relativePath = idStr(args.Argv(1))
                    extension = idStr(args.Argv(2))
                    if (extension[0] != '.') {
                        idLib.common.Warning("extension should have a leading dot")
                    }
                }
                relativePath.BackSlashesToSlashes()
                relativePath.StripTrailing('/')
                idLib.common.Printf("Listing of %s/*%s\n", relativePath.toString(), extension.toString())
                idLib.common.Printf("---------------\n")
                fileList = fileSystemLocal.ListFiles(relativePath.toString(), extension.toString())
                i = 0
                while (i < fileList.GetNumFiles()) {
                    idLib.common.Printf("%s\n", fileList.GetFile(i))
                    i++
                }
                idLib.common.Printf("%d files\n", fileList.list.size())
                fileSystemLocal.FreeFileList(fileList)
            }

            companion object {
                private val instance: cmdFunction_t = Dir_f()
                fun getInstance(): cmdFunction_t {
                    return instance
                }
            }
        }

        class DirTree_f private constructor() : cmdFunction_t() {
            override fun run(args: CmdArgs.idCmdArgs?) {
                val relativePath: idStr
                val extension: idStr
                val fileList: idFileList?
                var i: Int
                if (args!!.Argc() < 2 || args.Argc() > 3) {
                    idLib.common.Printf("usage: dirtree <directory> [extension]\n")
                    return
                }
                if (args.Argc() == 2) {
                    relativePath = idStr(args.Argv(1))
                    extension = idStr()
                } else {
                    relativePath = idStr(args.Argv(1))
                    extension = idStr(args.Argv(2))
                    if (extension[0] != '.') {
                        idLib.common.Warning("extension should have a leading dot")
                    }
                }
                relativePath.BackSlashesToSlashes()
                relativePath.StripTrailing('/')
                idLib.common.Printf("Listing of %s/*%s /s\n", relativePath.toString(), extension.toString())
                idLib.common.Printf("---------------\n")
                fileList = fileSystemLocal.ListFilesTree(relativePath.toString(), extension.toString())
                i = 0
                while (i < fileList.GetNumFiles()) {
                    idLib.common.Printf("%s\n", fileList.GetFile(i))
                    i++
                }
                idLib.common.Printf(
                    """
    %d files
    ${fileList.list.size()}
    """.trimIndent()
                )
                fileSystemLocal.FreeFileList(fileList)
            }

            companion object {
                private val instance: cmdFunction_t = DirTree_f()
                fun getInstance(): cmdFunction_t {
                    return instance
                }
            }
        }

        class Path_f private constructor() : cmdFunction_t() {
            override fun run(args: CmdArgs.idCmdArgs?) {
                var sp: searchpath_s?
                var i: Int
                var status: String // = "";
                idLib.common.Printf("Current search path:\n")
                sp = fileSystemLocal.searchPaths
                while (sp != null) {
                    if (sp.pack != null) {
                        if (Common.com_developer.GetBool()) {
                            status = String.format(
                                "%s (%d files - 0x%x %s",
                                sp.pack!!.pakFilename,
                                sp.pack!!.numfiles,
                                sp.pack!!.checksum,
                                if (sp.pack!!.referenced) "referenced" else "not referenced"
                            )
                            status += if (sp.pack!!.addon) {
                                " - addon)\n"
                            } else {
                                ")\n"
                            }
                            idLib.common.Printf("%s", status)
                        } else {
                            idLib.common.Printf("%s (%d files)\n", sp.pack!!.pakFilename, sp.pack!!.numfiles)
                        }
                        if (fileSystemLocal.serverPaks.Num() != 0) {
                            if (fileSystemLocal.serverPaks.Find(sp.pack!!) != 0) {
                                idLib.common.Printf("    on the pure list\n")
                            } else {
                                idLib.common.Printf("    not on the pure list\n")
                            }
                        }
                    } else {
                        idLib.common.Printf("%s/%s\n", sp.dir!!.path, sp.dir!!.gamedir)
                    }
                    sp = sp.next
                }
                idLib.common.Printf(
                    "game DLL: 0x%x in pak: 0x%x\n",
                    fileSystemLocal.gameDLLChecksum,
                    fileSystemLocal.gamePakChecksum
                )
                i = 0
                while (i < MAX_GAME_OS) {
                    if (fileSystemLocal.gamePakForOS[i] != 0) {
                        idLib.common.Printf("OS %d - pak 0x%x\n", i, fileSystemLocal.gamePakForOS[i])
                    }
                    i++
                }
                // show addon packs that are *not* in the search lists
                idLib.common.Printf("Addon pk4s:\n")
                sp = fileSystemLocal.addonPaks
                while (sp != null) {
                    if (Common.com_developer.GetBool()) {
                        idLib.common.Printf(
                            "%s (%d files - 0x%x)\n",
                            sp.pack!!.pakFilename,
                            sp.pack!!.numfiles,
                            sp.pack!!.checksum
                        )
                    } else {
                        idLib.common.Printf("%s (%d files)\n", sp.pack!!.pakFilename, sp.pack!!.numfiles)
                    }
                    sp = sp.next
                }
            }

            companion object {
                private val instance: cmdFunction_t = Path_f()
                fun getInstance(): cmdFunction_t {
                    return instance
                }
            }
        }

        /*
         ============
         idFileSystemLocal::TouchFile_f

         The only purpose of this function is to allow game script files to copy
         arbitrary files furing an "fs_copyfiles 1" run.
         ============
         */
        class TouchFile_f private constructor() : cmdFunction_t() {
            override fun run(args: CmdArgs.idCmdArgs?) {
                val f: idFile?
                if (args!!.Argc() != 2) {
                    idLib.common.Printf("Usage: touchFile <file>\n")
                    return
                }
                f = fileSystemLocal.OpenFileRead(args.Argv(1))
                if (f != null) {
                    fileSystemLocal.CloseFile(f)
                }
            }

            companion object {
                private val instance: cmdFunction_t = TouchFile_f()
                fun getInstance(): cmdFunction_t {
                    return instance
                }
            }
        }

        //
        /*
         ============
         idFileSystemLocal::TouchFileList_f

         Takes a text file and touches every file in it, use one file per line.
         ============
         */
        class TouchFileList_f private constructor() : cmdFunction_t() {
            override fun run(args: CmdArgs.idCmdArgs?) {
                if (args!!.Argc() != 2) {
                    idLib.common.Printf("Usage: touchFileList <filename>\n")
                    return
                }
                val buffer = arrayOfNulls<ByteBuffer>(1)
                val src =
                    idParser(Lexer.LEXFL_NOFATALERRORS or Lexer.LEXFL_NOSTRINGCONCAT or Lexer.LEXFL_ALLOWMULTICHARLITERALS or Lexer.LEXFL_ALLOWBACKSLASHSTRINGCONCAT)
                if (fileSystem.ReadFile(args.Argv(1), buffer, null) != 0 && buffer[0] != null) {
                    src.LoadMemory(String(buffer[0]!!.array()), buffer[0]!!.capacity(), args.Argv(1))
                    if (src.IsLoaded()) {
                        val token = idToken()
                        while (src.ReadToken(token)) {
                            idLib.common.Printf("%s\n", token.toString())
                            Session.session.UpdateScreen()
                            val f = fileSystemLocal.OpenFileRead(token.toString())
                            if (f != null) {
                                fileSystemLocal.CloseFile(f)
                            }
                        }
                    }
                }
            }

            companion object {
                private val instance: cmdFunction_t = TouchFileList_f()
                fun getInstance(): cmdFunction_t {
                    return instance
                }
            }
        }

        companion object {
            /*
             ==================
             BackgroundDownloadThreadFn
             Matches C++ BackgroundDownloadThread() in FileSystem.cpp
             ==================
             */
            fun BackgroundDownloadThreadFn(@Suppress("UNUSED_PARAMETER") arg: Any?): Int {
                while (!fileSystemLocal.backgroundDownloadExit) {
                    Sys_EnterCriticalSection()
                    val bgl = fileSystemLocal.backgroundDownloads
                    if (bgl == null) {
                        Sys_LeaveCriticalSection()
                        win_main.Sys_WaitForEvent()
                        continue
                    }
                    // remove from list
                    fileSystemLocal.backgroundDownloads = bgl.next
                    Sys_LeaveCriticalSection()

                    bgl.next = null

                    if (bgl.opcode == dlType_t.DLTYPE_FILE) {
                        if (bgl.f is idFile_Permanent) {
                            val filePerm = bgl.f as idFile_Permanent
                            filePerm.Seek(bgl.file.position.toLong(), fsOrigin_t.FS_SEEK_SET)
                            filePerm.Read(bgl.file.buffer!!, bgl.file.length)
                        }
                        bgl.completed = true
                    } else {
                        // DLTYPE_URL — no curl equivalent in Kotlin
                        bgl.url.status = dlStatus_t.DL_FAILED
                        bgl.completed = true
                    }
                }
                return 0
            }

            const val MAX_DESCRIPTION = 256
            private val fs_basepath: idCVar =
                idCVar("fs_basepath", "", CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_INIT, "")
            private val fs_caseSensitiveOS: idCVar = idCVar(
                "fs_caseSensitiveOS",
                if (WIN32) "0" else "1",
                CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_BOOL,
                ""
            )
            private val fs_cdpath: idCVar = idCVar("fs_cdpath", "", CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_INIT, "")
            private val fs_copyfiles: idCVar = idCVar(
                "fs_copyfiles",
                "0",
                CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_INIT or CVarSystem.CVAR_INTEGER,
                "",
                0.0f,
                4.0f,
                ArgCompletion_Integer(0, 3)
            )

            //
            private val fs_debug: idCVar = idCVar(
                "fs_debug",
                "0",
                CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_INTEGER,
                "",
                0.0f,
                2.0f,
                ArgCompletion_Integer(0, 2)
            )
            private val fs_devpath: idCVar =
                idCVar("fs_devpath", "", CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_INIT, "")
            private val fs_game: idCVar = idCVar(
                "fs_game",
                "",
                CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_INIT or CVarSystem.CVAR_SERVERINFO,
                "mod path"
            )
            private val fs_game_base: idCVar = idCVar(
                "fs_game_base",
                "",
                CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_INIT or CVarSystem.CVAR_SERVERINFO,
                "alternate mod path, searched after the main fs_game path, before the basedir"
            )
            private val fs_restrict: idCVar =
                idCVar("fs_restrict", "", CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_INIT or CVarSystem.CVAR_BOOL, "")
            private val fs_savepath: idCVar =
                idCVar("fs_savepath", "", CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_INIT, "")
            private val fs_searchAddons: idCVar = idCVar(
                "fs_searchAddons",
                "0",
                CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_BOOL,
                "search all addon pk4s ( disables addon functionality )"
            )

            private fun  /*size_t*/CurlWriteFunction(
                ptr: ByteBuffer,    /*size_t*/size: Int,  /*size_t*/
                nmemb: Int, stream: Array<Any>
            ): Int {
                throw TODO_Exception()
                //            backgroundDownload_t bgl = (backgroundDownload_t) stream[0];
//            if (null == bgl.f) {
//                return size * nmemb;
//            }
////            if (_WIN32) {
////                return _write(((idFile_Permanent) bgl.f).GetFilePtr()._file, ptr, size * nmemb);
////            } else {
//            return ((idFile_Permanent) bgl.f).GetFilePtr().write(ptr);
////                return fwrite(ptr, size, nmemb, ((idFile_Permanent) bgl.f).GetFilePtr());
////            }
            }

            private fun CurlProgressFunction(
                clientp: Array<Any>,
                dltotal: Float,
                dlnow: Float,
                ultotal: Float,
                ulnow: Float
            ): Int {
                val bgl = clientp[0] as backgroundDownload_s
                if (bgl.url.status == dlStatus_t.DL_ABORTING) {
                    return 1
                }
                bgl.url.dltotal = dltotal.toInt()
                bgl.url.dlnow = dlnow.toInt()
                return 0
            }
        }

        init {
            gameFolder = idStr()
            dir_cache_index = 0
            dir_cache_count = 0
            d3xp = 0
            loadedFileFromDir = false
            restartGamePakChecksum = 0
            serverPaks = idList()
            addonPaks = null
            mapDict = idDict()
            restartChecksums = idList()
            addonChecksums = idList()
            dir_cache = Array(MAX_CACHED_DIRS) { idDEntry() }
        }
    }

    // ensures that lengths for pure exclusions are correct
    class idInitExclusions {
        init {
            var i = 0
            while (pureExclusions[i].func != null) {
                if (pureExclusions[i].name != null) {
                    pureExclusions[i].nameLen = pureExclusions[i].name!!.length
                }
                if (pureExclusions[i].ext != null) {
                    pureExclusions[i].extLen = pureExclusions[i].ext!!.length
                }
                i++
            }
        }
    }
}

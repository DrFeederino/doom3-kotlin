/*
 * Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
 * Translated to Kotlin by Dr. Feederino with support of Claude Code
 *
 * This file is part of the Doom 3 Kotlin project.
 * Original source: neo/framework/CVarSystem.h, neo/framework/CVarSystem.cpp
 *
 * Doom 3 GPL Source Code is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Doom 3 Source Code is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 */
package neo.framework

import neo.Game.GameSys.SysCvar
import neo.Game.Game_local
import neo.Game.Game_network
import neo.Renderer.Image
import neo.Renderer.MegaTexture.idMegaTexture
import neo.Renderer.Model_local.idRenderModelStatic
import neo.Renderer.VertexCache.idVertexCache
import neo.Sound.snd_system
import neo.TempDump
import neo.TempDump.void_callback
import neo.cm.idCollisionModelManagerLocal
import neo.framework.Async.AsyncNetwork
import neo.framework.Async.ServerScan
import neo.framework.CmdSystem.cmdFunction_t
import neo.framework.CmdSystem.idCmdSystem.ArgCompletion_Boolean
import neo.framework.DemoFile.idDemoFile
import neo.framework.EventLoop.idEventLoop
import neo.framework.FileSystem_h.idFileSystemLocal
import neo.framework.File_h.idFile
import neo.framework.Session_local.idSessionLocal
import neo.framework.UsercmdGen.idUsercmdGenLocal
import neo.idlib.BIT
import neo.idlib.CmdArgs
import neo.idlib.Dict_h.idDict
import neo.idlib.Text.Str
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Str.idStr.Companion.FindChar
import neo.idlib.containers.List.cmp_t
import neo.idlib.containers.List.idList
import neo.idlib.containers.idHashIndex
import neo.idlib.idException
import neo.idlib.idLib
import neo.sys.sys_local
import neo.sys.win_local
import neo.sys.win_net
import neo.ui.DeviceContext
import neo.ui.GameBearShootWindow
import neo.ui.Window.idWindow
import java.util.*

//	CVar Registration
//
//	Each DLL using CVars has to declare a private copy of the static variable
//	idCVar::staticVars like this: idCVar * idCVar::staticVars = NULL;
object CVarSystem {
    /*
     ===============================================================================

     Console Variables (CVars) are used to hold scalar or string variables
     that can be changed or displayed at the console as well as accessed
     directly in code.

     CVars are mostly used to hold settings that can be changed from the
     console or saved to and loaded from configuration files. CVars are also
     occasionally used to communicate information between different modules
     of the program.

     CVars are restricted from having the same names as console commands to
     keep the console interface from being ambiguous.

     CVars can be accessed from the console in three ways:
     cvarName			prints the current value
     cvarName X			sets the value to X if the variable exists
     set cvarName X		as above, but creates the CVar if not present

     CVars may be declared in the global namespace, in classes and in functions.
     However declarations in classes and functions should always be static to
     save space and time. Making CVars static does not change their
     functionality due to their global nature.

     CVars should be contructed only through one of the constructors with name,
     value, flags and description. The name, value and description parameters
     to the constructor have to be static strings, do not use va() or the like
     functions returning a string.

     CVars may be declared multiple times using the same name string. However,
     they will all reference the same value and changing the value of one CVar
     changes the value of all CVars with the same name.

     CVars should always be declared with the correct type flag: CVAR_BOOL,
     CVAR_INTEGER or CVAR_FLOAT. If no such flag is specified the CVar
     defaults to type string. If the CVAR_BOOL flag is used there is no need
     to specify an argument auto-completion function because the CVar gets
     one assigned automatically.

     CVars are automatically range checked based on their type and any min/max
     or valid string set specified in the constructor.

     CVars are always considered cheats except when CVAR_NOCHEAT, CVAR_INIT,
     CVAR_ROM, CVAR_ARCHIVE, CVAR_USERINFO, CVAR_SERVERINFO, CVAR_NETWORKSYNC
     is set.

     ===============================================================================
     */
    const val CVAR_ALL = -1 // all flags
    val CVAR_ARCHIVE: Int = BIT(17) // set to cause it to be saved to a config file
    val CVAR_BOOL: Int = BIT(0) // variable is a boolean
    val CVAR_CHEAT: Int = BIT(13) // variable is considered a cheat
    val CVAR_FLOAT: Int = BIT(2) // variable is a float
    val CVAR_GAME: Int = BIT(7) // game variable
    val CVAR_GUI: Int = BIT(6) // gui variable
    val CVAR_INIT: Int = BIT(15) // can only be set from the command-line
    val CVAR_INTEGER: Int = BIT(1) // variable is an longeger
    val CVAR_MODIFIED: Int = BIT(18) // set when the variable is modified
    val CVAR_NETWORKSYNC: Int = BIT(11) // cvar is synced from the server to clients
    val CVAR_NOCHEAT: Int = BIT(14) // variable is not considered a cheat
    val CVAR_RENDERER: Int = BIT(4) // renderer variable
    val CVAR_ROM: Int = BIT(16) // display only; cannot be set by user at all
    val CVAR_SERVERINFO: Int = BIT(10) // sent from servers; available to menu
    val CVAR_SOUND: Int = BIT(5) // sound variable
    val CVAR_STATIC: Int = BIT(12) // statically declared; not user created
    val CVAR_SYSTEM: Int = BIT(3) // system variable
    val CVAR_TOOL: Int = BIT(8) // tool variable
    val CVAR_USERINFO: Int = BIT(9) // sent to servers; available to menu
    private val FORMAT_STRING: String = "%-32s "

    /**
     * CVARS eager init:
     * jvm's don't generally preload static fields until a class is
     * referenced, so this little trick is for all the scattered cvars.
     * could as well move them all to a single class, but we want to retain
     * a hint of...
     */
    val collision = idCollisionModelManagerLocal()
    val common = Common()
    val con = Console()
    val demoFile = idDemoFile()
    val fileSystem = idFileSystemLocal()
    val session = idSessionLocal()
    val usr = idUsercmdGenLocal()
    val async = AsyncNetwork.idAsyncNetwork()
    val scan = ServerScan()
    val image = Image
    val texture = idMegaTexture()
    val model = idRenderModelStatic()
    val vertex = idVertexCache()
    val snd = snd_system()
    val sys = sys_local()
    val wub: win_local = object : win_local() {}
    val net = win_net()
    val context = DeviceContext
    val bear = GameBearShootWindow
    val window = idWindow(null)
    val sysCvar = SysCvar()
    val game = Game_local()
    val network = Game_network()
    val event = EventLoop()
    val loop = idEventLoop()
    val decl = DeclManager()

    //
    //
    private const val NUM_COLUMNS = 77 // 78 - 1, or (80 x 2 - 2) / 2 - 2
    private const val NUM_NAME_CHARS = 33
    private const val NUM_DESCRIPTION_CHARS = NUM_COLUMNS - NUM_NAME_CHARS
    private var localCVarSystem: idCVarSystemLocal = idCVarSystemLocal()


    var cvarSystem: idCVarSystem = localCVarSystem
    fun CreateColumn(textString: String, columnWidth: Int, indent: String, string: idStr): String {
        var i: Int
        var lastLine: Int
        val text = textString.toCharArray()
        string.Clear()
        lastLine = 0.also { i = it } /*text[i] != '\0'*/
        while (i < text.size) {
            if (i - lastLine >= columnWidth || text[i] == '\n') {
                while (i > 0 && text[i] > ' ' && text[i] != '/' && text[i] != ',' && text[i] != '\\') {
                    i--
                }
                while (lastLine < i) {
                    string.Append(text[lastLine++])
                }
                string.Append(indent)
                lastLine++
            }
            i++
        }
        while (lastLine < i) {
            string.Append(text[lastLine++])
        }
        return string.toString()
    }

    fun setCvarSystems(cvarSystem: idCVarSystem) {
        localCVarSystem = cvarSystem as idCVarSystemLocal
        CVarSystem.cvarSystem = localCVarSystem
    }

    /*
     ===============================================================================

     idCVar

     ===============================================================================
     */
    open class idCVar {
        protected var description // description
                : String? = null
        var flags // CVAR_? flags
                = 0
        protected var floatValue // atof( value )
                = 0.0f
        protected var integerValue // atoi( string )
                = 0
        protected var internalVar // internal cvar
                : idCVar? = null
        protected var name // name
                : String? = null
        protected var next // next statically declared cvar
                : idCVar? = null
        var value // value
                : String? = null
        var valueCompletion // value auto-completion function
                : CmdSystem.argCompletion_t? = null
        protected var valueMax // maximum value
                = 0.0f
        protected var valueMin // minimum value
                = 0.0f
        protected var valueStrings // valid value strings
                : Array<String?>? = null

        // Never use the default constructor.
        constructor() {
            //assert (!this.getClass().equals(idCVar.class));
        }

        // Always use one of the following constructors.
        // FIX: C++ has valueCompletion as a local parameter defaulting to NULL. The bool check sets the
        // local, then passes it to Init. The old Kotlin code modified the member then passed null to Init,
        // discarding the boolean auto-completion.
        constructor(name: String, value: String, flags: Int, description: String) {
            val vc: CmdSystem.argCompletion_t? =
                if (flags and CVAR_BOOL != 0) ArgCompletion_Boolean.getInstance() else null
            Init(name, value, flags, description, 1.0f, -1.0f, null, vc)
        }

        constructor(
            name: String, value: String, flags: Int, description: String, valueCompletion: CmdSystem.argCompletion_t
        ) {
            var valueCompletion = valueCompletion
            if (null == valueCompletion && flags and CVAR_BOOL != 0) {
                valueCompletion = ArgCompletion_Boolean.getInstance()
            }
            Init(name, value, flags, description, 1.0f, -1.0f, null, valueCompletion)
        }

        constructor(name: String, value: String, flags: Int, description: String, valueMin: Float, valueMax: Float) {
            Init(name, value, flags, description, valueMin, valueMax, null, null)
        }

        constructor(
            name: String,
            value: String,
            flags: Int,
            description: String,
            valueMin: Float,
            valueMax: Float,
            valueCompletion: CmdSystem.argCompletion_t
        ) {
            Init(name, value, flags, description, valueMin, valueMax, null, valueCompletion)
        }

        constructor(name: String, value: String, flags: Int, description: String, valueStrings: Array<String?>?) {
            Init(name, value, flags, description, 1.0f, -1.0f, valueStrings, null)
        }

        constructor(
            name: String,
            value: String,
            flags: Int,
            description: String,
            valueStrings: Array<String?>,
            valueCompletion: CmdSystem.argCompletion_t
        ) {
            Init(name, value, flags, description, 1.0f, -1.0f, valueStrings, valueCompletion)
        }

        override fun equals(o: Any?): Boolean {
            if (this === o) return true
            if (o !is idCVar) return false
            val idCVar = o
            if (flags != idCVar.flags) return false
            if (idCVar.floatValue.compareTo(floatValue) != 0) return false
            if (integerValue != idCVar.integerValue) return false
            if (idCVar.valueMax.compareTo(valueMax) != 0) return false
            if (idCVar.valueMin.compareTo(valueMin) != 0) return false
            if (if (description != null) description != idCVar.description else idCVar.description != null) return false
            if (if (internalVar != null) internalVar != idCVar.internalVar else idCVar.internalVar != null) return false
            if (if (name != null) name != idCVar.name else idCVar.name != null) return false
            if (if (next != null) next != idCVar.next else idCVar.next != null) return false
            if (if (value != null) value != idCVar.value else idCVar.value != null) return false
            return if (if (valueCompletion != null) valueCompletion != idCVar.valueCompletion else idCVar.valueCompletion != null) false else valueStrings.contentEquals(
                idCVar.valueStrings
            )
        }

        override fun hashCode(): Int {
            var result = if (description != null) description.hashCode() else 0
            result = 31 * result + flags
            result = 31 * result + if (floatValue != +0.0f) floatValue.toBits() else 0
            result = 31 * result + integerValue
            result = 31 * result + if (internalVar != null) internalVar.hashCode() else 0
            result = 31 * result + if (name != null) name.hashCode() else 0
            result = 31 * result + if (next != null) next.hashCode() else 0
            result = 31 * result + if (value != null) value.hashCode() else 0
            result = 31 * result + if (valueCompletion != null) valueCompletion.hashCode() else 0
            result = 31 * result + if (valueMax != +0.0f) valueMax.toBits() else 0
            result = 31 * result + if (valueMin != +0.0f) valueMin.toBits() else 0
            result = 31 * result + valueStrings.contentHashCode()
            return result
        }

        fun GetName(): String {
            return internalVar!!.name!!
        }

        fun GetFlags(): Int {
            return internalVar!!.flags
        }

        fun GetDescription(): String? {
            return internalVar!!.description
        }

        fun GetMinValue(): Float {
            return internalVar!!.valueMin
        }

        fun GetMaxValue(): Float {
            return internalVar!!.valueMax
        }

        fun GetValueStrings(): Array<String?>? {
            return valueStrings
        }

        fun GetValueCompletion(): CmdSystem.argCompletion_t? {
            return valueCompletion
        }

        fun IsModified(): Boolean {
            return internalVar!!.flags and CVAR_MODIFIED != 0
        }

        fun SetModified() {
            internalVar!!.flags = internalVar!!.flags or CVAR_MODIFIED
        }

        fun ClearModified() {
            internalVar!!.flags = internalVar!!.flags and CVAR_MODIFIED.inv()
        }

        fun GetString(): String? {
            return internalVar!!.value
        }

        fun GetBool(): Boolean {
            // FIX: C++ uses internalVar->integerValue != 0, not string comparison on this.value
            return internalVar!!.integerValue != 0
        }

        fun GetInteger(): Int {
            return internalVar!!.integerValue
        }

        fun GetFloat(): Float {
            return internalVar!!.floatValue
        }

        fun SetString(value: String?) {
            internalVar!!.InternalSetString(value)
        }

        fun SetBool(value: Boolean) {
            internalVar!!.InternalSetBool(value)
        }

        fun SetInteger(value: Int) {
            internalVar!!.InternalSetInteger(value)
        }

        fun SetFloat(value: Float) {
            internalVar!!.InternalSetFloat(value)
        }

        fun SetInternalVar(cvar: idCVar) {
            internalVar = cvar
        }

        /*
         ===============================================================================

         CVar Registration

         Each DLL using CVars has to declare a private copy of the static variable
         idCVar::staticVars like this: idCVar * idCVar::staticVars = NULL;
         Furthermore idCVar::RegisterStaticVars() has to be called after the
         cvarSystem pointer is set when the DLL is first initialized.

         ===============================================================================
         */
        private fun Init(
            name: String,
            value: String,
            flags: Int,
            description: String,
            valueMin: Float,
            valueMax: Float,
            valueStrings: Array<String?>?,
            valueCompletion: CmdSystem.argCompletion_t?
        ) {
            this.name = name
            this.value = value
            this.flags = flags
            this.description = description
            this.flags = flags or CVAR_STATIC
            this.valueMin = valueMin
            this.valueMax = valueMax
            this.valueStrings = valueStrings
            this.valueCompletion = valueCompletion
            integerValue = 0
            floatValue = 0.0f
            internalVar = this
            if (staticVars !== ID_CVAR_0xFFFFFFFF) {
                next = staticVars
                staticVars = this
            } else {
                cvarSystem.Register(this)
            }
        }

        //virtual
        protected open fun InternalSetString(newValue: String?) {}
        protected open fun InternalSetBool(newValue: Boolean) {}
        protected open fun InternalSetInteger(newValue: Int) {}
        protected open fun InternalSetFloat(newValue: Float) {}

        companion object {
            private val ID_CVAR_0xFFFFFFFF: idCVar = idCVar()

            //
            private var staticVars: idCVar? = null

            //
            //public	virtual					~idCVar( void ) {}
            //
            /*
         ===============================================================================

         CVar Registration

         Each DLL using CVars has to declare a private copy of the static variable
         idCVar::staticVars like this: idCVar * idCVar::staticVars = NULL;
         Furthermore idCVar::RegisterStaticVars() has to be called after the
         cvarSystem pointer is set when the DLL is first initialized.

         ===============================================================================
         */
            fun RegisterStaticVars() {
                if (staticVars != ID_CVAR_0xFFFFFFFF) {
                    var cvar = staticVars
                    while (cvar != null) {
                        cvarSystem.Register(cvar)
                        cvar = cvar.next
                    }
                    staticVars = ID_CVAR_0xFFFFFFFF
                }
            }
        }
    }

    /**
     * ===============================================================================
     *
     *
     * idCVarSystem
     *
     *
     * ===============================================================================
     */
    abstract class idCVarSystem {
        //	public abstract					~idCVarSystem( ) {}
        @Throws(idException::class)
        abstract fun Init()
        abstract fun Shutdown()
        abstract fun IsInitialized(): Boolean

        // Registers a CVar.
        @Throws(idException::class)
        abstract fun Register(cvar: idCVar)

        // Finds the CVar with the given name.
        // Returns NULL if there is no CVar with the given name.
        abstract fun Find(name: String): idCVar?

        // Sets the value of a CVar by name.
        abstract fun SetCVarString(name: String, value: String)
        abstract fun SetCVarString(name: String, value: String, flags: Int)
        abstract fun SetCVarBool(name: String, value: Boolean)
        abstract fun SetCVarBool(name: String, value: Boolean, flags: Int)
        abstract fun SetCVarInteger(name: String, value: Int)
        abstract fun SetCVarInteger(name: String, value: Int, flags: Int)
        abstract fun SetCVarFloat(name: String, value: Float)
        abstract fun SetCVarFloat(name: String, value: Float, flags: Int)

        // Gets the value of a CVar by name.
        abstract fun GetCVarString(name: String): String
        abstract fun GetCVarBool(name: String): Boolean
        abstract fun GetCVarInteger(name: String): Int
        abstract fun GetCVarFloat(name: String): Float

        // Called by the command system when argv(0) doesn't match a known command.
        // Returns true if argv(0) is a variable reference and prints or changes the CVar.
        @Throws(idException::class)
        abstract fun Command(args: CmdArgs.idCmdArgs?): Boolean

        // Command and argument completion using callback for each valid string.
        @Throws(idException::class)
        abstract fun CommandCompletion(callback: void_callback<String> /*, final String s*/)

        @Throws(idException::class)
        abstract fun ArgCompletion(cmdString: String, callback: void_callback<String> /*, final String s*/)

        // Sets/gets/clears modified flags that tell what kind of CVars have changed.
        abstract fun SetModifiedFlags(flags: Int)
        abstract fun GetModifiedFlags(): Int
        abstract fun ClearModifiedFlags(flags: Int)

        // Resets variables with one of the given flags set.
        @Throws(idException::class)
        abstract fun ResetFlaggedVariables(flags: Int)

        // Removes auto-completion from the flagged variables.
        abstract fun RemoveFlaggedAutoCompletion(flags: Int)

        // Writes variables with one of the given flags set to the given file.
        abstract fun WriteFlaggedVariables(flags: Int, setCmd: String, f: idFile)

        // Moves CVars to and from dictionaries.
        @Throws(idException::class)
        abstract fun MoveCVarsToDict(flags: Int): idDict

        @Throws(idException::class)
        abstract fun SetCVarsFromDict(dict: idDict)
    }

    class idInternalCVar : idCVar {
        // friend class idCVarSystemLocal;
        private val descriptionString // description
                : idStr = idStr()
        val nameString // name
                : idStr = idStr()
        val resetString // resetting will change to this value
                : idStr = idStr()
        val valueString // value
                : idStr = idStr()

        // FIX: C++ implicitly calls the default idCVar() constructor here, NOT the 4-arg constructor.
        // Calling super(name, value, flags, "") invoked Init() which registered the cvar with the system
        // during construction, causing double registration when SetInternal also adds it to the hash.
        constructor(newName: String, newValue: String, newFlags: Int) : super() {
            nameString.set(newName)
            name = newName
            valueString.set(newValue)
            value = newValue
            resetString.set(newValue)
            descriptionString.set("")
            description = ""
            flags = newFlags and CVAR_STATIC.inv() or CVAR_MODIFIED
            valueMin = 1.0f
            valueMax = -1.0f
            valueStrings = null
            valueCompletion = null
            UpdateValue()
            UpdateCheat()
            internalVar = this
        }

        constructor(cvar: idCVar) : super() {
            nameString.set(cvar.GetName())
            name = cvar.GetName()
            valueString.set(cvar.GetString())
            value = cvar.GetString()
            resetString.set(cvar.GetString())
            descriptionString.set(cvar.GetDescription())
            description = cvar.GetDescription()
            flags = cvar.GetFlags() or CVAR_MODIFIED
            valueMin = cvar.GetMinValue()
            valueMax = cvar.GetMaxValue()
            valueStrings = CopyValueStrings(cvar.GetValueStrings())
            valueCompletion = cvar.GetValueCompletion()
            UpdateValue()
            UpdateCheat()
            internalVar = this
        }

        //	// virtual					~idInternalCVar( void );
        //
        fun CopyValueStrings(strings: Array<String?>?): Array<String?>? {
//	int i, totalLength;
//	const char **ptr;
//	char *str;
//
//	if ( !strings ) {
//		return NULL;
//	}
//
//	totalLength = 0;
//	for ( i = 0; strings[i] != NULL; i++ ) {
//		totalLength += idStr::Length( strings[i] ) + 1;
//	}
//
//	ptr = (const char **) Mem_Alloc( ( i + 1 ) * sizeof( char * ) + totalLength );
//	str = (char *) (((byte *)ptr) + ( i + 1 ) * sizeof( char * ) );
//
//	for ( i = 0; strings[i] != NULL; i++ ) {
//		ptr[i] = str;
//		strcpy( str, strings[i] );
//		str += idStr::Length( strings[i] ) + 1;
//	}
//	ptr[i] = NULL;
//
//	return ptr;

//            return Arrays.copyOf(strings, strings.length);
            return if (strings == null) null else strings.clone()
        }

        @Throws(idException::class)
        fun Update(cvar: idCVar) {

            // if this is a statically declared variable
            if (cvar.GetFlags() and CVAR_STATIC != 0) {
                if (flags and CVAR_STATIC != 0) {

                    // the code has more than one static declaration of the same variable, make sure they have the same properties
                    if (resetString.Icmp(cvar.GetString()!!) != 0) {
                        idLib.common.Warning(
                            "CVar '%s' declared multiple times with different initial value", nameString
                        )
                    }
                    if (flags and (CVAR_BOOL or CVAR_INTEGER or CVAR_FLOAT) != cvar.GetFlags() and (CVAR_BOOL or CVAR_INTEGER or CVAR_FLOAT)) {
                        idLib.common.Warning("CVar '%s' declared multiple times with different type", nameString)
                    }
                    if (valueMin != cvar.GetMinValue() || valueMax != cvar.GetMaxValue()) {
                        idLib.common.Warning(
                            "CVar '%s' declared multiple times with different minimum/maximum", nameString
                        )
                    }
                }

                // the code is now specifying a variable that the user already set a value for, take the new value as the reset value
                resetString.set(cvar.GetString()!!)
                descriptionString.set(cvar.GetDescription()!!)
                description = cvar.GetDescription()
                valueMin = cvar.GetMinValue()
                valueMax = cvar.GetMaxValue()
                //                Mem_Free(valueStrings);
                valueStrings = CopyValueStrings(cvar.GetValueStrings())
                valueCompletion = cvar.GetValueCompletion()
                UpdateValue()
                cvarSystem.SetModifiedFlags(cvar.GetFlags())
            }
            flags = flags or cvar.GetFlags()
            UpdateCheat()

            // only allow one non-empty reset string without a warning
            if (resetString.Length() == 0) {
                resetString.set(cvar.GetString()!!)
                // FIX: C++ checks cvar->GetString()[0] which is a non-empty string check, not a null check.
            } else if (cvar.GetString()?.isNotEmpty() == true && resetString.Cmp(cvar.GetString()!!) != 0) {
                idLib.common.Warning(
                    "cvar \"%s\" given initial values: \"%s\" and \"%s\"\n", nameString, resetString, cvar.GetString()!!
                )
            }
        }

        fun UpdateValue() {
            var clamped = false
            if (flags and CVAR_BOOL != 0) {
                integerValue = if (TempDump.atoi(value!!) != 0) 1 else 0
                floatValue = integerValue.toFloat()
                if (idStr.Icmp(value!!, "0") != 0 && idStr.Icmp(value!!, "1") != 0) {
                    // FIX: C++ idStr(bool) produces "1"/"0". Kotlin Boolean.toString() produces "true"/"false"
                    // which would break on config save/reload since atoi("true") returns 0.
                    valueString.set(if (integerValue != 0) "1" else "0")
                    value = valueString.toString()
                }
            } else if (flags and CVAR_INTEGER != 0) {
                integerValue = TempDump.atoi(value!!)
                if (valueMin < valueMax) {
                    if (integerValue < valueMin) {
                        integerValue = valueMin.toInt()
                        clamped = true
                    } else if (integerValue > valueMax) {
                        integerValue = valueMax.toInt()
                        clamped = true
                    }
                }
                // FIX: FindChar returns -1 when not found (like indexOf). C++ checks != -1. Was != 0 which made -1 (not found) truthy and 0 (dot at start) falsy — both wrong.
                if (clamped || !idStr.IsNumeric(value!!) || FindChar(value!!, '.') != -1) {
                    valueString.set(integerValue.toString())
                    value = valueString.toString()
                }
                floatValue = integerValue.toFloat()
            } else if (flags and CVAR_FLOAT != 0) {
                floatValue = TempDump.atof(value!!)
                if (valueMin < valueMax) {
                    if (floatValue < valueMin) {
                        floatValue = valueMin
                        clamped = true
                    } else if (floatValue > valueMax) {
                        floatValue = valueMax
                        clamped = true
                    }
                }
                if (clamped || !idStr.IsNumeric(value!!)) {
                    valueString.set(floatValue.toString())
                    value = valueString.toString()
                }
                integerValue = floatValue.toInt()
            } else {
                if (valueStrings != null && valueStrings!!.size > 0) {
                    integerValue = 0
                    var i = 0
                    while (valueStrings!![i] != null) {
                        if (valueString.Icmp(valueStrings!![i]!!) == 0) {
                            integerValue = i
                            break
                        }
                        i++
                    }
                    valueString.set(valueStrings!![integerValue])
                    value = valueStrings!![integerValue]
                    floatValue = integerValue.toFloat()
                } else if (valueString.Length() < 32) {
                    floatValue = TempDump.atof(value!!)
                    integerValue = floatValue.toInt()
                } else {
                    floatValue = 0.0f
                    integerValue = 0
                }
            }
        }

        fun UpdateCheat() {
            // all variables are considered cheats except for a few types
            flags =
                if (flags and (CVAR_NOCHEAT or CVAR_INIT or CVAR_ROM or CVAR_ARCHIVE or CVAR_USERINFO or CVAR_SERVERINFO or CVAR_NETWORKSYNC) != 0) {
                    flags and CVAR_CHEAT.inv()
                } else {
                    flags or CVAR_CHEAT
                }
        }

        fun Set(newValue: String?, force: Boolean, fromServer: Boolean) {
            var newValue = newValue
            if (Session.session != null && Session.session.IsMultiplayer() && !fromServer) {
// #ifndef ID_TYPEINFO
                // if ( ( flags & CVAR_NETWORKSYNC ) && idAsyncNetwork::client.IsActive() ) {
                // common.Printf( "%s is a synced over the network and cannot be changed on a multiplayer client.\n", nameString.c_str() );
// #if ID_ALLOW_CHEATS
                // common.Printf( "ID_ALLOW_CHEATS override!\n" );
// #else
                // return;
// #endif
//		}
// #endif
                if (flags and CVAR_CHEAT != 0 && !cvarSystem.GetCVarBool("net_allowCheats")) {
                    idLib.common.Printf("%s cannot be changed in multiplayer.\n", nameString)
                    // #if ID_ALLOW_CHEATS
                    // common.Printf( "ID_ALLOW_CHEATS override!\n" );
// #else
                    return
                    // #endif
                }
            }
            if (null == newValue) {
                newValue = resetString.toString()
            }
            if (!force) {
                if (flags and CVAR_ROM != 0) {
                    idLib.common.Printf("%s is read only.\n", nameString)
                    return
                }
                if (flags and CVAR_INIT != 0) {
                    idLib.common.Printf("%s is write protected.\n", nameString)
                    return
                }
            }
            if (valueString.Icmp(newValue) == 0) {
                return
            }
            valueString.set(newValue)
            value = newValue
            UpdateValue()
            SetModified()
            cvarSystem.SetModifiedFlags(flags)
        }

        fun Reset() {
            valueString.set(resetString)
            value = valueString.toString()
            UpdateValue()
        }

        public override fun InternalSetString(newValue: String?) {
            Set(newValue, true, false)
        }

        @Throws(idException::class)
        fun InternalServerSetString(newValue: String?) {
            Set(newValue, true, true)
        }

        @Throws(idException::class)
        override fun InternalSetBool(newValue: Boolean) {
            Set(TempDump.btoi(newValue).toString(), true, false)
        }

        @Throws(idException::class)
        override fun InternalSetInteger(newValue: Int) {
            Set(newValue.toString(), true, false)
        }

        @Throws(idException::class)
        override fun InternalSetFloat(newValue: Float) {
            Set(newValue.toString(), true, false)
        }
    }

    /*
     ===============================================================================

     idCVarSystemLocal

     ===============================================================================
     */
    internal class idCVarSystemLocal : idCVarSystem() {
        private val cvarHash: idHashIndex
        private val cvars: idList<idInternalCVar>
        private var initialized = false
        private var modifiedFlags: Int

        @Throws(idException::class)
        override fun Init() {
            modifiedFlags = 0
            CmdSystem.cmdSystem.AddCommand("toggle", Toggle_f.getInstance(), CmdSystem.CMD_FL_SYSTEM, "toggles a cvar")
            CmdSystem.cmdSystem.AddCommand("set", Set_f.getInstance(), CmdSystem.CMD_FL_SYSTEM, "sets a cvar")
            CmdSystem.cmdSystem.AddCommand(
                "sets", SetS_f.getInstance(), CmdSystem.CMD_FL_SYSTEM, "sets a cvar and flags it as server info"
            )
            CmdSystem.cmdSystem.AddCommand(
                "setu", SetU_f.getInstance(), CmdSystem.CMD_FL_SYSTEM, "sets a cvar and flags it as user info"
            )
            CmdSystem.cmdSystem.AddCommand(
                "sett", SetT_f.getInstance(), CmdSystem.CMD_FL_SYSTEM, "sets a cvar and flags it as tool"
            )
            CmdSystem.cmdSystem.AddCommand(
                "seta", SetA_f.getInstance(), CmdSystem.CMD_FL_SYSTEM, "sets a cvar and flags it as archive"
            )
            CmdSystem.cmdSystem.AddCommand("reset", Reset_f.getInstance(), CmdSystem.CMD_FL_SYSTEM, "resets a cvar")
            CmdSystem.cmdSystem.AddCommand(
                "listCvars", List_f.getInstance(), CmdSystem.CMD_FL_SYSTEM, "lists cvars"
            )
            CmdSystem.cmdSystem.AddCommand(
                "cvar_restart", Restart_f.getInstance(), CmdSystem.CMD_FL_SYSTEM, "restart the cvar system"
            )
            initialized = true
        }

        override fun Shutdown() {
            cvars.DeleteContents(true)
            cvarHash.Free()
            moveCVarsToDict.Clear()
            initialized = false
        }

        override fun IsInitialized(): Boolean {
            return initialized
        }

        @Throws(idException::class)
        override fun Register(cvar: idCVar) {
            val hash: Int
            var internal: idInternalCVar?
            cvar.SetInternalVar(cvar)
            internal = FindInternal(cvar.GetName())
            if (internal != null) {
                internal.Update(cvar)
            } else {
                internal = idInternalCVar(cvar)
                hash = cvarHash.GenerateKey(internal.nameString.toString(), false)
                cvarHash.Add(hash, cvars.Append(internal))
            }
            cvar.SetInternalVar(internal)
        }

        override fun Find(name: String): idCVar? {
            return FindInternal(name)
        }

        override fun SetCVarString(name: String, value: String /*, int flags = 0*/) {
            SetCVarString(name, value, 0)
        }

        //public	 void			SetCVarBool( final String name, const boolean value/*, int flags = 0*/);
        override fun SetCVarString(name: String, value: String, flags: Int) {
            SetInternal(name, value, flags)
        }

        override fun SetCVarBool(name: String, value: Boolean) {
            SetCVarBool(name, value, 0)
        }

        override fun SetCVarBool(name: String, value: Boolean, flags: Int) {
            // FIX: C++ uses idStr(bool) which produces "1"/"0". Kotlin "" + bool produces "true"/"false"
            // which atoi() can't parse, causing boolean cvars to always read as false.
            SetInternal(name, if (value) "1" else "0", flags)
        }

        //public	 void			SetCVarInteger( final String name, const int value/*, int flags = 0*/ );
        override fun SetCVarInteger(name: String, value: Int) {
            SetCVarInteger(name, value, 0)
        }

        override fun SetCVarInteger(name: String, value: Int, flags: Int) {
            SetInternal(name, "" + value, flags)
        }

        override fun SetCVarFloat(name: String, value: Float) {
            SetCVarFloat(name, value, 0)
        }

        override fun SetCVarFloat(name: String, value: Float, flags: Int) {
            SetInternal(name, "" + value, flags)
        }

        override fun GetCVarString(name: String): String {
            val internal = FindInternal(name)
            return if (internal != null) {
                internal.GetString()!!
            } else ""
        }

        override fun GetCVarBool(name: String): Boolean {
            val internal = FindInternal(name)
            return internal?.GetBool() ?: false
        }

        override fun GetCVarInteger(name: String): Int {
            val internal = FindInternal(name)
            return internal?.GetInteger() ?: 0
        }

        override fun GetCVarFloat(name: String): Float {
            val internal = FindInternal(name)
            return internal?.GetFloat() ?: 0.0f
        }

        @Throws(idException::class)
        override fun Command(args: CmdArgs.idCmdArgs?): Boolean {
            val internal: idInternalCVar?
            internal = FindInternal(args!!.Argv(0))
            if (internal == null) {
                return false
            }
            if (args.Argc() == 1) {
                // print the variable
                idLib.common.Printf(
                    """"%s" is:"%s" ${Str.S_COLOR_WHITE} default:"%s"
""", internal.nameString, internal.valueString, internal.resetString
                )
                if ( /*idStr.Length*/internal.GetDescription()!!.length > 0) {
                    idLib.common.Printf(
                        """
    ${Str.S_COLOR_WHITE}%s
    
    """.trimIndent(), internal.GetDescription()!!
                    )
                }
            } else {
                // set the value
                internal.Set(args.Args(), false, false)
            }
            return true
        }

        @Throws(idException::class)
        override fun CommandCompletion(callback: void_callback<String>) {
            for (i in 0 until cvars.Num()) {
                callback.run(cvars[i].GetName())
            }
        }

        @Throws(idException::class)
        override fun ArgCompletion(cmdString: String, callback: void_callback<String>) {
            val args = CmdArgs.idCmdArgs()
            args.TokenizeString(cmdString, false)
            for (i in 0 until cvars.Num()) {
                if (null == cvars[i].valueCompletion) {
                    continue
                }
                if (idStr.Icmp(args.Argv(0), cvars[i].nameString.toString()) == 0) {
                    cvars[i].valueCompletion!!.run(args, callback)
                    break
                }
            }
        }

        override fun SetModifiedFlags(flags: Int) {
            modifiedFlags = modifiedFlags or flags
        }

        override fun GetModifiedFlags(): Int {
            return modifiedFlags
        }

        override fun ClearModifiedFlags(flags: Int) {
            modifiedFlags = modifiedFlags and flags.inv()
        }

        @Throws(idException::class)
        override fun ResetFlaggedVariables(flags: Int) {
            for (i in 0 until cvars.Num()) {
                val cvar = cvars[i]
                if (cvar.GetFlags() and flags != 0) {
                    cvar.Set(null, true, true)
                }
            }
        }

        override fun RemoveFlaggedAutoCompletion(flags: Int) {
            for (i in 0 until cvars.Num()) {
                val cvar = cvars[i]
                if (cvar.GetFlags() and flags != 0) {
                    cvar.valueCompletion = null
                }
            }
        }

        /*
         ============
         idCVarSystemLocal::WriteFlaggedVariables

         Appends lines containing "set variable value" for all variables
         with the "flags" flag set to true.
         ============
         */
        override fun WriteFlaggedVariables(flags: Int, setCmd: String, f: idFile) {
            for (i in 0 until cvars.Num()) {
                val cvar = cvars[i]
                if (cvar.GetFlags() and flags != 0) {
                    f.Printf("%s %s \"%s\"\n", setCmd, cvar.GetName(), cvar.GetString()!!)
                }
            }
        }

        @Throws(idException::class)
        override fun MoveCVarsToDict(flags: Int): idDict {
            moveCVarsToDict.Clear()
            for (i in 0 until cvars.Num()) {
                val cvar: idCVar = cvars[i]
                if (cvar.GetFlags() and flags != 0) {
                    moveCVarsToDict.Set(cvar.GetName(), cvar.GetString()!!)
                }
            }
            return moveCVarsToDict
        }

        //
        //public	void					RegisterInternal( idCVar cvar );
        @Throws(idException::class)
        override fun SetCVarsFromDict(dict: idDict) {
            var internal: idInternalCVar?
            for (i in 0 until dict.GetNumKeyVals()) {
                val kv = dict.GetKeyVal(i)!!
                internal = FindInternal(kv.GetKey().toString())
                internal?.InternalServerSetString(kv.GetValue().toString())
            }
        }

        fun FindInternal(name: String): idInternalCVar? {
            val hash = cvarHash.GenerateKey(name, false)
            var i = cvarHash.First(hash)
            while (i != -1) {
                if (cvars[i].nameString.Icmp(name) == 0) {
                    return cvars[i]
                }
                i = cvarHash.Next(i)
            }
            return null
        }

        fun SetInternal(name: String, value: String, flags: Int) {
            val hash: Int
            var internal: idInternalCVar?
            internal = FindInternal(name)
            if (internal != null) {
                internal.InternalSetString(value)
                internal.flags = internal.flags or (flags and CVAR_STATIC.inv())
                internal.UpdateCheat()
            } else {
                internal = idInternalCVar(name, value, flags)
                hash = cvarHash.GenerateKey(internal.nameString.toString(), false)
                cvarHash.Add(hash, cvars.Append(internal))
            }
        }

        enum class show {
            SHOW_VALUE,
            SHOW_DESCRIPTION,
            SHOW_TYPE,
            SHOW_FLAGS
        }

        /*
         ============
         idCVarSystemLocal::Toggle_f
         ============
         */
        internal class Toggle_f : cmdFunction_t() {
            @Throws(idException::class)
            override fun run(args: CmdArgs.idCmdArgs?) {
                val argc: Int
                var i: Int
                var current: Float
                val set: Float
                val text: String
                argc = args!!.Argc()
                if (argc < 2) {
                    idLib.common.Printf(
                        """usage:
   toggle <variable>  - toggles between 0 and 1
   toggle <variable> <value> - toggles between 0 and <value>
   toggle <variable> [string 1] [string 2]...[string n] - cycles through all strings
"""
                    )
                    return
                }
                val cvar: idInternalCVar? = localCVarSystem.FindInternal(args.Argv(1))
                if (null == cvar) {
                    idLib.common.Warning("Toggle_f: cvar \"%s\" not found", args.Argv(1))
                    return
                }
                if (argc > 3) {
                    // cycle through multiple values
                    text = cvar.GetString()!!
                    i = 2
                    while (i < argc) {
                        if (0 == idStr.Icmp(text, args.Argv(i))) {
                            // point to next value
                            i++
                            break
                        }
                        i++
                    }
                    if (i >= argc) {
                        i = 2
                    }
                    idLib.common.Printf("set %s = %s\n", args.Argv(1), args.Argv(i))
                    cvar.Set(Str.va("%s", args.Argv(i)), false, false)
                } else {
                    // toggle between 0 and 1
                    current = cvar.GetFloat()
                    set = if (argc == 3) {
                        TempDump.atof(args.Argv(2))
                    } else {
                        1.0f
                    }
                    current = if (current == 0.0f) {
                        set
                    } else {
                        0.0f
                    }
                    idLib.common.Printf("set %s = %f\n", args.Argv(1), current)
                    cvar.Set(idStr(current).toString(), false, false)
                }
            }

            companion object {
                private val instance: cmdFunction_t = Toggle_f()
                fun getInstance(): cmdFunction_t {
                    return instance
                }
            }
        }

        internal class Set_f : cmdFunction_t() {
            @Throws(idException::class)
            override fun run(args: CmdArgs.idCmdArgs?) {
                val str: String?
                str = args!!.Args(2, args.Argc() - 1)
                localCVarSystem.SetCVarString(args.Argv(1), str)
            }

            companion object {
                private val instance: cmdFunction_t = Set_f()
                fun getInstance(): cmdFunction_t {
                    return instance
                }
            }
        }

        internal class SetS_f : cmdFunction_t() {
            @Throws(idException::class)
            override fun run(args: CmdArgs.idCmdArgs?) {
                val cvar: idInternalCVar?
                Set_f.getInstance().run(args)
                cvar = localCVarSystem.FindInternal(args!!.Argv(1))
                if (null == cvar) {
                    return
                }
                cvar.flags = cvar.flags or (CVAR_SERVERINFO or CVAR_ARCHIVE)
            }

            companion object {
                private val instance: cmdFunction_t = SetS_f()
                fun getInstance(): cmdFunction_t {
                    return instance
                }
            }
        }

        internal class SetU_f : cmdFunction_t() {
            @Throws(idException::class)
            override fun run(args: CmdArgs.idCmdArgs?) {
                val cvar: idInternalCVar?
                Set_f.getInstance().run(args)
                cvar = localCVarSystem.FindInternal(args!!.Argv(1))
                if (null == cvar) {
                    return
                }
                cvar.flags = cvar.flags or (CVAR_USERINFO or CVAR_ARCHIVE)
            }

            companion object {
                private val instance: cmdFunction_t = SetU_f()
                fun getInstance(): cmdFunction_t {
                    return instance
                }
            }
        }

        internal class SetT_f : cmdFunction_t() {
            @Throws(idException::class)
            override fun run(args: CmdArgs.idCmdArgs?) {
                val cvar: idInternalCVar?
                Set_f.getInstance().run(args)
                cvar = localCVarSystem.FindInternal(args!!.Argv(1))
                if (null == cvar) {
                    return
                }
                cvar.flags = cvar.flags or CVAR_TOOL
            }

            companion object {
                private val instance: cmdFunction_t = SetT_f()
                fun getInstance(): cmdFunction_t {
                    return instance
                }
            }
        }

        internal class SetA_f : cmdFunction_t() {
            @Throws(idException::class)
            override fun run(args: CmdArgs.idCmdArgs?) {
                val cvar: idInternalCVar?
                Set_f.getInstance().run(args)
                cvar = localCVarSystem.FindInternal(args!!.Argv(1))
                //                if (null == cvar) {
//                    return;
//                }

                // FIXME: enable this for ship, so mods can store extra data
                // but during development we don't want obsolete cvars to continue
                // to be saved
//	cvar->flags |= CVAR_ARCHIVE;
            }

            companion object {
                private val instance: cmdFunction_t = SetA_f()
                fun getInstance(): cmdFunction_t {
                    return instance
                }
            }
        }

        internal class Reset_f : cmdFunction_t() {
            @Throws(idException::class)
            override fun run(args: CmdArgs.idCmdArgs?) {
                val cvar: idInternalCVar?
                if (args!!.Argc() != 2) {
                    idLib.common.Printf("usage: reset <variable>\n")
                    return
                }
                cvar = localCVarSystem.FindInternal(args.Argv(1))
                if (null == cvar) {
                    return
                }
                cvar.Reset()
            }

            companion object {
                private val instance: cmdFunction_t = Reset_f()
                fun getInstance(): cmdFunction_t {
                    return instance
                }
            }
        }

        internal class List_f : cmdFunction_t() {
            @Throws(idException::class)
            override fun run(args: CmdArgs.idCmdArgs?) {
                ListByFlags(args!!, CVAR_ALL)
            }

            companion object {
                private val instance: cmdFunction_t = List_f()
                fun getInstance(): cmdFunction_t {
                    return instance
                }
            }
        }

        internal class Restart_f : cmdFunction_t() {
            override fun run(args: CmdArgs.idCmdArgs?) {
                var i: Int
                var hash: Int
                var cvar: idInternalCVar?
                i = 0
                while (i < localCVarSystem.cvars.Num()) {
                    cvar = localCVarSystem.cvars[i]

                    // don't mess with rom values
                    if (cvar.flags and (CVAR_ROM or CVAR_INIT) != 0) {
                        i++
                        continue
                    }

                    // throw out any variables the user created
                    if (0 == cvar.flags and CVAR_STATIC) {
                        hash = localCVarSystem.cvarHash.GenerateKey(cvar.nameString.toString(), false)
                        //			delete cvar;
                        localCVarSystem.cvars.RemoveIndex(i)
                        localCVarSystem.cvarHash.RemoveIndex(hash, i)
                        // NOTE: In C++ this was i--; continue; inside a for-loop where the loop update does i++.
                        // In Kotlin's while-loop, just continue re-examines the same index (now holding the next element).
                        continue
                    }
                    cvar.Reset()
                    i++
                }
            }

            companion object {
                private val instance: cmdFunction_t = Restart_f()
                fun getInstance(): cmdFunction_t {
                    return instance
                }
            }
        }

        companion object {
            // use a static dictionary to MoveCVarsToDict can be used from game
            private val moveCVarsToDict: idDict = idDict()

            //
            //public						~idCVarSystemLocal() {}
            //
            @Throws(idException::class)
            fun ListByFlags(args: CmdArgs.idCmdArgs,    /*cvarFlags_t*/flags: Int) {
                var i: Int
                var argNum: Int
                val match: idStr
                val indent = idStr()
                val str = idStr()
                var string: String
                var cvar: idInternalCVar
                val cvarList = idList<idInternalCVar>()
                argNum = 1
                var showType: show = show.SHOW_VALUE
                if (idStr.Icmp(args.Argv(argNum), "-") == 0 || idStr.Icmp(
                        args.Argv(argNum), "/"
                    ) == 0
                ) {
                    if (idStr.Icmp(args.Argv(argNum + 1), "help") == 0 || idStr.Icmp(
                            args.Argv(
                                argNum + 1
                            ), "?"
                        ) == 0
                    ) {
                        argNum = 3
                        showType = show.SHOW_DESCRIPTION
                    } else if (idStr.Icmp(
                            args.Argv(argNum + 1), "type"
                        ) == 0 || idStr.Icmp(args.Argv(argNum + 1), "range") == 0
                    ) {
                        argNum = 3
                        showType = show.SHOW_TYPE
                    } else if (idStr.Icmp(args.Argv(argNum + 1), "flags") == 0) {
                        argNum = 3
                        showType = show.SHOW_FLAGS
                    }
                }
                if (args.Argc() > argNum) {
                    match = idStr(args.Args(argNum, -1))
                    match.Replace(" ", "")
                } else {
                    match = idStr()
                }
                i = 0
                while (i < localCVarSystem.cvars.Num()) {
                    cvar = localCVarSystem.cvars[i]
                    if (0 == cvar.GetFlags() and flags) {
                        i++
                        continue
                    }
                    if (match.Length() != 0 && !cvar.nameString.Filter(match.toString(), false)) {
                        i++
                        continue
                    }
                    cvarList.Append(cvar)
                    i++
                }
                cvarList.Sort()
                when (showType) {
                    show.SHOW_VALUE -> {
                        i = 0
                        while (i < cvarList.Num()) {
                            cvar = cvarList[i]
                            idLib.common.Printf(
                                """
    $FORMAT_STRING${Str.S_COLOR_WHITE}"%s"
    
    """.trimIndent(), cvar.nameString, cvar.valueString
                            )
                            i++
                        }
                    }

                    show.SHOW_DESCRIPTION -> {
                        indent.Fill(' ', NUM_NAME_CHARS)
                        indent.Insert("\n", 0)
                        i = 0
                        while (i < cvarList.Num()) {
                            cvar = cvarList[i]
                            idLib.common.Printf(
                                """
    $FORMAT_STRING${Str.S_COLOR_WHITE}%s
    
    """.trimIndent(), cvar.nameString, CreateColumn(
                                    cvar.GetDescription()!!, NUM_DESCRIPTION_CHARS, indent.toString(), str
                                )
                            )
                            i++
                        }
                    }

                    show.SHOW_TYPE -> {
                        i = 0
                        while (i < cvarList.Num()) {
                            cvar = cvarList[i]
                            if (cvar.GetFlags() and CVAR_BOOL != 0) {
                                idLib.common.Printf(
                                    """
    $FORMAT_STRING${Str.S_COLOR_CYAN}bool
    
    """.trimIndent(), cvar.GetName()
                                )
                            } else if (cvar.GetFlags() and CVAR_INTEGER != 0) {
                                if (cvar.GetMinValue() < cvar.GetMaxValue()) {
                                    idLib.common.Printf(
                                        """
    $FORMAT_STRING${Str.S_COLOR_GREEN}int ${Str.S_COLOR_WHITE}[%d, %d]
    
    """.trimIndent(), cvar.GetName(), cvar.GetMinValue().toInt(), cvar.GetMaxValue().toInt()
                                    )
                                } else {
                                    idLib.common.Printf(
                                        """
    $FORMAT_STRING${Str.S_COLOR_GREEN}int
    
    """.trimIndent(), cvar.GetName()
                                    )
                                }
                            } else if (cvar.GetFlags() and CVAR_FLOAT != 0) {
                                if (cvar.GetMinValue() < cvar.GetMaxValue()) {
                                    idLib.common.Printf(
                                        """
    $FORMAT_STRING${Str.S_COLOR_RED}float ${Str.S_COLOR_WHITE}[%s, %s]
    
    """.trimIndent(), cvar.GetName(), idStr(cvar.GetMinValue()).toString(), idStr(cvar.GetMaxValue()).toString()
                                    )
                                } else {
                                    idLib.common.Printf(
                                        """
    $FORMAT_STRING${Str.S_COLOR_RED}float
    
    """.trimIndent(), cvar.GetName()
                                    )
                                }
                            } else if (cvar.GetValueStrings() != null) {
                                idLib.common.Printf(
                                    FORMAT_STRING + Str.S_COLOR_WHITE + "string " + Str.S_COLOR_WHITE + "[",
                                    cvar.GetName()
                                )
                                var j = 0
                                while (cvar.GetValueStrings()!![j] != null) {
                                    if (j != 0) {
                                        idLib.common.Printf(Str.S_COLOR_WHITE + ", %s", cvar.GetValueStrings()!![j]!!)
                                    } else {
                                        idLib.common.Printf(Str.S_COLOR_WHITE + "%s", cvar.GetValueStrings()!![j]!!)
                                    }
                                    j++
                                }
                                idLib.common.Printf(
                                    """
    ${Str.S_COLOR_WHITE}]
    
    """.trimIndent()
                                )
                            } else {
                                idLib.common.Printf(
                                    """
    $FORMAT_STRING${Str.S_COLOR_WHITE}string
    
    """.trimIndent(), cvar.GetName()
                                )
                            }
                            i++
                        }
                    }

                    show.SHOW_FLAGS -> {
                        i = 0
                        while (i < cvarList.Num()) {
                            cvar = cvarList[i]
                            idLib.common.Printf(FORMAT_STRING, cvar.GetName())
                            string = ""
                            string += if (cvar.GetFlags() and CVAR_BOOL != 0) {
                                Str.S_COLOR_CYAN + "B "
                            } else if (cvar.GetFlags() and CVAR_INTEGER != 0) {
                                Str.S_COLOR_GREEN + "I "
                            } else if (cvar.GetFlags() and CVAR_FLOAT != 0) {
                                Str.S_COLOR_RED + "F "
                            } else {
                                Str.S_COLOR_WHITE + "S "
                            }
                            string += if (cvar.GetFlags() and CVAR_SYSTEM != 0) {
                                Str.S_COLOR_WHITE + "SYS  "
                            } else if (cvar.GetFlags() and CVAR_RENDERER != 0) {
                                Str.S_COLOR_WHITE + "RNDR "
                            } else if (cvar.GetFlags() and CVAR_SOUND != 0) {
                                Str.S_COLOR_WHITE + "SND  "
                            } else if (cvar.GetFlags() and CVAR_GUI != 0) {
                                Str.S_COLOR_WHITE + "GUI  "
                            } else if (cvar.GetFlags() and CVAR_GAME != 0) {
                                Str.S_COLOR_WHITE + "GAME "
                            } else if (cvar.GetFlags() and CVAR_TOOL != 0) {
                                Str.S_COLOR_WHITE + "TOOL "
                            } else {
                                Str.S_COLOR_WHITE + "     "
                            }
                            string += if (cvar.GetFlags() and CVAR_USERINFO != 0) "UI " else "   "
                            string += if (cvar.GetFlags() and CVAR_SERVERINFO != 0) "SI " else "   "
                            string += if (cvar.GetFlags() and CVAR_STATIC != 0) "ST " else "   "
                            string += if (cvar.GetFlags() and CVAR_CHEAT != 0) "CH " else "   "
                            string += if (cvar.GetFlags() and CVAR_INIT != 0) "IN " else "   "
                            string += if (cvar.GetFlags() and CVAR_ROM != 0) "RO " else "   "
                            string += if (cvar.GetFlags() and CVAR_ARCHIVE != 0) "AR " else "   "
                            string += if (cvar.GetFlags() and CVAR_MODIFIED != 0) "MO " else "   "
                            string += "\n"
                            idLib.common.Printf("%s", string)
                            i++
                        }
                    }
                }
                idLib.common.Printf("\n%d cvars listed\n\n", cvarList.Num())
                idLib.common.Printf(
                    """
    listCvar [search string]          = list cvar values
    listCvar -help [search string]    = list cvar descriptions
    listCvar -type [search string]    = list cvar types
    listCvar -flags [search string]   = list cvar flags
    
    """.trimIndent()
                )
            }
        }

        init {
            cvars = idList()
            cvarHash = idHashIndex()
            modifiedFlags = 0
        }
    }

    /*
     ============
     idCVarSystemLocal::ListByFlags
     ============
     */
    // NOTE: the const wonkyness is required to make msvc happy
    class idListSortCompare : cmp_t<idInternalCVar> {
        override fun compare(a: idInternalCVar, b: idInternalCVar): Int {
            return idStr.Icmp(a.GetName(), b.GetName())
        }
    }

}
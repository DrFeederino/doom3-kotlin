package neo.Game.Script

import neo.Game.GameSys.Event.idEventDef
import neo.Game.GameSys.SaveGame.idRestoreGame
import neo.Game.GameSys.SaveGame.idSaveGame
import neo.Game.Game_local
import neo.Game.Game_local.Companion.isD3XP
import neo.Game.Game_local.idGameLocal.Companion.gameError
import neo.framework.File_h.idFile
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Str.idStr.Companion.CharIsPrintable
import neo.idlib.Text.btos
import neo.idlib.containers.CInt
import neo.idlib.containers.CPP_class
import neo.idlib.containers.List.idList
import neo.idlib.containers.idStrList
import neo.idlib.idException
import neo.idlib.idSerializable
import neo.idlib.math.idVec3
import neo.idlib.toBoolean
import neo.idlib.toInt
import java.nio.ByteBuffer
import java.nio.ByteOrder

object Script_Program {
    const val MAX_STRING_LEN = 128
    fun MAX_FUNCS(): Int {
        return if (isD3XP) 3584 else 3072    // D3XP: 3072 → 3584 (larger d3xp script library)
    }

    const val MAX_GLOBALS = 296608 // in bytes -- DG: increased for 64-bit compatibility (dhewm3 value);
    fun MAX_STATEMENTS(): Int {
        return if (isD3XP) 131072 else 81920// D3XP: 81920 → 131072
    }

    const val MAX_STRINGS = 1024
    const val SIZEOF_INTPTR = 8
    const val E_EVENT_SIZEOF_VEC = (idVec3.BYTES + (SIZEOF_INTPTR - 1)) and (SIZEOF_INTPTR - 1).inv()
    const val ev_argsize = 13
    const val ev_boolean = 14
    const val ev_entity = 6
    const val ev_error = -1
    const val ev_field = 7
    const val ev_float = 4
    const val ev_function = 8
    const val ev_jumpoffset = 12
    const val ev_namespace = 2
    const val ev_object = 11
    const val ev_pointer = 10
    const val ev_scriptevent = 1
    const val ev_string = 3
    const val ev_vector = 5
    const val ev_virtualfunction = 9
    const val ev_void = 0

    /* **********************************************************************

         Variable and type defintions

         ***********************************************************************/
    // simple types.  function types are dynamically allocated
    val type_argsize =
        idTypeDef(ev_argsize, "<argsize>", SIZEOF_INTPTR, null) // only used for function call and thread opcodes
    val type_boolean = idTypeDef(ev_boolean, "boolean", SIZEOF_INTPTR, null)
    val type_entity = idTypeDef(ev_entity, "entity", SIZEOF_INTPTR, null) // stored as entity number pointer
    val type_field = idTypeDef(ev_field, "field", SIZEOF_INTPTR, null)
    val type_float = idTypeDef(ev_float, "float", SIZEOF_INTPTR, null)
    val type_jumpoffset = idTypeDef(ev_jumpoffset, "<jump>", SIZEOF_INTPTR, null) // only used for jump opcodes
    val type_namespace = idTypeDef(ev_namespace, "namespace", SIZEOF_INTPTR, null)
    val type_object = idTypeDef(ev_object, "object", SIZEOF_INTPTR, null) // stored as entity number pointer
    val type_pointer = idTypeDef(ev_pointer, "pointer", SIZEOF_INTPTR, null)
    val type_scriptevent = idTypeDef(ev_scriptevent, "scriptevent", SIZEOF_INTPTR, null)
    val type_string = idTypeDef(ev_string, "string", MAX_STRING_LEN, null)
    val type_vector = idTypeDef(ev_vector, "vector", E_EVENT_SIZEOF_VEC, null)
    val type_virtualfunction = idTypeDef(ev_virtualfunction, "virtual function", SIZEOF_INTPTR, null)
    val type_void = idTypeDef(ev_void, "void", 0, null)
    val type_function = idTypeDef(ev_function, "function", SIZEOF_INTPTR, type_void)

    val def_entity = idVarDef(type_entity)
    val def_argsize = idVarDef(type_argsize)
    val def_boolean = idVarDef(type_boolean)
    val def_field = idVarDef(type_field)
    val def_float = idVarDef(type_float)
    val def_jumpoffset = idVarDef(type_jumpoffset) // only used for jump opcodes
    val def_namespace = idVarDef(type_namespace)
    val def_object = idVarDef(type_object)
    val def_pointer = idVarDef(type_pointer)
    val def_scriptevent = idVarDef(type_scriptevent)
    val def_string = idVarDef(type_string)
    val def_vector = idVarDef(type_vector)
    val def_virtualfunction = idVarDef(type_virtualfunction)
    val def_function = idVarDef(type_function)
    val def_void = idVarDef(type_void)

    init {
        type_void.def = def_void
        type_scriptevent.def = def_scriptevent
        type_namespace.def = def_namespace
        type_string.def = def_string
        type_float.def = def_float
        type_vector.def = def_vector
        type_entity.def = def_entity
        type_field.def = def_field
        type_function.def = def_function
        type_virtualfunction.def = def_virtualfunction
        type_pointer.def = def_pointer
        type_object.def = def_object
        type_jumpoffset.def = def_jumpoffset
        type_argsize.def = def_argsize
        type_boolean.def = def_boolean
    }

    /* **********************************************************************

     function_t

     ***********************************************************************/
    class function_t : idSerializable {
        var def: idVarDef? = null

        //
        var eventdef: idEventDef? = null
        var filenum = 0 // source file defined in
        var firstStatement = 0
        var locals = 0 // total ints of parms + locals
        var numStatements = 0 //TODO:booleans?
        val parmSize = idList<Int>()
        var parmTotal = 0
        var type: idTypeDef? = null
        private val name = idStr()

        //
        //
        init {
            Clear()
        }

        fun  /*size_t*/Allocated(): Int {
            return name.Allocated() + parmSize.Allocated()
        }

        fun SetName(name: String?) {
            this.name.set(name)
        }

        fun Name(): String {
            return name.toString()
        }

        fun Clear() {
            eventdef = null
            def = null
            type = null
            firstStatement = 0
            numStatements = 0
            parmTotal = 0
            locals = 0
            filenum = 0
            name.Clear()
            parmSize.Clear()
        }

        override fun readFrom(file: idFile) {
            name.readFrom(file)
            file.ReadInt() //skip eventdef pointer
            file.ReadInt() //skip def pointer
            file.ReadInt() //skip type pointer
            firstStatement = file.ReadInt()
            numStatements = file.ReadInt()
            parmTotal = file.ReadInt()
            locals = file.ReadInt()
            filenum = file.ReadInt()
        }

        override fun writeTo(file: idFile) {
            name.writeTo(file)
            file.WriteInt(0) // eventdef pointer
            file.WriteInt(0) // def pointer
            file.WriteInt(0) // type pointer
            file.WriteInt(firstStatement)
            file.WriteInt(numStatements)
            file.WriteInt(parmTotal)
            file.WriteInt(locals)
            file.WriteInt(filenum)
            // parmSize idList - write num and pointer
            file.WriteInt(parmSize.Num())
            file.WriteInt(0) // list data pointer
        }

        companion object {
            val SIZE = (idStr.SIZE + CPP_class.POINTER_SIZE //eventdef
                    + CPP_class.POINTER_SIZE //def
                    + CPP_class.POINTER_SIZE //type
                    + Integer.SIZE + Integer.SIZE + Integer.SIZE + Integer.SIZE + Integer.SIZE + idList.SIZE)
            val BYTES = SIZE / java.lang.Byte.SIZE
        }
    }

    class eval_s {
        var _float: Float = 0.0f
        var _int: Int = 0
        var entity: Int = 0
        var function: Array<function_t>? = null
        var stringPtr: Array<String>? = null
        var vector: FloatArray = FloatArray(3)
    }

    /* **********************************************************************

     idTypeDef

     Contains type information for variables and functions.

     ***********************************************************************/
    class idTypeDef {
        //
        var def: idVarDef? = null // a def that points to this type

        //
        // function types are more complex
        private var auxType: idTypeDef? = null // return type
        private val functions = idList<function_t?>()
        private var name: idStr? = null
        private var parmNames = idStrList()
        private val parmTypes = idList<idTypeDef?>()
        private var size = 0
        private var   /*etype_t*/type = 0

        //
        //
        constructor(other: idTypeDef) {
            this.set(other)
        }

        constructor(  /*etype_t*/etype: Int, edef: idVarDef?, ename: String?, esize: Int, aux: idTypeDef?) {
            name = idStr(ename!!)
            type = etype
            def = edef
            size = esize
            auxType = aux
            parmTypes.SetGranularity(1)
            parmNames.SetGranularity(1)
            functions.SetGranularity(1)
        }

        constructor(  /*etype_t*/etype: Int, ename: String?, esize: Int, aux: idTypeDef?) {
            name = idStr(ename!!)
            type = etype
            def = idVarDef(this)
            size = esize
            auxType = aux
            parmTypes.SetGranularity(1)
            parmNames.SetGranularity(1)
            functions.SetGranularity(1)
        }

        fun set(other: idTypeDef) {
            type = other.type
            def = other.def
            name = other.name
            size = other.size
            auxType = other.auxType
            parmTypes.set(other.parmTypes)
            parmNames = other.parmNames
            functions.set(other.functions)
        }

        fun  /*size_t*/Allocated(): Int {
            var   /*size_t*/memsize: Int
            var i: Int
            memsize = name!!.Allocated() + parmTypes.Allocated() + parmNames.size() + functions.Allocated()
            i = 0
            while (i < parmTypes.Num()) {
                memsize += parmNames[i].Allocated()
                i++
            }
            return memsize
        }

        /*
         ================
         idTypeDef::Inherits

         Returns true if basetype is an ancestor of this type.
         ================
         */
        fun Inherits(basetype: idTypeDef?): Boolean {
            var superType: idTypeDef?
            if (type != ev_object) {
                return false
            }
            if (this == basetype) {
                return true
            }
            superType = auxType
            while (superType != null) {
                if (superType == basetype) {
                    return true
                }
                superType = superType.auxType
            }
            return false
        }

        /*
         ================
         idTypeDef::MatchesType

         Returns true if both types' base types and parameters match
         ================
         */
        fun MatchesType(matchtype: idTypeDef): Boolean {
            var i: Int
            if (this == matchtype) {
                return true
            }
            if (type != matchtype.type || auxType != matchtype.auxType) {
                return false
            }
            if (parmTypes.Num() != matchtype.parmTypes.Num()) {
                return false
            }
            i = 0
            while (i < matchtype.parmTypes.Num()) {
                if (parmTypes[i] != matchtype.parmTypes[i]) {
                    return false
                }
                i++
            }
            return true
        }

        /*
         ================
         idTypeDef::MatchesVirtualFunction

         Returns true if both functions' base types and parameters match
         ================
         */
        fun MatchesVirtualFunction(matchfunc: idTypeDef?): Boolean {
            var i: Int
            if (this == matchfunc) {
                return true
            }
            if (type != matchfunc!!.type || auxType != matchfunc.auxType) {
                return false
            }
            if (parmTypes.Num() != matchfunc.parmTypes.Num()) {
                return false
            }
            if (parmTypes.Num() > 0) {
                if (!parmTypes[0]!!.Inherits(matchfunc.parmTypes[0])) {
                    return false
                }
            }
            i = 1
            while (i < matchfunc.parmTypes.Num()) {
                if (parmTypes[i] != matchfunc.parmTypes[i]) {
                    return false
                }
                i++
            }
            return true
        }

        /*
         ================
         idTypeDef::AddFunctionParm

         Adds a new parameter for a function type.
         ================
         */
        fun AddFunctionParm(parmtype: idTypeDef?, name: String?) {
            if (type != ev_function) {
                throw idCompileError("idTypeDef::AddFunctionParm : tried to add parameter on non-function type")
            }
            parmTypes.Append(parmtype)
            val parmName = parmNames.addEmptyStr()
            parmName.set(name)
        }

        /*
         ================
         idTypeDef::AddField

         Adds a new field to an object type.
         ================
         */
        fun AddField(fieldtype: idTypeDef?, name: String?) {
            if (type != ev_object) {
                throw idCompileError("idTypeDef::AddField : tried to add field to non-object type")
            }
            parmTypes.Append(fieldtype)
            val parmName = parmNames.addEmptyStr()
            parmName.set(name)
            size += if (fieldtype!!.FieldType()!!.Inherits(type_object)) {
                type_object.Size()
            } else {
                fieldtype.FieldType()!!.Size()
            }
        }

        fun SetName(newname: String?) {
            name!!.set(newname)
        }

        fun Name(): String {
            return name.toString()
        }

        fun  /*etype_t*/Type(): Int {
            return type
        }

        fun Size(): Int {
            return size
        }

        /*
         ================
         idTypeDef::SuperClass

         If type is an object, then returns the object's superclass
         ================
         */
        fun SuperClass(): idTypeDef? {
            if (type != ev_object) {
                throw idCompileError("idTypeDef::SuperClass : tried to get superclass of a non-object type")
            }
            return auxType
        }

        /*
         ================
         idTypeDef::ReturnType

         If type is a function, then returns the function's return type
         ================
         */
        fun ReturnType(): idTypeDef? {
            if (type != ev_function) {
                throw idCompileError("idTypeDef::ReturnType: tried to get return type on non-function type")
            }
            return auxType
        }

        /*
         ================
         idTypeDef::SetReturnType

         If type is a function, then sets the function's return type
         ================
         */
        fun SetReturnType(returntype: idTypeDef?) {
            if (type != ev_function) {
                throw idCompileError("idTypeDef::SetReturnType: tried to set return type on non-function type")
            }
            auxType = returntype
        }

        /*
         ================
         idTypeDef::FieldType

         If type is a field, then returns it's type
         ================
         */
        fun FieldType(): idTypeDef? {
            if (type != ev_field) {
                throw idCompileError("idTypeDef::FieldType: tried to get field type on non-field type")
            }
            return auxType
        }

        /*
         ================
         idTypeDef::SetFieldType

         If type is a field, then sets the function's return type
         ================
         */
        fun SetFieldType(fieldtype: idTypeDef?) {
            if (type != ev_field) {
                throw idCompileError("idTypeDef::SetFieldType: tried to set return type on non-function type")
            }
            auxType = fieldtype
        }

        /*
         ================
         idTypeDef::PointerType

         If type is a pointer, then returns the type it points to
         ================
         */
        fun PointerType(): idTypeDef? {
            if (type != ev_pointer) {
                throw idCompileError("idTypeDef::PointerType: tried to get pointer type on non-pointer")
            }
            return auxType
        }

        /*
         ================
         idTypeDef::SetPointerType

         If type is a pointer, then sets the pointer's type
         ================
         */
        fun SetPointerType(pointertype: idTypeDef?) {
            if (type != ev_pointer) {
                throw idCompileError("idTypeDef::SetPointerType: tried to set type on non-pointer")
            }
            auxType = pointertype
        }

        fun NumParameters(): Int {
            return parmTypes.Num()
        }

        fun GetParmType(parmNumber: Int): idTypeDef? {
            assert(parmNumber >= 0)
            assert(parmNumber < parmTypes.Num())
            return parmTypes[parmNumber]
        }

        fun GetParmName(parmNumber: Int): String {
            assert(parmNumber >= 0)
            assert(parmNumber < parmTypes.Num())
            return parmNames[parmNumber].toString()
        }

        fun NumFunctions(): Int {
            return functions.Num()
        }

        fun GetFunctionNumber(func: function_t?): Int {
            var i: Int
            i = 0
            while (i < functions.Num()) {
                if (functions[i] == func) {
                    return i
                }
                i++
            }
            return -1
        }

        fun GetFunction(funcNumber: Int): function_t? {
            assert(funcNumber >= 0)
            assert(funcNumber < functions.Num())
            return functions[funcNumber]
        }

        fun AddFunction(func: function_t?) {
            var i: Int
            i = 0
            while (i < functions.Num()) {
                if (functions[i]!!.def!!.Name() == func!!.def!!.Name()) {
                    if (func.def!!.TypeDef()!!.MatchesVirtualFunction(functions[i]!!.def!!.TypeDef())) {
                        functions[i] = func
                        return
                    }
                }
                i++
            }
            functions.Append(func)
        }

        companion object {
            const val BYTES = Integer.BYTES * 8 //TODO:<-
        }
    }

    /* **********************************************************************

     idScriptObject

     In-game representation of objects in scripts.  Use the idScriptVariable template
     (below) to access variables.

     ***********************************************************************/
    class idScriptObject : idSerializable {
        //
        var data: ByteBuffer? = null
        var offset = 0
        private var type: idTypeDef

        //
        //
        init {
            type = type_object
        }

        // ~idScriptObject();
        fun Save(savefile: idSaveGame) {            // archives object for save game file
            val   /*size_t*/size: Int
            if (type == type_object && data == null) {
                // Write empty string for uninitialized object
                savefile.WriteString("")
            } else {
                savefile.WriteString(type.Name())
                size = type.Size()
                savefile.WriteInt(size)
                data!!.position(0) // C++ writes from data[0]; ensure position is at start
                savefile.Write(data!!, size)
            }
        }

        fun Restore(savefile: idRestoreGame) {            // unarchives object from save game file
            val typeName = idStr()
            val size = CInt()
            savefile.ReadString(typeName)

            // Empty string signals uninitialized object
            if (typeName.Length() == 0) {
                return
            }
            if (!SetType(typeName.toString())) {
                savefile.Error("idScriptObject::Restore: failed to restore object of type '%s'.", typeName.toString())
            }
            savefile.ReadInt(size)
            if (size._val != type.Size()) {
                savefile.Error(
                    "idScriptObject::Restore: size of object '%s' doesn't match size in save game.", typeName
                )
            }
            savefile.Read(data!!, size._val)
        }

        fun Free() {
//            if (data != null) {
//                Mem_Free(data);
//            }
            data = null
            type = type_object
        }

        /*
         ============
         idScriptObject::SetType

         Allocates an object and initializes memory.
         ============
         */
        fun SetType(typeName: String?): Boolean {
            val   /*size_t*/size: Int
            val newType: idTypeDef?

            // lookup the type
            newType = Game_local.gameLocal.program.FindType(typeName)

            // only allocate memory if the object type changes
            if (newType != type) {
                Free()
                if (null == newType) {
                    Game_local.gameLocal.Warning("idScriptObject::SetType: Unknown type '%s'", typeName)
                    return false
                }
                if (!newType.Inherits(type_object)) {
                    Game_local.gameLocal.Warning(
                        "idScriptObject::SetType: Can't create object of type '%s'.  Must be an object type.",
                        newType.Name()
                    )
                    return false
                }

                // set the type
                type = newType

                // allocate the memory
                size = type.Size()
                data = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN) // Mem_Alloc(size);
            }

            // init object memory
            ClearObject()
            return true
        }

        /*
         ============
         idScriptObject::ClearObject

         Resets the memory for the script object without changing its type.
         ============
         */
        fun ClearObject() {
            val   /*size_t*/size: Int
            if (type != type_object) {
                // init object memory
                size = type.Size()
                //		memset( data, 0, size );
                for (i in 0 until size) {
                    data!!.put(i, 0)
                }
            }
        }

        fun HasObject(): Boolean {
            return type != type_object
        }

        fun GetTypeDef(): idTypeDef {
            return type
        }

        fun GetTypeName(): String {
            return type.Name()
        }

        fun GetConstructor(): function_t? {
            val func: function_t?
            func = GetFunction("init")
            return func
        }

        fun GetDestructor(): function_t? {
            val func: function_t?
            func = GetFunction("destroy")
            return func
        }

        fun GetFunction(name: String?): function_t? {
            val func: function_t?
            if (type == type_object) {
                return null
            }
            func = Game_local.gameLocal.program.FindFunction(name, type)
            return func
        }

        fun GetVariable(name: String,   /*etype_t*/etype: Int): ByteBuffer? {
            var i: Int
            var pos: Int
            var t: idTypeDef?
            var parm: idTypeDef?
            if (type == type_object) {
                return null
            }
            t = type
            do {
                pos = if (t!!.SuperClass() != type_object) {
                    t.SuperClass()!!.Size()
                } else {
                    0
                }
                i = 0
                while (i < t.NumParameters()) {
                    parm = t.GetParmType(i)
                    if (t.GetParmName(i) == name) {
                        return if (etype != parm!!.FieldType()!!.Type()) {
                            null
                        } else data!!.duplicate().order(ByteOrder.LITTLE_ENDIAN).position(pos).slice()
                            .order(ByteOrder.LITTLE_ENDIAN)
                    }
                    pos += if (parm!!.FieldType()!!.Inherits(type_object)) {
                        type_object.Size()
                    } else {
                        parm.FieldType()!!.Size()
                    }
                    i++
                }
                t = t.SuperClass()
            } while (t != null && t != type_object)
            return null
        }

        override fun readFrom(file: idFile) {
            file.ReadInt() // data pointer, skip
            offset = file.ReadInt()
            file.ReadInt() // type pointer, skip
            file.ReadInt() // padding
        }

        override fun writeTo(file: idFile) {
            file.WriteInt(0) // data pointer
            file.WriteInt(offset)
            file.WriteInt(0) // type pointer
            file.WriteInt(0) // padding
        }

        companion object {
            val BYTES = 16
        }
    }

    /* **********************************************************************

     idScriptVariable

     Helper template that handles looking up script variables stored in objects.
     If the specified variable doesn't exist, or is the wrong data type, idScriptVariable
     will cause an error.

     ***********************************************************************/
    open class idScriptVariable<type, returnType>    //
    //
    //        public idScriptVariable() {
    //            etype = null;
    //            data = null;
    //        }
    //        
        (  /*etype_t*//*etype_t*/protected val etype: Int) {
        private var data: ByteBuffer? = null
        fun IsLinked(): Boolean {
            return data != null
        }

        fun Unlink() {
            data = null
        }

        fun LinkTo(obj: idScriptObject, name: String) {
            data = obj.GetVariable(name, etype) //TODO:convert bytes to type
            if (null == data) {
                gameError("Missing '%s' field in script object '%s'", name, obj.GetTypeName())
            }
        }

        fun set(value: returnType): idScriptVariable<*, *> {
            // check if we attempt to access the object before it's been linked
            assert(data != null)

            // make sure we don't crash if we don't have a pointer
            if (data != null) {
                val pos = data!!.position()
                when (etype) {
                    ev_boolean -> data!!.putInt(pos, ((value as Boolean)).toInt())
                    ev_float -> data!!.putFloat((value as Float))
                }
                data!!.position(pos)
            }
            return this
        }

        fun underscore(): returnType? {
            // check if we attempt to access the object before it's been linked
            assert(data != null)

            // make sure we don't crash if we don't have a pointer
            return if (data != null) {
                val pos = data!!.position()
                when (etype) {
                    ev_boolean -> (data!!.getInt(pos)).toBoolean() as returnType
                    ev_float -> data!!.getFloat(pos) as returnType
                    else -> null
                }
            } else {
                // reasonably safe value
                null
            }
        }

        fun underscore(bla: returnType) {
            this.set(bla)
        }
    }

    /* **********************************************************************

     Script object variable access template instantiations

     These objects will automatically handle looking up of the current value
     of a variable in a script object.  They can be stored as part of a class
     for up-to-date values of the variable, or can be used in functions to
     sample the data for non-dynamic values.

     ***********************************************************************/
    class idScriptBool : idScriptVariable<Boolean?, Boolean?>(ev_boolean)
    class idScriptFloat : idScriptVariable<Float?, Float?>(ev_float)
    private class idScriptInt : idScriptVariable<Float?, Int?>(ev_float)
    private class idScriptVector : idScriptVariable<idVec3?, idVec3?>(ev_vector)
    private class idScriptString : idScriptVariable<idStr?, String?>(ev_string)

    /* **********************************************************************

     idCompileError

     Causes the compiler to exit out of compiling the current function and
     display an error message with line and file info.

     ***********************************************************************/
    class idCompileError(text: String?) : idException(text!!)

    /* **********************************************************************

     idVarDef

     Define the name, type, and location of variables, functions, and objects
     defined in script.

     ***********************************************************************/
    class  /*union*/ varEval_s {
        //        final         int[]          intPtr;
        //        final         ByteBuffer     bytePtr;
        //        private int virtualFunction;
        //        private int jumpOffset;
        //        private int stackOffset;		// offset in stack for local variables
        //        private int argSize;
        var evalPtr: varEval_s? = null
        var functionPtr: function_t? = null
        var objectPtrPtr: idScriptObject? = null
        var stringPtr: String? = null

        //        final         float[]        floatPtr;
        val vectorPtr = idVec3()
        private var offset = 0

        //        private int ptrOffset;
        private var primitive = ByteBuffer.allocate(java.lang.Float.BYTES * 3).order(ByteOrder.LITTLE_ENDIAN)
        var virtualFunction: Int
            get() = getPrimitive()
            set(value) {
                setPrimitive(value)
            }
        var jumpOffset: Int
            get() = getPrimitive()
            set(value) {
                setPrimitive(value)
            }
        var stackOffset: Int
            get() = getPrimitive()
            set(value) {
                setPrimitive(value)
            }
        var argSize: Int
            get() = getPrimitive()
            set(value) {
                setPrimitive(value)
            }
        var ptrOffset: Int
            get() = getPrimitive()
            set(value) {
                setPrimitive(value)
            }
        var evalPointerOffset: Int
            get() = primitive.getInt(4)
            set(value) {
                primitive.putInt(4, value)
            }

        private fun getPrimitive(): Int {
            return primitive.getInt(0)
        }

        private fun setPrimitive(`val`: Int) {
            primitive.putInt(0, `val`)
        }

        fun setIntPtr(`val`: ByteArray?, offset: Int) {
            setBytePtr(ByteBuffer.wrap(`val`), offset)
        }

        fun getVectorPtrs(): idVec3 {
            vectorPtr[0] = primitive.getFloat(0)
            vectorPtr[1] = primitive.getFloat(4)
            vectorPtr[2] = primitive.getFloat(8)
            return vectorPtr
        }

        fun setVectorPtr(vector: idVec3) {
            setVectorPtr(vector.ToFloatPtr())
        }

        fun setVectorPtr(vector: FloatArray) {
            vectorPtr.set(idVec3(vector))
            primitive.putFloat(0, vector[0])
            primitive.putFloat(4, vector[1])
            primitive.putFloat(8, vector[2])
        }

        var intPtr: Int
            get() = primitive.getInt(0)
            set(value) {
                setPrimitive(value)
            }
        var floatPtr: Float
            get() = primitive.getFloat(0)
            set(value) {
                primitive.putFloat(0, value)
            }
        var entityNumberPtr: Int
            get() = intPtr
            set(value) {
                setPrimitive(value)
            }

        fun setBytePtr(bytes: ByteBuffer?, offset: Int) {
            this.offset = offset
            primitive = bytes!!.duplicate().order(ByteOrder.LITTLE_ENDIAN).position(offset).slice()
                .order(ByteOrder.LITTLE_ENDIAN)
        }

        fun setBytePtr(bytes: ByteArray?, offset: Int) {
            setBytePtr(ByteBuffer.wrap(bytes), offset)
        }

        fun setStringPtr(data: ByteBuffer?, offset: Int) {
            stringPtr = btos(data!!.array(), offset)
        }

        fun setString(string: String?) {
            // C++ uses idStr::Copynz(stringPtr, src, MAX_STRING_LEN) — null-terminates and zero-pads
            stringPtr = string
            val bytes = (string ?: "").toByteArray()
            val copyLen = Math.min(bytes.size, Math.min(primitive.capacity(), MAX_STRING_LEN) - 1)
            primitive.rewind()
            primitive.put(bytes, 0, copyLen)
            // null-terminate and zero-fill remaining space
            val remaining = Math.min(primitive.remaining(), MAX_STRING_LEN - copyLen)
            for (i in 0 until remaining) {
                primitive.put(0.toByte())
            }
            primitive.rewind()
        }

        fun setEvalPtr(entityNumberIndex: Int, ptrOffset: Int = 0) {
            entityNumberPtr = entityNumberIndex
            evalPointerOffset = ptrOffset
        }

        companion object {
            const val BYTES = SIZEOF_INTPTR  // C++ sizeof(intptr_t) = 8 on 64-bit
        }
    }

    class idVarDef( //
        private var typeDef: idTypeDef? = null /*= NULL*/
    ) {
        //
        var initialized: initialized_t
        var num = 0
        var numUsers // number of users if this is a constant
                = 0
        var scope // function, namespace, or object the var was defined in
                : idVarDef? = null

        //
        var value: varEval_s? = null
        var name // name of this var
                : idVarDefName? = null
        var next // next var with the same name
                : idVarDef? = null

        //
        //
        init {
            initialized = initialized_t.uninitialized
            //	memset( &value, 0, sizeof( value ) );
        }

        // ~idVarDef();
        fun close() {
            name?.RemoveDef(this)
        }

        fun Name(): String {
            return name!!.Name()
        }

        fun GlobalName(): String {
            return if (scope != def_namespace) {
                String.format("%s::%s", scope!!.GlobalName(), name!!.Name())
            } else {
                name!!.Name()
            }
        }

        fun SetTypeDef(_type: idTypeDef?) {
            typeDef = _type
        }

        fun TypeDef(): idTypeDef? {
            return typeDef
        }

        fun  /*etype_t*/Type(): Int {
            return if (typeDef != null) typeDef!!.Type() else ev_void
        }

        fun DepthOfScope(otherScope: idVarDef?): Int {
            var def: idVarDef?
            var depth: Int
            depth = 1
            def = otherScope
            while (def != null) {
                if (def == scope) {
                    return depth
                }
                depth++
                def = def.scope
            }
            return 0
        }

        fun SetFunction(func: function_t) {
            assert(typeDef != null)
            initialized = initialized_t.initializedConstant
            assert(typeDef!!.Type() == ev_function)
            value = varEval_s()
            value!!.functionPtr = func
        }

        fun SetObject(`object`: idScriptObject?) {
            assert(typeDef != null)
            assert(typeDef!!.Inherits(type_object))
            value = varEval_s()
            value!!.objectPtrPtr = `object`
        }

        fun SetValue(_value: eval_s?, constant: Boolean) {
            assert(typeDef != null)
            initialized = if (constant) {
                initialized_t.initializedConstant
            } else {
                initialized_t.initializedVariable
            }
            when (typeDef!!.Type()) {
                ev_pointer, ev_boolean, ev_field -> value!!.intPtr = _value!!._int
                ev_jumpoffset -> value!!.jumpOffset = _value!!._int
                ev_argsize -> value!!.argSize = _value!!._int
                ev_entity -> value!!.entityNumberPtr = _value!!.entity
                ev_string -> value!!.stringPtr =
                    _value!!.stringPtr!![0] //idStr.Copynz(value.stringPtr, _value.stringPtr, MAX_STRING_LEN);
                ev_float -> value!!.floatPtr = _value!!._float
                ev_vector -> value!!.setVectorPtr(_value!!.vector)
                ev_function -> value!!.functionPtr = _value!!.function!![0]
                ev_virtualfunction -> value!!.virtualFunction = _value!!._int
                ev_object -> value!!.entityNumberPtr = _value!!.entity
                else -> throw idCompileError(String.format("weird type on '%s'", Name()))
            }
        }

        fun SetString(string: String?, constant: Boolean) {
            initialized = if (constant) {
                initialized_t.initializedConstant
            } else {
                initialized_t.initializedVariable
            }
            assert(typeDef != null && typeDef!!.Type() == ev_string)
            value!!.stringPtr = string
        }

        fun Next(): idVarDef? {
            return next
        } // next var def with same name

        fun PrintInfo(file: idFile, instructionPointer: Int) {
            val jumpst: statement_s?
            val jumpto: Int
            val   /*etype_t*/etype: Int
            if (initialized == initialized_t.initializedConstant) {
                file.Printf("const ")
            }
            etype = typeDef!!.Type()
            when (etype) {
                ev_jumpoffset -> {
                    jumpto = instructionPointer + value!!.jumpOffset
                    jumpst = Game_local.gameLocal.program.GetStatement(jumpto)
                    file.Printf(
                        "address %d [%s(%d)]",
                        jumpto,
                        Game_local.gameLocal.program.GetFilename(jumpst.file),
                        jumpst.linenumber
                    )
                }

                ev_function -> if (value!!.functionPtr!!.eventdef != null) {
                    file.Printf("event %s", GlobalName())
                } else {
                    file.Printf("function %s", GlobalName())
                }

                ev_field -> file.Printf("field %d", value!!.ptrOffset)
                ev_argsize -> file.Printf("args %d", value!!.argSize)
                else -> {
                    file.Printf("%s ", typeDef!!.Name())
                    if (initialized == initialized_t.initializedConstant) {
                        when (etype) {
                            ev_string -> {
                                file.Printf("\"")
                                for (ch in value!!.stringPtr!!.toCharArray()) {
                                    if (CharIsPrintable(ch.code)) {
                                        file.Printf("%c", ch)
                                    } else if (ch == '\n') {
                                        file.Printf("\\n")
                                    } else {
                                        file.Printf("\\x%.2x", ch.code)
                                    }
                                }
                                file.Printf("\"")
                            }

                            ev_vector -> file.Printf("'%s'", value!!.getVectorPtrs().ToString())
                            ev_float -> file.Printf("%f", value!!.floatPtr)
                            ev_virtualfunction -> file.Printf("vtable[ %d ]", value!!.virtualFunction)
                            else -> file.Printf("%d", value!!.intPtr)
                        }
                    } else if (initialized == initialized_t.stackVariable) {
                        file.Printf("stack[%d]", value!!.stackOffset)
                    } else {
                        file.Printf("global[%d]", num)
                    }
                }
            }
        }

        enum class initialized_t {
            uninitialized,
            initializedVariable,
            initializedConstant,
            stackVariable
        }

        companion object {
            // friend class idVarDefName;
            const val BYTES = (Integer.BYTES + varEval_s.BYTES + Integer.BYTES + Integer.BYTES)
        }
    }

    /* **********************************************************************

     idVarDefName

     ***********************************************************************/
    class idVarDefName {
        private var defs: idVarDef?
        private val name = idStr()

        //
        //
        constructor() {
            defs = null
        }

        constructor(n: String?) {
            name.set(n)
            defs = null
        }

        fun Name(): String {
            return name.toString()
        }

        fun GetDefs(): idVarDef? {
            return defs
        }

        fun AddDef(def: idVarDef) {
            assert(def.next == null)
            def.name = this
            def.next = defs
            defs = def
        }

        fun RemoveDef(def: idVarDef) {
            if (defs == def) {
                defs = def.next
            } else {
                var d = defs
                while (d!!.next != null) {
                    if (d.next == def) {
                        d.next = def.next
                        break
                    }
                    d = d.next
                }
            }
            def.next = null
            def.name = null
        }
    }

    class statement_s {
        var a: idVarDef? = null
        var b: idVarDef? = null
        var c: idVarDef? = null
        var file = 0
        var linenumber = 0
        var   /*unsigned short*/op = 0
        var   /*unsigned short*/flags = 0 // DG: added for savegame compat hack

        companion object {
            const val FLAG_OBJECTCALL_IMPL_NOT_PARSED_YET = 1
        }
    }
}

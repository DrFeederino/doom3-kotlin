package neo.ui

import neo.Renderer.Material.idMaterial
import neo.TempDump.atoi
import neo.framework.DeclManager
import neo.framework.File_h.idFile
import neo.idlib.Dict_h.idDict
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Str.va
import neo.idlib.containers.List.idList
import neo.idlib.math.idVec2
import neo.idlib.math.idVec3
import neo.idlib.math.idVec4
import neo.ui.Rectangle.idRectangle
import neo.ui.Window.idWindow
import java.util.*

object Winvar {
    val MIN_ONE: idWinVar = idWinInt(-1)
    val MIN_TWO: idWinVar = idWinInt(-2)
    const val VAR_GUIPREFIX = "gui::"
    const val VAR_GUIPREFIX_LEN = VAR_GUIPREFIX.length

    abstract class idWinVar // public   ~idWinVar();
    {
        protected var eval = true
        protected var guiDict: idDict? = null
        protected var name: String? = null
        fun SetGuiInfo(gd: idDict?, _name: String?) {
            guiDict = gd
            SetName(_name)
        }

        fun GetName(): String {
            return if (name != null) {
                if (guiDict != null && name!![0] == '*') {
                    guiDict!!.GetString(name!!.substring(1))
                } else name!!
            } else ""
        }

        fun SetName(_name: String?) {
            name = _name
        }

        // idWinVar &operator=( final idWinVar other );
        fun set(other: idWinVar?): idWinVar {
            guiDict = other!!.guiDict
            SetName(other.name)
            return this
        }

        fun GetDict(): idDict? {
            return guiDict
        }

        fun NeedsUpdate(): Boolean {
            return guiDict != null
        }

        open fun Init(_name: String?, win: idWindow?) {
            var key = idStr(_name!!)
            guiDict = null
            val len = key.Length()
            if (len > 5 && _name.startsWith("gui:")) {
                key = key.Right(len - VAR_GUIPREFIX_LEN)
                SetGuiInfo(win!!.GetGui().GetStateDict(), key.toString())
                win.AddUpdateVar(this)
            } else {
                Set(_name)
            }
        }

        abstract fun Set(`val`: String?)
        fun Set(`val`: idStr) {
            Set(`val`.toString())
        }

        abstract fun Update()
        abstract fun c_str(): String?
        open fun  /*size_t*/Size(): Int {
            return if (name != null) name!!.length else 0
        }

        abstract fun WriteToSaveGame(savefile: idFile)
        abstract fun ReadFromSaveGame(savefile: idFile)
        abstract fun x(): Float
        fun SetEval(b: Boolean) {
            eval = b
        }

        fun GetEval(): Boolean {
            return eval
        }

        override fun toString(): String {
            return "idWinVar{guiDict=$guiDict, name=$name}"
        }

        companion object {
            @Deprecated("calling this function in idWindow::EmitOp hides the loading bar progress.")
            fun clone(`var`: idWinVar?): idWinVar? {
                if (`var` == null) return null
                if (`var` is idWinBool) {
                    return idWinBool(`var`)
                }
                if (`var` is idWinBackground) {
                    return idWinBackground(`var`)
                }
                if (`var` is idWinFloat) {
                    return idWinFloat(`var`)
                }
                if (`var` is idWinInt) {
                    return idWinInt(`var`.data)
                }
                if (`var` is idWinRectangle) {
                    return idWinRectangle(`var`)
                }
                if (`var` is idWinStr) {
                    return idWinStr(`var`)
                }
                if (`var` is idWinVec2) {
                    return idWinVec2(`var`.data)
                }
                if (`var` is idWinVec3) {
                    return idWinVec3(`var`)
                }
                if (`var` is idWinVec4) {
                    return idWinVec4(`var`)
                }
                throw UnsupportedOperationException()
            }
        }
    }

    class idWinBool : idWinVar {
        var data = false

        //
        //
        constructor() : super()
        constructor(a: Boolean) : this() {
            data = a
        }

        //copy constructor
        constructor(winBool: idWinBool) {
            super.set(winBool)
            data = winBool.data
        }

        // ~idWinBool() {};
        override fun Init(_name: String?, win: idWindow?) {
            super.Init(_name, win)
            if (guiDict != null) {
                data = guiDict!!.GetBool(GetName())
            }
        }

        //	int	operator==(	const bool &other ) { return (other == data); }
        override fun hashCode(): Int {
            var hash = 7
            hash = 37 * hash + if (data) 1 else 0
            return hash
        }

        override fun equals(obj: Any?): Boolean {
            if (obj == null) {
                return false
            }
            if (obj.javaClass != Boolean::class.javaPrimitiveType) {
                return false
            }
            val other = obj as Boolean
            return data == other
        }

        fun set(other: Boolean): Boolean {
            data = other
            if (guiDict != null) {
                guiDict!!.SetBool(GetName(), data)
            }
            return data
        }

        fun set(other: idWinBool): idWinBool {
            super.set(other)
            data = other.data
            return this
        }

        override fun Set(`val`: String?) {
            data = atoi(`val`!!) != 0
            if (guiDict != null) {
                guiDict!!.SetBool(GetName(), data)
            }
        }

        override fun Update() {
            val s = GetName()
            if (guiDict != null && s[0] != '\u0000') {
                data = guiDict!!.GetBool(s)
            }
        }

        override fun c_str(): String {
            return if (data) "1" else "0"
        }

        // SaveGames
        override fun WriteToSaveGame(savefile: idFile) {
            savefile.WriteBool(eval)
            savefile.WriteBool(data)
        }

        override fun ReadFromSaveGame(savefile: idFile) {
            eval = savefile.ReadBool()
            data = savefile.ReadBool()
        }

        override fun x(): Float {
            return if (data) 1.0f else 0.0f
        }
    }

    open class idWinStr : idWinVar {
        var data: idStr? = idStr()

        //
        //
        constructor() : super()

        //	// ~idWinStr() {};
        constructor(a: String?) : this() {
            data = idStr(a!!)
        }

        //copy constructor
        internal constructor(other: idWinStr) {
            super.set(other)
            data = idStr(other.data!!)
        }

        override fun Init(_name: String?, win: idWindow?) {
            super.Init(_name, win)
            if (guiDict != null) {
                data = idStr(guiDict!!.GetString(GetName()))
            }
        }

        //	int	operator==(	const idStr other ) {
        //		return (other == data);
        //	}
        //	int	operator==(	const char *other ) {
        //		return (data == other);
        //	}
        override fun hashCode(): Int {
            var hash = 7
            hash = 11 * hash + Objects.hashCode(data)
            return hash
        }

        override fun equals(obj: Any?): Boolean {
            if (obj == null) {
                return false
            }

            //operator==( const idStr &other )
            if (obj.javaClass == idStr::class.java) {
                val other = obj as idStr
                return data == other
            }

            //operator==( const char *other )
            if (obj.javaClass == String::class.java) {
                val other = obj as String
                return data.toString() == other
            }
            return false
        }

        open fun set(other: idStr?): idStr? {
            data = other
            if (guiDict != null) {
                guiDict!!.Set(GetName(), data!!)
            }
            return data
        }

        fun set(other: idWinStr?): idWinStr {
            super.set(other)
            data = other!!.data
            return this
        }

        //public	operator const char *() {
        open fun oCastChar(): CharArray {
            return data!!.c_str()
        }

        //	public operator const idStr &() {
        fun LengthWithoutColors(): Int {
            if (guiDict != null && name != null && !name!!.isEmpty()) {
                data!!.set(guiDict!!.GetString(GetName()))
            }
            return data!!.LengthWithoutColors()
        }

        open fun Length(): Int {
            if (guiDict != null && name != null && !name!!.isEmpty()) {
                data!!.set(guiDict!!.GetString(GetName()))
            }
            return data!!.Length()
        }

        fun RemoveColors() {
            if (guiDict != null && name != null && !name!!.isEmpty()) {
                data!!.set(guiDict!!.GetString(GetName()))
            }
            data!!.RemoveColors()
        }

        override fun c_str(): String? {
            return data.toString()
        }

        override fun Set(`val`: String?) {
            data!!.set(`val`)
            if (guiDict != null) {
                guiDict!!.Set(GetName(), data!!)
            }
        }

        override fun Update() {
            val s = GetName()
            if (guiDict != null && !s.isEmpty()) {
                data!!.set(guiDict!!.GetString(s))
            }
        }

        override fun  /*size_t*/Size(): Int {
            val sz = super.Size()
            return sz + data!!.Allocated()
        }

        // SaveGames
        override fun WriteToSaveGame(savefile: idFile) {
            savefile.WriteBool(eval)
            val len = data!!.Length()
            savefile.WriteInt(len)
            if (len > 0) {
                savefile.WriteString(data)
            }
        }

        override fun ReadFromSaveGame(savefile: idFile) {
            eval = savefile.ReadBool()
            val len: Int
            len = savefile.ReadInt()
            if (len > 0) {
                data!!.Fill(' ', len)
                savefile.ReadString(data!!)
            }
        }

        // return wether string is emtpy
        override fun x(): Float {
            return if (data!!.IsEmpty()) 0.0f else 1.0f
        }
    }

    internal class idWinInt() : idWinVar() {
        var data = 0

        constructor(a: Int) : this() {
            data = a
        }

        override fun Init(_name: String?, win: idWindow?) {
            super.Init(_name, win)
            if (guiDict != null) {
                data = guiDict!!.GetInt(GetName())
            }
        }

        fun set(other: Int): Int {
            data = other
            if (guiDict != null) {
                guiDict!!.SetInt(GetName(), data)
            }
            return data
        }

        fun set(other: idWinInt): idWinInt {
            super.set(other)
            data = other.data
            return this
        }

        fun oCastInt(): Int {
            return data
        }

        override fun Set(`val`: String?) {
            data = atoi(`val`!!)
            if (guiDict != null) {
                guiDict!!.SetInt(GetName(), data)
            }
        }

        override fun Update() {
            val s = GetName()
            if (guiDict != null && s[0] != '\u0000') {
                data = guiDict!!.GetInt(s)
            }
        }

        override fun c_str(): String {
            return va("%d", data)
        }

        // SaveGames
        override fun WriteToSaveGame(savefile: idFile) {
            savefile.WriteBool(eval)
            savefile.WriteInt(data)
        }

        override fun ReadFromSaveGame(savefile: idFile) {
            eval = savefile.ReadBool()
            data = savefile.ReadInt()
        }

        // no suitable conversion
        override fun x(): Float {
            assert(false)
            return 0.0f
        }
    }

    class idWinFloat : idWinVar {
        var data: Float = 0.0f

        constructor() : super()
        constructor(a: Int) : this() {
            data = a.toFloat()
        }

        constructor(winFloat: idWinFloat) {
            super.set(winFloat)
            data = winFloat.data
        }

        override fun Init(_name: String?, win: idWindow?) {
            super.Init(_name, win)
            if (guiDict != null) {
                data = guiDict!!.GetFloat(GetName())
            }
        }

        fun set(other: idWinFloat?): idWinFloat {
            super.set(other)
            data = other!!.data
            return this
        }

        fun set(other: Float): Float {
            data = other
            if (guiDict != null) {
                guiDict!!.SetFloat(GetName(), data)
            }
            return data
        }

        fun oCastFloat(): Float {
            return data
        }

        override fun Set(`val`: String?) {
            data = try {
                `val`!!.toFloat()
            } catch (e: NumberFormatException) {
                0.0f//atof doesn't crash with non numbers.
            }
            if (guiDict != null) {
                guiDict!!.SetFloat(GetName(), data)
            }
        }

        override fun Update() {
            val s = GetName()
            if (guiDict != null && s[0] != '\u0000') {
                data = guiDict!!.GetFloat(s)
            }
        }

        override fun c_str(): String {
            return va("%f", data)
        }

        override fun WriteToSaveGame(savefile: idFile) {
            savefile.WriteBool(eval)
            savefile.WriteFloat(data)
        }

        override fun ReadFromSaveGame(savefile: idFile) {
            eval = savefile.ReadBool()
            data = savefile.ReadFloat()
        }

        override fun x(): Float {
            return data
        }
    }

    class idWinRectangle : idWinVar {
        //
        //
        val data: idRectangle

        constructor() : super() {
            data = idRectangle()
        }

        //copy constructor
        constructor(rect: idWinRectangle) {
            super.set(rect)
            data = idRectangle(rect.data)
        }

        //	~idWinRectangle() {};
        override fun Init(_name: String?, win: idWindow?) {
            super.Init(_name, win)
            if (guiDict != null) {
                val v = guiDict!!.GetVec4(GetName())
                data.x = v.x
                data.y = v.y
                data.w = v.z
                data.h = v.w
            }
        }

        fun set(other: idWinRectangle?): idWinRectangle {
            super.set(other)
            data.set(other!!.data)
            return this
        }

        fun set(other: idVec4?): idRectangle {
            data.set(other)
            if (guiDict != null) {
                guiDict!!.SetVec4(GetName(), other!!)
            }
            return data
        }

        fun set(other: idRectangle?): idRectangle {
            data.set(other)
            if (guiDict != null) {
                val v = data.ToVec4()
                guiDict!!.SetVec4(GetName(), v)
            }
            return data
        }

        fun oCastIdRectangle(): idRectangle {
            return data
        }

        override fun x(): Float {
            return data.x
        }

        fun y(): Float {
            return data.y
        }

        fun w(): Float {
            return data.w
        }

        fun h(): Float {
            return data.h
        }

        fun Right(): Float {
            return data.Right()
        }

        fun Bottom(): Float {
            return data.Bottom()
        }

        fun ToVec4(): idVec4 {
            ret.set(data.ToVec4())
            return ret
        }

        override fun Set(`val`: String?) {
            Scanner(`val`).use { sscanf ->
                if (`val`!!.contains(",")) {
                    if (sscanf.hasNext()) {
                        data.x = sscanf.nextFloat()
                    }
                    if (sscanf.hasNext()) {
                        data.y = sscanf.skip(",").nextFloat()
                    }
                    if (sscanf.hasNext()) {
                        data.w = sscanf.skip(",").nextFloat()
                    }
                    if (sscanf.hasNext()) {
                        data.h = sscanf.skip(",").nextFloat()
                    }
                } else {
                    if (sscanf.hasNextFloat()) {
                        data.x = sscanf.nextFloat()
                    }
                    if (sscanf.hasNextFloat()) {
                        data.y = sscanf.nextFloat()
                    }
                    if (sscanf.hasNextFloat()) {
                        data.w = sscanf.nextFloat()
                    }
                    if (sscanf.hasNextFloat()) {
                        data.h = sscanf.nextFloat()
                    }
                }
            }
            if (guiDict != null) {
                val v = data.ToVec4()
                guiDict!!.SetVec4(GetName(), v)
            }
        }

        override fun Update() {
            val s = GetName()
            if (guiDict != null && s[0] != '\u0000') {
                val v = guiDict!!.GetVec4(s)
                data.x = v.x
                data.y = v.y
                data.w = v.z
                data.h = v.w
            }
        }

        override fun c_str(): String {
            return data.ToVec4().ToString()
        }

        override fun WriteToSaveGame(savefile: idFile) {
            savefile.WriteBool(eval)
            savefile.Write(data)
        }

        override fun ReadFromSaveGame(savefile: idFile) {
            eval = savefile.ReadBool()
            savefile.Read(data)
        }

        override fun hashCode(): Int {
            var hash = 7
            hash = 97 * hash + Objects.hashCode(data)
            return hash
        }

        override fun equals(obj: Any?): Boolean {
            if (obj == null) {
                return false
            }
            if (obj.javaClass != idRectangle::class.java) {
                return false
            }
            val other = obj as idRectangle
            return data == other
        }

        companion object {
            private val ret: idVec4 = idVec4()
        }
    }

    class idWinVec2 : idWinVar {
        val data: idVec2 = idVec2()

        //
        //
        constructor() : super()

        //copy constructor
        constructor(vec2: idVec2) {
            data.set(vec2)
            if (guiDict != null) {
                guiDict!!.SetVec2(GetName(), data)
            }
        }

        //	~idWinVec2() {};
        override fun Init(_name: String?, win: idWindow?) {
            super.Init(_name, win)
            if (guiDict != null) {
                data.set(guiDict!!.GetVec2(GetName()))
            }
        }

        //	int	operator==(	const idVec2 other ) {
        //		return (other == data);
        //	}
        override fun hashCode(): Int {
            var hash = 3
            hash = 23 * hash + Objects.hashCode(data)
            return hash
        }

        override fun equals(obj: Any?): Boolean {
            if (obj == null) {
                return false
            }
            if (obj.javaClass != idVec2::class.java) {
                return false
            }
            val other = obj as idVec2
            return data == other
        }

        fun set(other: idWinVec2): idWinVec2 {
            super.set(other)
            data.set(other.data)
            return this
        }

        fun set(other: idVec2): idVec2 {
            data.set(other)
            if (guiDict != null) {
                guiDict!!.SetVec2(GetName(), data)
            }
            return data
        }

        override fun x(): Float {
            return data.x
        }

        fun y(): Float {
            return data.y
        }

        override fun Set(`val`: String?) {
            Scanner(`val`).use { sscanf ->
                if (`val`!!.contains(",")) {
//			sscanf( val, "%f,%f,%f,%f", data.x, data.y, data.w, data.h );
                    if (sscanf.hasNext()) {
                        data.x = sscanf.nextFloat()
                    }
                    if (sscanf.hasNext()) {
                        data.y = sscanf.skip(",").nextFloat()
                    }
                } else {
//			sscanf( val, "%f %f %f %f", data.x, data.y, data.w, data.h );
                    if (sscanf.hasNextFloat()) {
                        data.x = sscanf.nextFloat()
                    }
                    if (sscanf.hasNextFloat()) {
                        data.y = sscanf.nextFloat()
                    }
                }
            }
            if (guiDict != null) {
                guiDict!!.SetVec2(GetName(), data)
            }
        }

        fun oCastIdVec2(): idVec2 {
            return data
        }

        override fun Update() {
            val s = GetName()
            if (guiDict != null && s[0] != '\u0000') {
                data.set(guiDict!!.GetVec2(s))
            }
        }

        override fun c_str(): String {
            return data.ToString()
        }

        fun Zero() {
            data.Zero()
        }

        override fun WriteToSaveGame(savefile: idFile) {
            savefile.WriteBool(eval)
            savefile.Write(data)
        }

        override fun ReadFromSaveGame(savefile: idFile) {
            eval = savefile.ReadBool()
            savefile.Read(data)
        }
    }

    class idWinVec4 : idWinVar {
        val data: idVec4

        //
        //
        constructor() : super() {
            data = idVec4()
        }

        constructor(
            x: Float,
            y: Float,
            z: Float,
            w: Float
        ) : this() {
            data.set(idVec4(x, y, z, w))
        }

        //copy constructor
        constructor(winVec4: idWinVec4) {
            super.set(winVec4)
            data = idVec4(winVec4.data)
        }

        //	~idWinVec4() {};
        override fun Init(_name: String?, win: idWindow?) {
            super.Init(_name, win)
            if (guiDict != null) {
                data.set(guiDict!!.GetVec4(GetName()))
            }
        }

        //	int	operator==(	final idVec4 other ) {
        //		return (other == data);
        //	}
        override fun hashCode(): Int {
            var hash = 7
            hash = 97 * hash + Objects.hashCode(data)
            return hash
        }

        override fun equals(obj: Any?): Boolean {
            if (obj == null) {
                return false
            }
            if (obj.javaClass != idVec4::class.java) {
                return false
            }
            val other = obj as idVec4
            return data == other
        }

        fun set(other: idWinVec4?): idWinVec4 {
            super.set(other)
            data.set(other!!.data)
            return this
        }

        fun set(other: idVec4?): idVec4 {
            data.set(other!!)
            if (guiDict != null) {
                guiDict!!.SetVec4(GetName(), data)
            }
            return data
        }

        fun oCastIdVec4(): idVec4 {
            return data
        }

        override fun x(): Float {
            return data.x
        }

        fun y(): Float {
            return data.y
        }

        fun z(): Float {
            return data.z
        }

        fun w(): Float {
            return data.w
        }

        override fun Set(`val`: String?) {
            Scanner(`val`).use { sscanf ->
                if (`val`!!.contains(",")) {
//			sscanf( val, "%f,%f,%f,%f", data.x, data.y, data.z, data.w );
                    if (sscanf.hasNext()) {
                        data.x = sscanf.nextFloat()
                    }
                    if (sscanf.hasNext()) {
                        data.y = sscanf.skip(",").nextFloat()
                    }
                    if (sscanf.hasNext()) {
                        data.z = sscanf.skip(",").nextFloat()
                    }
                    if (sscanf.hasNext()) {
                        data.w = sscanf.skip(",").nextFloat()
                    }
                } else {
//			sscanf( val, "%f %f %f %f", data.x, data.y, data.z, data.w );
                    if (sscanf.hasNextFloat()) {
                        data.x = sscanf.nextFloat()
                    }
                    if (sscanf.hasNextFloat()) {
                        data.y = sscanf.nextFloat()
                    }
                    if (sscanf.hasNextFloat()) {
                        data.z = sscanf.nextFloat()
                    }
                    if (sscanf.hasNextFloat()) {
                        data.w = sscanf.nextFloat()
                    }
                }
            }
            if (guiDict != null) {
                guiDict!!.SetVec4(GetName(), data)
            }
        }

        override fun Update() {
            val s = GetName()
            if (guiDict != null && s[0] != '\u0000') {
                data.set(guiDict!!.GetVec4(s))
            }
        }

        override fun c_str(): String {
            return data.ToString()
        }

        override fun toString(): String {
            return data.toString()
        }

        fun Zero() {
            data.Zero()
            if (guiDict != null) {
                guiDict!!.SetVec4(GetName(), data)
            }
        }

        fun ToVec3(): idVec3 {
            return data.ToVec3()
        }

        override fun WriteToSaveGame(savefile: idFile) {
            savefile.WriteBool(eval)
            savefile.Write(data)
        }

        override fun ReadFromSaveGame(savefile: idFile) {
            eval = savefile.ReadBool()
            savefile.Read(data)
        }
    }

    internal class idWinVec3 : idWinVar {
        val data = idVec3()

        //
        //
        constructor() : super()

        //copy constructor
        constructor(winVec3: idWinVec3) {
            super.set(winVec3)
            data.set(winVec3.data)
        }

        //	~idWinVec3() {};
        override fun Init(_name: String?, win: idWindow?) {
            super.Init(_name, win)
            if (guiDict != null) {
                data.set(guiDict!!.GetVector(GetName()))
            }
        }

        //	int	operator==(	const idVec3 other ) {
        //		return (other == data);
        //	}
        override fun hashCode(): Int {
            var hash = 7
            hash = 23 * hash + Objects.hashCode(data)
            return hash
        }

        override fun equals(obj: Any?): Boolean {
            if (obj == null) {
                return false
            }
            if (obj.javaClass != idVec3::class.java) {
                return false
            }
            val other = obj as idVec3
            return data == other
        }

        fun set(other: idWinVec3): idWinVec3 {
            super.set(other)
            data.set(other.data)
            return this
        }

        fun set(other: idVec3?): idVec3 {
            data.set(other!!)
            if (guiDict != null) {
                guiDict!!.SetVector(GetName(), data)
            }
            return data
        }

        fun oCastIdVec3(): idVec3 {
            return data
        }

        override fun x(): Float {
            return data.x
        }

        fun y(): Float {
            return data.y
        }

        fun z(): Float {
            return data.z
        }

        override fun Set(`val`: String?) {
            Scanner(`val`).use { sscanf ->
//		sscanf( val, "%f %f %f", data.x, data.y, data.z);
                if (sscanf.hasNextFloat()) {
                    data.x = sscanf.nextFloat()
                }
                if (sscanf.hasNextFloat()) {
                    data.y = sscanf.nextFloat()
                }
                if (sscanf.hasNextFloat()) {
                    data.z = sscanf.nextFloat()
                }
            }
            if (guiDict != null) {
                guiDict!!.SetVector(GetName(), data)
            }
        }

        override fun Update() {
            val s = GetName()
            if (guiDict != null && s[0] != '\u0000') {
                data.set(guiDict!!.GetVector(s))
            }
        }

        override fun c_str(): String {
            return data.ToString()
        }

        fun Zero() {
            data.Zero()
            if (guiDict != null) {
                guiDict!!.SetVector(GetName(), data)
            }
        }

        override fun WriteToSaveGame(savefile: idFile) {
            savefile.WriteBool(eval)
            savefile.Write(data)
        }

        override fun ReadFromSaveGame(savefile: idFile) {
            eval = savefile.ReadBool()
            savefile.Read(data)
        }
    }

    class idWinBackground : idWinStr {
        protected var mat: Array<idMaterial?>? = null

        //
        //
        constructor() : super() {
            mat = null
            data = idStr()
        }

        //copy constructor
        constructor(other: idWinBackground) {
            super.set(other)
            data = other.data
            mat = other.mat
            if (mat != null) {
                if (data!!.IsEmpty()) {
                    mat!![0] = null
                } else {
                    mat!![0] = DeclManager.declManager.FindMaterial(data!!)
                }
            }
        }

        //	~idWinBackground() {};
        override fun Init(_name: String?, win: idWindow?) {
            super.Init(_name, win)
            if (guiDict != null) {
                data!!.set(guiDict!!.GetString(GetName()))
            }
        }

        override fun set(other: idStr?): idStr? {
            data = other
            if (guiDict != null) {
                guiDict!!.Set(GetName(), data!!)
            }
            if (mat != null) {
                if (data!!.IsEmpty()) {
                    mat!![0] = null
                } else {
                    mat!![0] = DeclManager.declManager.FindMaterial(data!!)
                }
            }
            return data
        }

        override fun oCastChar(): CharArray {
            return data!!.c_str()
        }

        override fun Length(): Int {
            if (guiDict != null) {
                data!!.set(guiDict!!.GetString(GetName()))
            }
            return data!!.Length()
        }

        override fun c_str(): String {
            return data.toString()
        }

        override fun Set(`val`: String?) {
            data!!.set(`val`)
            if (guiDict != null) {
                guiDict!!.Set(GetName(), data!!)
            }
            if (mat != null) {
                if (data!!.IsEmpty()) {
                    mat!![0] = null
                } else {
                    mat!![0] = DeclManager.declManager.FindMaterial(data!!)
                }
            }
        }

        override fun Update() {
            val s = GetName()
            if (guiDict != null && s[0] != '\u0000') {
                data!!.set(guiDict!!.GetString(s))
                if (mat != null) {
                    if (data!!.IsEmpty()) {
                        mat!![0] = null
                    } else {
                        mat!![0] = DeclManager.declManager.FindMaterial(data!!)
                    }
                }
            }
        }

        override fun  /*size_t*/Size(): Int {
            val sz = super.Size()
            return sz + data!!.Allocated()
        }

        fun SetMaterialPtr(m: idMaterial?) {
            if (mat == null) {
                mat = arrayOfNulls(1)
            }
            mat!![0] = m
        }

        override fun WriteToSaveGame(savefile: idFile) {
            savefile.WriteBool(eval)
            val len = data!!.Length()
            savefile.WriteInt(len)
            if (len > 0) {
                savefile.WriteString(data)
            }
        }

        override fun ReadFromSaveGame(savefile: idFile) {
            eval = savefile.ReadBool()
            val len: Int
            len = savefile.ReadInt()
            if (len > 0) {
                data!!.Fill(' ', len)
                savefile.ReadString(data!!)
            }
            if (mat != null) {
                if (len > 0) {
                    mat!![0] = DeclManager.declManager.FindMaterial(data!!)
                } else {
                    mat!![0] = null
                }
            }
        }
    }

    /*
     ================
     idMultiWinVar
     multiplexes access to a list if idWinVar*
     ================
     */
    internal class idMultiWinVar : idList<idWinVar?>() {
        fun Set(`val`: String?) {
            for (i in 0 until Num()) {
                this[i]!!.Set(`val`)
            }
        }

        fun Update() {
            for (i in 0 until Num()) {
                this[i]!!.Update()
            }
        }

        fun SetGuiInfo(dict: idDict?) {
            for (i in 0 until Num()) {
                this[i]!!.SetGuiInfo(dict, this[i]!!.c_str())
            }
        }
    }
}

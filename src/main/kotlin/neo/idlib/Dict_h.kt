package neo.idlib

import neo.TempDump
import neo.framework.CmdSystem.cmdFunction_t
import neo.framework.Common
import neo.framework.File_h.idFile
import neo.idlib.Text.Parser.idParser
import neo.idlib.Text.Str
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Token
import neo.idlib.Text.Token.idToken
import neo.idlib.containers.CBool
import neo.idlib.containers.CFloat
import neo.idlib.containers.CInt
import neo.idlib.containers.List.cmp_t
import neo.idlib.containers.List.idList
import neo.idlib.containers.StrPool.idPoolStr
import neo.idlib.containers.StrPool.idStrPool
import neo.idlib.containers.idHashIndex
import neo.idlib.hashing.CRC32
import neo.idlib.hashing.CRC32.Companion.CRC32_FinishChecksum
import neo.idlib.hashing.CRC32.Companion.CRC32_InitChecksum
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.Random.idRandom
import neo.idlib.math.idAngles
import neo.idlib.math.idVec2
import neo.idlib.math.idVec3
import neo.idlib.math.idVec4
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

class Dict_h {
    /**
     * ===============================================================================
     *
     *
     * Key/value dictionary
     *
     *
     * This is a dictionary class that tracks an arbitrary number of key / value
     * pair combinations. It is used for map entity spawning, GUI state
     * management, and other things.
     *
     *
     * Keys are compared case-insensitive.
     *
     *
     * Does not allocate memory until the first key/value pair is added.
     *
     *
     * ===============================================================================
     */
    class idKeyValue : idDict(), Cloneable {
        //	friend class idDict;
        var key: idPoolStr = idPoolStr()
        var value: idPoolStr = idPoolStr()

        //
        //
        fun GetKey(): idStr {
            return key
        }

        fun GetValue(): idStr {
            return value
        }

        override fun Allocated(): Long {
            return (key.Allocated() + value.Allocated()).toLong()
        }

        override fun Size(): Long {
            return  /*sizeof( *this ) +*/(key.Size() + value.Size()).toLong()
        }

        //	public boolean				operator==( final idKeyValue &kv ) final { return ( key == kv.key && value == kv.value ); }


        override fun toString(): String {
            return "idKeyValue{key=$key, value=$value}"
        }

        @Throws(CloneNotSupportedException::class)
        public override fun clone(): Any {
            return super.clone()
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as idKeyValue

            if (key != other.key) return false
            if (value != other.value) return false

            return true
        }

        override fun hashCode(): Int {
            var result = key.hashCode()
            result = 31 * result + value.hashCode()
            return result
        }
    }

    open class idDict {
        private val DBG_count = DBG_counter++
        private val argHash: idHashIndex = idHashIndex()
        private var args: idList<idKeyValue> = idList()

        constructor() {
            args.SetGranularity(16)
            argHash.SetGranularity(16)
            argHash.Clear(128, 16)
        }

        //public						~idDict( );
        // allow declaration with assignment
        constructor(other: idDict) {
            set(other)
        }

        // set the granularity for the index
        fun SetGranularity(granularity: Int) {
            args.SetGranularity(granularity)
            argHash.SetGranularity(granularity)
        }

        // set hash size
        fun SetHashSize(hashSize: Int) {
            if (args.Num() == 0) {
                argHash.Clear(hashSize, 16)
            }
        }

        /*
         ================
         idDict::operator=

         clear existing key/value pairs and copy all key/value pairs from other
         ================
         */
        // clear existing key/value pairs and copy all key/value pairs from other
        fun set(other: idDict): idDict {
            var i: Int

            // check for assignment to self
            if (this === other) {
                return this
            }
            Clear()
            argHash.set(other.argHash)
            // C++ args = other.args copies idKeyValue structs by value.
            // Kotlin idList.set() copies references, which causes both dicts to
            // share the same idKeyValue objects. Any Set() call on either dict
            // then corrupts the other's pool string reference counts.
            // Fix: create new idKeyValue objects with properly ref-counted pool strings.
            i = 0
            while (i < other.args.Num()) {
                val kv = idKeyValue()
                kv.key = globalKeys.CopyString(other.args[i].key)
                kv.value = globalValues.CopyString(other.args[i].value)
                args.Append(kv)
                i++
            }
            return this
        }

        /*
         ================
         idDict::Copy

         copy all key value pairs without removing existing key/value pairs not present in the other dict
         ================
         */
        // copy from other while leaving existing key/value pairs in place
        @Throws(idException::class)
        fun Copy(other: idDict) {
            var i: Int
            val n: Int
            val found: IntArray?
            val kv = idKeyValue()

            // check for assignment to self
            if (this === other) {
                return
            }
            n = other.args.Num()
            if (args.Num() != 0) {
                found = IntArray(other.args.Num())
                i = 0
                while (i < n) {
                    found[i] = FindKeyIndex(other.args[i].GetKey().toString() + "")
                    i++
                }
            } else {
                found = null
            }
            i = 0
            while (i < n) {
                if (found != null && found[i] != -1) {
                    // first set the new value and then free the old value to allow proper self copying
                    val oldValue = args[found[i]].value
                    args[found[i]].value = globalValues.CopyString(other.args[i].value)
                    globalValues.FreeString(oldValue)
                } else {
                    kv.key = globalKeys.CopyString(other.args[i].key)
                    kv.value = globalValues.CopyString(other.args[i].value)
                    argHash.Add(argHash.GenerateKey(kv.GetKey().toString() + "", false), args.Append(kv))
                }
                i++
            }
        }

        /*
         ================
         idDict::TransferKeyValues

         clear existing key/value pairs and transfer key/value pairs from other
         ================
         */
        // clear existing key/value pairs and transfer key/value pairs from other
        @Throws(idException::class)
        fun TransferKeyValues(other: idDict) {
            var i: Int
            val n: Int
            if (this === other) {
                return
            }
            if (other.args.Num() != 0 && other.args[0].key.GetPool() !== globalKeys) {
                Common.common.FatalError("idDict::TransferKeyValues: can't transfer values across a DLL boundary")
                return
            }
            Clear()
            n = other.args.Num()
            args.SetNum(n)
            try {
                i = 0
                while (i < n) {
                    args[i] = other.args[i] // TODO:check if clone() was necessary
                    i++
                }
            } catch (ex: CloneNotSupportedException) {
                throw idException(ex)
            }
            argHash.set(other.argHash)
            other.args.Clear()
            other.argHash.Free()
        }

        // parse dict from parser
        @Throws(idException::class)
        fun Parse(parser: idParser): Boolean {
            val token = idToken()
            val token2 = idToken()
            var errors: Boolean
            errors = false
            parser.ExpectTokenString("{")
            parser.ReadToken(token)
            while (token.type != Token.TT_PUNCTUATION || token.toString() != "}") {
                if (token.type != Token.TT_STRING) {
                    parser.Error("Expected quoted string, but found '%s'", token)
                }
                if (!parser.ReadToken(token2)) {
                    parser.Error("Unexpected end of file")
                }
                if (FindKey(token) != null) {
                    parser.Warning("'%s' already defined", token)
                    errors = true
                }
                Set(token, token2)
                if (!parser.ReadToken(token)) {
                    parser.Error("Unexpected end of file")
                }
            }
            return !errors
        }

        // copy key/value pairs from other dict not present in this dict
        @Throws(idException::class)
        fun SetDefaults(dict: idDict) {
            val n = dict.args.Num()
            for (i in 0 until n) {
                val def = dict.args[i]
                val kv = FindKey(def.GetKey().toString() + "") //TODO:override toString?
                val newkv = idKeyValue()
                if (null == kv) {
                    newkv.key = globalKeys.CopyString(def.key)
                    newkv.value = globalValues.CopyString(def.value)
                    argHash.Add(argHash.GenerateKey(newkv.GetKey().toString() + "", false), args.Append(newkv))
                }
            }
        }

        // clear dict freeing up memory
        fun Clear() {
            var i: Int
            i = 0
            while (i < args.Num()) {
                globalKeys.FreeString(args[i].key)
                globalValues.FreeString(args[i].value)
                i++
            }
            args.Clear()
            argHash.Free()
        }

        // print the dict
        @Throws(idException::class)
        fun Print() {
            var i: Int
            val n: Int
            n = args.Num()
            i = 0
            while (i < n) {
                idLib.common.Printf("%s = %s\n", args[i].GetKey().toString(), args[i].GetValue().toString())
                i++
            }
        }

        open fun Allocated(): Long {
            var i: Int
            var size: Long
            size = (args.Num() * Integer.BYTES + argHash.Allocated()).toLong()
            i = 0
            while (i < args.Num()) {
                size += args[i].Size()
                i++
            }
            return size
        }

        open fun Size(): Long {
            return  /*sizeof( * this) +*/Allocated()
        }

        @Deprecated(
            """make sure the <b>.toString()</b> methods output the
          strings we need."""
        )
        @Throws(idException::class)
        fun Set(key: Any?, value: Any) {
            Set(
                key.toString(), value.toString()
            ) //TODO:check if toString is sufficient instead of checking whether it's an idStr first?
        }

        @Throws(idException::class)
        fun Set(key: String?, value: String) {
            val i: Int
            val kv = idKeyValue()

//            System.out.println(DBG_Set++ + " " + key);
            if (key == null || key.isEmpty() || key[0] == '\u0000') {
                return
            }
            i = FindKeyIndex(key)
            if (i != -1) {
                // first set the new value and then free the old value to allow proper self copying
                val oldValue = args[i].value
                args[i].value = globalValues.AllocString(value)
                globalValues.FreeString(oldValue)
            } else {
                kv.key = globalKeys.AllocString(key)
                kv.value = globalValues.AllocString(value)
                argHash.Add(argHash.GenerateKey("" + kv.GetKey(), false), args.Append(kv))
            }
        }

        @Throws(idException::class)
        fun SetFloat(key: String?, `val`: Float) {
            Set(key, Str.va("%f", `val`))
        }

        @Throws(idException::class)
        fun SetInt(key: String?, `val`: Int) {
            Set(key, Str.va("%d", `val`))
        }

        @Throws(idException::class)
        fun SetBool(key: String?, `val`: Boolean) {
            Set(key, Str.va("%d", TempDump.btoi(`val`)))
        }

        @Throws(idException::class)
        fun SetVector(key: String?, `val`: idVec3) {
            Set(key, `val`.ToString())
        }

        @Throws(idException::class)
        fun SetVec2(key: String?, `val`: idVec2) {
            Set(key, `val`.ToString())
        }

        @Throws(idException::class)
        fun SetVec4(key: String?, `val`: idVec4) {
            Set(key, `val`.ToString())
        }

        @Throws(idException::class)
        fun SetAngles(key: String, `val`: idAngles) {
            Set(key, `val`.ToString())
        }

        @Throws(idException::class)
        fun SetMatrix(key: String, `val`: idMat3) {
            Set(key, `val`.ToString())
        }

        // these return default values of 0.0f, 0 and false

        @Throws(idException::class)
        fun GetString(key: String?, defaultString: String?): String? {
            val kv = FindKey(key)
            return kv?.GetValue()?.toString() ?: defaultString
        }


        @Throws(idException::class)
        fun GetString(key: String?): String {
            return GetString(key, "")!!
        }


        @Throws(idException::class)
        fun GetFloat(key: String?, defaultString: String = "0" /*= "0"*/): Float {
            return TempDump.atof(GetString(key, defaultString)!!)
        }


        @Throws(idException::class)
        fun GetInt(key: String?, defaultString: String = "0"): Int {
            return TempDump.atoi(GetString(key, defaultString)!!)
        }


        @Throws(idException::class)
        fun GetBool(key: String?, defaultString: String = "0"): Boolean {
            return TempDump.atob(GetString(key, defaultString)!!)
        }


        @Throws(idException::class)
        fun GetVector(key: String?, defaultString: String? = null): idVec3 {
            val out = idVec3()
            GetVector(key, defaultString, out)
            return out
        }


        @Throws(idException::class)
        fun GetVec2(key: String?, defaultString: String? = null): idVec2 {
            val out = idVec2()
            GetVec2(key, defaultString, out)
            return out
        }


        @Throws(idException::class)
        fun GetVec4(key: String?, defaultString: String? = null): idVec4 {
            val out = idVec4()
            GetVec4(key, defaultString, out)
            return out
        }


        @Throws(idException::class)
        fun GetAngles(key: String?, defaultString: String? = null): idAngles {
            val out = idAngles()
            GetAngles(key, defaultString, out)
            return out
        }

        @Throws(idException::class)
        fun GetMatrix(key: String?, defaultString: String?): idMat3 {
            val out = idMat3()
            GetMatrix(key, defaultString, out)
            return out
        }

        @Throws(idException::class)
        fun GetString(key: String?, defaultString: String?, out: Array<String?>): Boolean {
            val kv = FindKey(key)
            if (kv != null) {
                out[0] = kv.GetValue().toString()
                return true
            }
            out[0] = defaultString
            return false
        }

        @Throws(idException::class)
        fun GetString(key: String?, defaultString: String?, out: idStr): Boolean {
            val kv = FindKey(key)
            if (kv != null) {
                out.set(kv.GetValue())
                return true
            }
            out.set(defaultString)
            return false
        }

        @Throws(idException::class)
        fun GetFloat(key: String?, defaultString: String, out: CFloat): Boolean {
            val s = arrayOfNulls<String>(1)
            val found: Boolean
            found = GetString(key, defaultString, s)
            out._val = (TempDump.atof(s[0]!!))
            return found
        }

        @Throws(idException::class)
        fun GetInt(key: String?, defaultString: String, out: CInt): Boolean {
            val s = arrayOfNulls<String>(1)
            val found: Boolean
            found = GetString(key, defaultString, s)
            out._val = (TempDump.atoi(s[0]!!))
            return found
        }

        @Throws(idException::class)
        fun GetBool(key: String?, defaultString: String, out: CBool): Boolean {
            val s = arrayOfNulls<String>(1)
            val found: Boolean
            found = GetString(key, defaultString, s)
            out._val = (TempDump.atob(s[0]!!))
            return found
        }

        @Throws(idException::class)
        fun GetVector(key: String?, defaultString: String?, out: idVec3): Boolean {
            var defaultString = defaultString
            val found: Boolean
            val s = arrayOfNulls<String>(1)
            if (null == defaultString) {
                defaultString = "0 0 0"
            }
            found = GetString(key, defaultString, s)
            out.Zero()
            val sscanf: Array<String> = s[0]!!.split(" ").toTypedArray()

            for (i in sscanf.indices) {
                out[i] = TempDump.atof(sscanf[i])
            }

            return found
        }

        @Throws(idException::class)
        fun GetVec2(key: String?, defaultString: String?, out: idVec2): Boolean {
            var defaultString = defaultString
            val found: Boolean
            val s = arrayOfNulls<String>(1)
            if (null == defaultString) {
                defaultString = "0 0"
            }
            found = GetString(key, defaultString, s)
            out.Zero()
            val sscanf: Array<String> = s[0]!!.split(" ").toTypedArray()

            for (i in sscanf.indices) {
                out[i] = TempDump.atof(sscanf[i])
            }

            return found
        }

        @Throws(idException::class)
        fun GetVec4(key: String?, defaultString: String?, out: idVec4): Boolean {
            var defaultString = defaultString
            val found: Boolean
            val s = arrayOfNulls<String>(1)
            if (null == defaultString) {
                defaultString = "0 0 0 0"
            }
            found = GetString(key, defaultString, s)
            out.Zero()
            val sscanf: Array<String> = s[0]!!.split(" ").toTypedArray()

            for (i in sscanf.indices) {
                out[i] = TempDump.atof(sscanf[i])
            }

            return found
        }

        @Throws(idException::class)
        fun GetAngles(key: String?, defaultString: String?, out: idAngles): Boolean {
            var defaultString = defaultString
            val found: Boolean
            val s = arrayOfNulls<String>(1)
            if (null == defaultString) {
                defaultString = "0 0 0"
            }
            found = GetString(key, defaultString, s)
            out.Zero()
            val sscanf: Array<String> = s[0]!!.split(" ").toTypedArray()
            for (i in sscanf.indices) {
                out[i] = TempDump.atof(sscanf[i])
            }
            return found
        }

        @Throws(idException::class)
        fun GetMatrix(key: String?, defaultString: String?, out: idMat3): Boolean {
            var defaultString = defaultString
            val found: Boolean
            val s = arrayOfNulls<String>(1)
            if (null == defaultString) {
                defaultString = "1 0 0 0 1 0 0 0 1"
            }
            found = GetString(key, defaultString, s)
            out.Identity()
            val sscanf: Array<String> = s[0]!!.split(" ").toTypedArray()
            val halfSize = sqrt(sscanf.size.toFloat()).toInt()
            var i = 0
            var index = 0
            while (i < halfSize) {
                for (j in 0 until halfSize) {
                    out.set(i, j, TempDump.atof(sscanf[index++]))
                }
                i++
            }
            return found
        }

        fun GetNumKeyVals(): Int {
            return args.Num()
        }

        fun GetKeyVal(index: Int): idKeyValue? {
            return if (index >= 0 && index < args.Num()) {
                args[index]
            } else null
        }

        // returns the key/value pair with the given key
        // returns NULL if the key/value pair does not exist
        @Throws(idException::class)
        fun FindKey(key: String?): idKeyValue? {
            var i: Int
            val hash: Int
            if (key == null || key.isEmpty() /*[0] == '\0'*/) {
                idLib.common.DWarning("idDict::FindKey: empty key")
                return null
            }
            hash = argHash.GenerateKey(key, false)
            i = argHash.First(hash)
            while (i != -1) {
                if (args[i].GetKey().Icmp(key) == 0) {
                    return args[i]
                }
                i = argHash.Next(i)
            }
            return null
        }

        @Throws(idException::class)
        fun FindKey(key: idStr?): idKeyValue? {
            return FindKey(key.toString())
        }

        // returns the index to the key/value pair with the given key
        // returns -1 if the key/value pair does not exist
        @Throws(idException::class)
        fun FindKeyIndex(key: String?): Int {
            if (key == null || key.length < 1 /*[0] == '\0'*/) {
                idLib.common.DWarning("idDict::FindKeyIndex: empty key")
                return 0
            }
            val hash = argHash.GenerateKey(key, false)
            var i = argHash.First(hash)
            while (i != -1) {
                if (args[i].GetKey().Icmp(key) == 0) {
                    return i
                }
                i = argHash.Next(i)
            }
            return -1
        }

        // finds the next key/value pair with the given key prefix.
        // lastMatch can be used to do additional searches past the first match.
        // delete the key/value pair with the given key
        fun Delete(key: String) {
            val hash: Int
            var i: Int
            hash = argHash.GenerateKey(key, false)
            i = argHash.First(hash)
            while (i != -1) {
                if (args[i].GetKey().Icmp(key) == 0) {
                    globalKeys.FreeString(args[i].key)
                    globalValues.FreeString(args[i].value)
                    args.RemoveIndex(i)
                    argHash.RemoveIndex(hash, i)
                    break
                }
                i = argHash.Next(i)
            }
            //
//#if 0
//	// make sure all keys can still be found in the hash index
//	for ( i = 0; i < args.Num(); i++ ) {
//		assert( FindKey( args[i].GetKey() ) != NULL );
//	}
//#endif
        }

        fun Delete(key: idStr) {
            Delete(key.toString())
        }


        fun MatchPrefix(prefix: String, lastMatch: idKeyValue? = null): idKeyValue? {
            var i: Int
            val len: Int
            var start: Int
            assert(prefix != null)
            len = prefix.length
            start = -1
            if (lastMatch != null) {
                start = args.FindIndex(lastMatch)
                assert(start >= 0)
                if (start < 1) {
                    start = 0
                }
            }
            i = start + 1
            while (i < args.Num()) {
                if (0 == args[i].GetKey().Icmpn(prefix, len)) {
                    return args[i]
                }
                i++
            }
            return null
        }

        // randomly chooses one of the key/value pairs with the given key prefix and returns it's value
        fun RandomPrefix(prefix: String, random: idRandom): String? {
            var count: Int
            val MAX_RANDOM_KEYS = 2048
            val list = arrayOfNulls<String>(MAX_RANDOM_KEYS)
            var kv: idKeyValue?

//            list[0] = "";
            count = 0
            kv = MatchPrefix(prefix)
            while (kv != null && count < MAX_RANDOM_KEYS) {
                list[count++] = String(kv.GetValue().toString().toCharArray())
                kv = MatchPrefix(prefix, kv)
            }
            return list[random.RandomInt(count)]
        }

        @Throws(idException::class)
        fun WriteToFileHandle(f: idFile) {
            val c: Int = LittleLong(args.Num())
            f.WriteInt(c) //, sizeof(c));
            for (i in 0 until args.Num()) {    // don't loop on the swapped count use the original
                WriteString(args[i].GetKey(), f)
                WriteString(args[i].GetValue(), f)
            }
        }

        @Throws(idException::class)
        fun ReadFromFileHandle(f: idFile) {
            val c = CInt()
            var key: idStr
            var `val`: idStr
            Clear()

//            f.Read(c, sizeof(c));
            f.ReadInt(c)
            c._val = (LittleLong(c._val))
            for (i in 0 until c._val) {
                key = ReadString(f)
                `val` = ReadString(f)
                Set(key, `val`)
            }
        }

        // returns a unique checksum for this dictionary's content
        fun Checksum(): Long {
            val ret = LongArray(1)
            var i: Int
            val n: Int
            val sorted: idList<idKeyValue> = idList(args)
            sorted.Sort(KeyCompare())
            n = sorted.Num()
            CRC32_InitChecksum(ret)
            i = 0
            while (i < n) {
                CRC32.CRC32_UpdateChecksum(ret, sorted[i].GetKey().c_str(), sorted[i].GetKey().Length())
                CRC32.CRC32_UpdateChecksum(
                    ret,
                    sorted[i].GetValue().c_str(),
                    sorted[i].GetValue().Length()
                )
                i++
            }
            CRC32_FinishChecksum(ret)
            return ret[0]
        }

        class ShowMemoryUsage_f : cmdFunction_t() {
            @Throws(idException::class)
            override fun run(args: CmdArgs.idCmdArgs?) {
                idLib.common.Printf("%5d KB in %d keys\n", globalKeys.Size() shr 10, globalKeys.Num())
                idLib.common.Printf("%5d KB in %d values\n", globalValues.Size() shr 10, globalValues.Num())
            }

            companion object {
                private val instance: cmdFunction_t = ShowMemoryUsage_f()
                fun getInstance(): cmdFunction_t {
                    return instance
                }
            }
        }

        class ListKeys_f : cmdFunction_t() {
            @Throws(idException::class)
            override fun run(args: CmdArgs.idCmdArgs?) {
                var i: Int
                val keyStrings = idList<idPoolStr>()
                i = 0
                while (i < globalKeys.Num()) {
                    keyStrings.Append(globalKeys[i])
                    i++
                }
                keyStrings.Sort()
                i = 0
                while (i < keyStrings.Num()) {
                    idLib.common.Printf("%s\n", keyStrings[i])
                    i++
                }
                idLib.common.Printf("%5d keys\n", keyStrings.Num())
            }

            companion object {
                private val instance: cmdFunction_t = ListKeys_f()
                fun getInstance(): cmdFunction_t {
                    return instance
                }
            }
        }

        class ListValues_f : cmdFunction_t() {
            @Throws(idException::class)
            override fun run(args: CmdArgs.idCmdArgs?) {
                var i: Int
                val valueStrings = idList<idPoolStr>()
                i = 0
                while (i < globalValues.Num()) {
                    valueStrings.Append(globalValues[i])
                    i++
                }
                valueStrings.Sort()
                i = 0
                while (i < valueStrings.Num()) {
                    idLib.common.Printf("%s\n", valueStrings[i])
                    i++
                }
                idLib.common.Printf("%5d values\n", valueStrings.Num())
            }

            companion object {
                private val instance: cmdFunction_t = ListValues_f()
                fun getInstance(): cmdFunction_t {
                    return instance
                }
            }
        }

        companion object {
            //
            private val globalKeys: idStrPool = idStrPool()
            private val globalValues: idStrPool = idStrPool()
            private const val DBG_Set = 0

            //
            //
            private var DBG_counter = 0

            @Throws(idException::class)
            fun WriteString(s: idStr, f: idFile) {
                val len = s.data.length + 1
                if (len >= MAX_STRING_CHARS - 1) {
                    idLib.common.Error("idDict::WriteToFileHandle: bad string")
                }
                val buffer = ByteBuffer.allocate(len).order(ByteOrder.LITTLE_ENDIAN)
                buffer.put(s.data.toByteArray())
                buffer.flip()
                f.Write(buffer, len)
            }

            @Throws(idException::class)
            fun ReadString(f: idFile): idStr {
                val str = CharArray(MAX_STRING_CHARS)
                val c = shortArrayOf(0)
                var len: Int
                len = 0
                while (len < MAX_STRING_CHARS) {
                    f.ReadChar(c) //, 1);
                    str[len] = Char(c[0].toInt())
                    if (str[len].code == 0) {
                        break
                    }
                    len++
                }
                if (len == MAX_STRING_CHARS) {
                    idLib.common.Error("idDict::ReadFromFileHandle: bad string")
                }
                return idStr(str)
            }

            fun Init() {
                globalKeys.SetCaseSensitive(false)
                globalValues.SetCaseSensitive(true)
            }

            fun Shutdown() {
                globalKeys.Clear()
                globalValues.Clear()
            }
        }
    }

    internal class KeyCompare : cmp_t<idKeyValue> {
        override fun compare(a: idKeyValue, b: idKeyValue): Int {
            return idStr.Cmp(a.GetKey(), b.GetKey())
        }
    }
}
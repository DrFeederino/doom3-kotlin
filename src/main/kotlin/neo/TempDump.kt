package neo

import neo.Game.idEntity
import neo.Renderer.Material
import neo.Renderer.Material.idMaterial
import neo.Renderer.Model.idRenderModel
import neo.Renderer.RenderWorld
import neo.Sound.sound.idSoundEmitter
import neo.framework.DeclSkin.idDeclSkin
import neo.idlib.BV.idBounds
import neo.idlib.CmdArgs
import neo.idlib.Text.Str.idStr
import neo.idlib.containers.CBool
import neo.idlib.containers.CFloat
import neo.idlib.containers.CInt
import neo.idlib.containers.LinkList.idLinkList
import neo.idlib.geometry.JointTransform.idJointMat
import neo.idlib.idException
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.idVec3
import neo.ui.UserInterface.idUserInterface
import org.lwjgl.BufferUtils
import org.lwjgl.openal.AL10
import java.io.IOException
import java.io.Serializable
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.FloatBuffer
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.StandardOpenOption
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger
import java.util.stream.IntStream
import javax.swing.undo.CannotUndoException

object TempDump {
    //TODO:rename/refactor to ToolBox or something
    private val CALL_STACK_MAP: MutableMap<String, Int> = HashMap()

    /**
     * Our humble java implementation of the C++ strlen function, with NULL
     * checks.
     *
     * @param str a char array.
     * @return 0 if the array is empty or starts with null terminator, otherwise the index of the first null terminator.
     */

    fun strLen(str: CharArray): Int {
        if (str.isEmpty()) {
            return 0
        }
        var len = 0
        while (len < str.size) {
            if (str[len] == '\u0000') {
                break
            }
            len++
        }
        return len
    }


    fun strLen(str: ByteArray, offset: Int = 0): Int {
        if (str.isEmpty()) {
            return 0
        }
        var len = offset
        while (len < str.size) {
            if (str[len].toInt() == 0) {
                break
            }
            len++
        }
        return len
    }


    fun strLen(str: String): Int {
        return strLen(str.toCharArray())
    }

    fun memcmp(ptr1: IntArray, ptr2: IntArray, size_t: Int): Boolean {
        return memcmp(ptr1, 0, ptr2, 0, size_t)
    }

    fun memcmp(ptr1: IntArray, p1_offset: Int, ptr2: IntArray, p2_offset: Int, size_t: Int): Boolean {
        for (i in 0 until size_t) {
            if (ptr1[p1_offset + i] != ptr2[p2_offset + i]) {
                return false
            }
        }
        return true
    }

    fun memcmp(a: ByteArray?, b: ByteArray?, length: Int): Boolean {
        return if (null == a || null == b || a.size < length || b.size < length) {
            false
        } else Arrays.equals(
            Arrays.copyOf(a, length), Arrays.copyOf(b, length)
        )
    }

    /**
     * returns the serialized size of the object in bytes.
     *
     *
     * NB: the output of this method should ALWAYS be tagged **transient**.
     */
    fun SERIAL_SIZE(`object`: Any?): Int {
        return -1
        //        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
//                ObjectOutputStream oos = new ObjectOutputStream(baos)) {
//            oos.writeObject(object);
//            return baos.toByteArray().length;
//        } catch (IOException ex) {
//            Logger.getLogger(object.getClass().getName()).log(Level.SEVERE, null, ex);
//        }
//
//        throw new RuntimeException("unable to determine size!");
    }

    /**
     * @param unknownArray our unknown array.
     * @return -1 for **NULL** objects or **non-arrays** and the array's
     * dimensions for actual arrays.
     */
    fun arrayDimensions(unknownArray: Any?): Int {
        return if (null == unknownArray) {
            -1
        } else unknownArray.javaClass.toString().split("\\[").toTypedArray().size - 1
    }


    fun flatten(input: Array<ByteArray>): ByteArray {
        val height = input.size
        val width: Int = input[0].size
        val output = ByteArray(height * width)
        for (anInput in input) {
            System.arraycopy(anInput, 0, output, width, width)
        }
        return output
    }


    fun flatten(input: Array<Array<ByteArray>>): ByteArray {
        val height = input.size
        val width: Int = input[0].size
        val length: Int = input[0][0].size
        val output = ByteArray(height * width * length)
        for (a in 0 until height) {
            val x = a * width * length
            for (b in 0 until width) {
                val y = b * length
                System.arraycopy(input[a][b], 0, output, x + y, length)
            }
        }
        return output
    }

    /**
     * @param character character to insert.
     * @param index     position at which the character is inserted.
     * @param string    the input string
     * @return substring before **index** + character + substring after
     * **index**.
     */

    fun replaceByIndex(character: Char, index: Int, string: String): String {
        return string.substring(0, index) + character + string.substring(index + 1)
    }

    /**
     * Equivalent to **!object**.
     *
     * @param objects
     * @return True if **ALL** objects[0...i] = null.
     */

//    fun NOT(vararg objects: Any?): Boolean {
//        //TODO: make sure incoming object isn't Integer or Float...etc.
//        if (objects == null) return true
//        for (o in objects) {
//            if (o != null) {
//                return false
//            }
//        }
//        return true
//    }


//    fun NOT(objects: Any?): Boolean {
//        //TODO: make sure incoming object isn't Integer or Float...etc.
//        if (objects == null) return true else return false
//    }

    /**
     * @param number
     * @return
     */
    fun SNOT(number: Double): Int {
        return if (0.0 == number) 1 else 0
    }

    /**
     * Enum TO Int
     *
     *
     * ORDINALS!! mine arch enemy!!
     */

    fun etoi(enumeration: Enum<*>): Int {
        return enumeration.ordinal
    }

    /**
     * Boolean TO Int
     */

    fun btoi(bool: Boolean): Int {
        return if (bool) 1 else 0
    }

    /**
     * Byte TO Int
     */

    fun btoi(b: Byte): Int {
        return b.toInt() and 0xFF
    }

    /**
     * @return -1 if **v1** not in **vList**.
     */

    fun indexOf(v1: Any?, vList: Array<*>?): Int {
        var i: Int
        if (v1 != null && vList != null) {
            i = 0
            while (i < vList.size) {
                if (vList[i] == v1) {
                    return i
                }
                i++
            }
        }

        //we should NEVER get here!
        return -1
    }

    /**
     * Byte TO Int
     */

    fun btoi(b: ByteBuffer): Int {
        return b.get(0).toInt() and 0xFF
    }

    /**
     * Int TO Boolean
     */

    fun itob(i: Int): Boolean {
        return i != 0
    }

    fun ftoi(f: Float): Int {
        return java.lang.Float.floatToIntBits(f)
    }

    fun ftoi(a: FloatArray): IntArray {
        return IntStream.range(0, a.size).map { i: Int -> java.lang.Float.floatToIntBits(a.get(i)) }.toArray()
    }

    /**
     * FloatBuffer to Float Array
     */
    fun fbtofa(fb: FloatBuffer): FloatArray {
        // Must respect the FloatBuffer's arrayOffset from slice().
        // FloatBuffer.array() returns the FULL backing array ignoring offset,
        // so fb.array()[0] is NOT fb.get(0) for sliced buffers.
        val offset = fb.arrayOffset()
        val remaining = fb.remaining()
        return fb.array().copyOfRange(offset, offset + remaining)
    }


    fun atoi(ascii: String): Int {
        return try {
            ascii.trim { it <= ' ' }.toInt()
        } catch (e: NumberFormatException) {
            0
        }
    }

    fun atob(ascii: String): Boolean {
        return itob(atoi(ascii))
    }


    fun atoi(ascii: idStr): Int {
        return atoi(ascii.toString())
    }


    fun atoi(ascii: CharArray): Int {
        return atoi(ctos(ascii))
    }


    fun atof(ascii: String): Float {
        try {
            return ascii.trim { it <= ' ' }.replace(",", ".").toFloat()
        } catch (nfe: Exception) {
            return 0f
        }
    }


    fun atof(ascii: idStr?): Float {
        return atof(ascii.toString())
    }


    fun ctos(ascii: CharArray): String { //TODO:rename this moronic overloading!
//        if (NOT(ascii)) {
//            return null
//        }
        for (a in ascii.indices) {
            if ('\u0000' == ascii[a]) {
                return String(ascii).substring(0, a)
            }
        }
        return String(ascii)
    }


    fun ctos(ascii: Char): String {
        return "" + ascii
    }

    fun btos(bytes: ByteArray, offset: Int, length: Int): String? {
        return if (bytes.isEmpty()) {
            null
        } else String(bytes, offset, length)
    }


    fun btos(bytes: ByteArray, offset: Int = 0): String? {
        val length = strLen(bytes, offset) - offset //c style strings
        return btos(bytes, offset, length)
    }

    fun atobb(ascii: String?): ByteBuffer? {
        return if (ascii == null || ascii.isEmpty() || ascii == "\u0000") {
            null
        } else StandardCharsets.UTF_8.encode(ascii)

//        return ByteBuffer.wrap(ascii.getBytes());
    }

    fun atobb(ascii: idStr): ByteBuffer? {
        return if (ascii.data.isEmpty() || ascii.data == "\u0000") {
            null
        } else atobb(ascii.toString())
    }

    fun atobb(ascii: CharArray): ByteBuffer? {
        return if (ascii.isEmpty() || ascii[0] == '\u0000') {
            null
        } else atobb(ctos(ascii))
    }

    fun stobb(arr: ShortArray): ByteBuffer? {
        val buffer: ByteBuffer?
        if (arr.isEmpty()) {
            return null
        }
        buffer = ByteBuffer.allocate(arr.size * 2)
        buffer.asShortBuffer().put(arr)
        return buffer.flip()
    }

    fun atocb(ascii: String?): CharBuffer? {
        return if (ascii == null) {
            null
        } else CharBuffer.wrap(ascii.toCharArray())
    }


    fun bbtocb(buffer: ByteBuffer): CharBuffer {
        return StandardCharsets.ISO_8859_1.decode(buffer.rewind())
    }

    fun bbtoa(buffer: ByteBuffer): String {
        return bbtocb(buffer).toString()
    }


    fun wrapToNativeBuffer(bytes: ByteArray?): ByteBuffer? {
        return if (null == bytes) {
            null
        } else BufferUtils.createByteBuffer(bytes.size).put(bytes).flip()
    }

    /**
     * Integer array TO Int array
     */
    fun itoi(integerArray: Array<Int>?): IntArray? {
        if (integerArray == null) return null
        val intArray = IntArray(integerArray.size)
        for (i in 0 until intArray.size) {
            intArray[i] = integerArray[i]
        }

        return intArray
    }


    fun itob(intArray: IntArray): ByteArray {
        val buffer = ByteBuffer.allocate(intArray.size * 4)
        buffer.asIntBuffer().put(intArray)
        return buffer.array()
    }

    fun btoia(buffer: ByteBuffer): IntArray {
        val intArray = IntArray(buffer.capacity() / 4)
        for (i in intArray.indices) {
            intArray[i] = buffer.getInt(4 * i)
        }
        return intArray
    }

    fun ntohl(ip: ByteArray): Long {
        val buffer = ByteBuffer.allocate(8)
        buffer.put(ip)
        buffer.flip()
        buffer.limit(8)
        return buffer.getLong(0)
    }

    fun fopenOptions(mode: String?): MutableSet<StandardOpenOption>? {
        var mode = mode
        val temp: MutableSet<StandardOpenOption> = HashSet()
        if (null == mode) {
            return null
        }

        //it's all binary here.
        mode = mode.replace("b", "").replace("t", "")
        if (mode.contains("r")) {
            temp.add(StandardOpenOption.READ)
            if (mode.contains("r+")) {
                temp.add(StandardOpenOption.WRITE)
            }
        }
        if (mode.contains("w")) {
            temp.add(StandardOpenOption.CREATE)
            temp.add(StandardOpenOption.TRUNCATE_EXISTING)
            temp.add(StandardOpenOption.WRITE)
            if (mode.contains("w+")) {
                temp.add(StandardOpenOption.READ)
            }
        }
        if (mode.contains("a")) {
            temp.add(StandardOpenOption.APPEND)
            temp.add(StandardOpenOption.CREATE)
            temp.add(StandardOpenOption.WRITE)
            if (mode.contains("a+")) {
                temp.add(StandardOpenOption.READ)
            }
        }
        return temp
    }

    @Throws(IOException::class)

    fun fprintf(logFile: FileChannel, text: String) {
        logFile.write(ByteBuffer.wrap(text.toByteArray()))
    }

    fun reinterpret_cast_long_array(array: ByteArray): LongArray {
        val len = array.size / java.lang.Long.BYTES
        val buffer = ByteBuffer.wrap(array).asLongBuffer()
        val temp = LongArray(len)
        for (l in 0 until len) {
            temp[l] = buffer[l]
        }
        return temp
    }


    fun dynamic_cast(glass: Class<*>, `object`: Any?): Any? {
        return if (glass.isInstance(`object`)) {
            `object`
        } else null
    }

    /**
     * Prints the call stack from main to the point the function is called from.
     *
     * @param text some we would like to be put on top of our block.
     */
    fun printCallStack(text: String) {
        val elements = Thread.currentThread().stackTrace
        System.out.printf("----------------%s----------------\n", text)
        //e=2, skip current call, and calling class.
        for (e in 2 until elements.size) {
            System.out.printf("%s.%s\n", elements[e].className, elements[e].methodName)
        }
        System.out.printf("------------------------------\n")
    }

    fun countCallStack() {
        val elements = Thread.currentThread().stackTrace

        //e=2, skip current call, and calling class.
        for (e in 2 until elements.size) {
            val key =
                String.format("%s.%s->%d\n", elements[e].className, elements[e].methodName, elements[e].lineNumber)
            if (CALL_STACK_MAP.containsKey(key)) {
                val value: Int = CALL_STACK_MAP.getValue(key)
                CALL_STACK_MAP[key] = value + 1 //increment
            } else {
                CALL_STACK_MAP[key] = 1
            }
        }
    }

    private fun breakOnALError() {
        val e: Int
        if (AL10.alGetError().also { e = it } != 0) {
            throw RuntimeException("$e minutes, to miiiiiiiidnight!")
        }
    }

    fun printCallStackCount() {
        println(Arrays.toString(CALL_STACK_MAP.entries.toTypedArray()))
    }

    fun printLinkedList(head: idLinkList<idEntity?>) {
        var ent = head.Next()
        while (ent != null) {
            println(ent.name)
            ent = ent.activeNode!!.Next()
        }
    }

    @Deprecated("")

    fun <T> allocArray(clazz: Class<T>, length: Int): Array<T> {
        val array = java.lang.reflect.Array.newInstance(clazz, length) as Array<T>
        for (a in 0 until length) {
            try {
                array[a] = clazz.getConstructor().newInstance()
            } catch (ex: NoSuchMethodException) {
                throw TODO_Exception() //missing default constructor
            } catch (ex: SecurityException) {
                throw TODO_Exception()
            } catch (ex: InstantiationException) {
                throw TODO_Exception()
            } catch (ex: IllegalAccessException) {
                throw TODO_Exception()
            } catch (ex: IllegalArgumentException) {
                throw TODO_Exception()
            } catch (ex: InvocationTargetException) {
                throw TODO_Exception()
            }
        }
        return array
    }

    interface Settable<T> {
        fun set(other: T): T
        fun new(): T
        fun zero()
    }

    /**
     *
     */
    @Deprecated("")
    interface SERiAL : Serializable {
        /**
         * Prepares an **empty** ByteBuffer representation of the class for
         * reading.
         *
         * @return
         */
        fun AllocBuffer(): ByteBuffer

        /**
         * Reads the ByteBuffer and converts and sets its values to the current
         * object.
         *
         * @param buffer
         */
        fun Read(buffer: ByteBuffer)

        /**
         * Prepares a ByteBuffer representation of the class for writing.
         *
         * @return
         */
        fun Write(): ByteBuffer

        companion object {
            //TODO:remove Serializable
            const val SIZE = Int.MIN_VALUE
            const val BYTES = SIZE / java.lang.Byte.SIZE
        }
    }

    /**
     *
     */
    interface NiLLABLE<type> {
        fun oSet(node: type): type
        fun isNULL(): Boolean
    }

    abstract class void_callback<type> {
        @Throws(idException::class)
        abstract fun run(vararg objects: type)
    }

    abstract class argCompletion_t<E> {
        //TODO
        //    public abstract void run(type... objects);
        abstract fun load(args: CmdArgs.idCmdArgs, callback: void_callback<String?>?)
    }

    /**
     *
     */
    @Deprecated("")
    object reflects {
        /**
         *
         */
        private val GET_DIMENSION: String = "GetDimension"
        private val O_GET: String = "get"
        private val O_MINUS: String = "minus"
        private val O_MULTIPLY: String = "times"
        private val O_PLUS: String = "plus"
        private val O_SET: String = "set"
        private val ZERO: String = "Zero"
//        fun GetDimension(`object`: Any): Int {
//            val clazz: Class<*> = `object`.javaClass
//            var returnValue = 0
//            //try {
//                val getDimension = clazz.getDeclaredMethod(GET_DIMENSION)
//                returnValue = getDimension.invoke(`object`) as Int
////            } catch (ex: NoSuchMethodException) {
////                Logger.getLogger(Curve::class.java.name).log(Level.SEVERE, null, ex)
////            } catch (ex: SecurityException) {
////                Logger.getLogger(Curve::class.java.name).log(Level.SEVERE, null, ex)
////            } catch (ex: IllegalAccessException) {
////                Logger.getLogger(Curve::class.java.name).log(Level.SEVERE, null, ex)
////            } catch (ex: IllegalArgumentException) {
////                Logger.getLogger(Curve::class.java.name).log(Level.SEVERE, null, ex)
////            } catch (ex: InvocationTargetException) {
////                Logger.getLogger(Curve::class.java.name).log(Level.SEVERE, null, ex)
////            }
//            return returnValue
//        }
//
//        fun Zero(`object`: Any) {
//            val clazz: Class<*> = `object`.javaClass
//            val getDimension: Method?
//            try {
//                getDimension = clazz.getDeclaredMethod(ZERO)
//                getDimension.invoke(`object`)
//            } catch (ex: NoSuchMethodException) {
//                Logger.getLogger(Curve::class.java.name).log(Level.SEVERE, null, ex)
//            } catch (ex: SecurityException) {
//                Logger.getLogger(Curve::class.java.name).log(Level.SEVERE, null, ex)
//            } catch (ex: IllegalAccessException) {
//                Logger.getLogger(Curve::class.java.name).log(Level.SEVERE, null, ex)
//            } catch (ex: IllegalArgumentException) {
//                Logger.getLogger(Curve::class.java.name).log(Level.SEVERE, null, ex)
//            } catch (ex: InvocationTargetException) {
//                Logger.getLogger(Curve::class.java.name).log(Level.SEVERE, null, ex)
//            }
//        }
//
//        fun _Get(`object`: Any, declaredField: String): Any? {
//            val clazz: Class<*> = `object`.javaClass
//            val field: Field?
//            var returnObject: Any? = null
//            try {
//                field = clazz.getDeclaredField(declaredField)
//                returnObject = field[`object`]
//            } catch (ex: NoSuchFieldException) {
//                Logger.getLogger(TempDump::class.java.name).log(Level.SEVERE, null, ex)
//            } catch (ex: SecurityException) {
//                Logger.getLogger(TempDump::class.java.name).log(Level.SEVERE, null, ex)
//            } catch (ex: IllegalArgumentException) {
//                Logger.getLogger(TempDump::class.java.name).log(Level.SEVERE, null, ex)
//            } catch (ex: IllegalAccessException) {
//                Logger.getLogger(TempDump::class.java.name).log(Level.SEVERE, null, ex)
//            }
//            return returnObject
//        }
//
//        fun _Get(`object`: Any, index: Int): Float {
//            return _GetMul(`object`, index, 1.0f) //TODO:you know what to do
//        }
//
//        fun _GetMul(`object`: Any, index: Int, value: Float): Float {
//            val clazz: Class<*> = `object`.javaClass
//            var returnValue = 0.0f
//            val get: Method?
//            val times: Method
//            val returnObject: Any?
//            try {
////                System.out.printf("%s\n\n", Arrays.toString(clazz.getDeclaredMethods()));
//                get = clazz.getDeclaredMethod(O_GET, Int::class.javaPrimitiveType)
//                returnObject = get.invoke(`object`, index)
//                try {
//                    times = returnObject.javaClass.getDeclaredMethod(O_MULTIPLY)
//                    returnValue =
//                        times.invoke(returnObject, value) as Float //object becomes float when multiplied(idMat)
//                } catch (ex: NoSuchMethodException) {
//                    returnValue = returnObject as Float * value //object that has float(idVec)
//                }
//            } catch (ex: NoSuchMethodException) {
//                returnValue = `object` as Float * value //float
//            } catch (ex: SecurityException) {
//                Logger.getLogger(Curve::class.java.name).log(Level.SEVERE, null, ex)
//            } catch (ex: IllegalAccessException) {
//                Logger.getLogger(Curve::class.java.name).log(Level.SEVERE, null, ex)
//            } catch (ex: IllegalArgumentException) {
//                Logger.getLogger(Curve::class.java.name).log(Level.SEVERE, null, ex)
//            } catch (ex: InvocationTargetException) {
//                Logger.getLogger(Curve::class.java.name).log(Level.SEVERE, null, ex)
//            }
//            return returnValue
//        }
//
//        fun _GetGet(`object`: Any, x: Int, y: Int): Float {
//            val clazz: Class<*> = `object`.javaClass
//            var returnValue = 0.0f
//            val get: Method?
//            val oGet2: Method
//            val returnObject: Any?
//            try {
//                get = clazz.getDeclaredMethod(O_GET)
//                returnObject = get.invoke(`object`, x)
//                oGet2 = returnObject.javaClass.getDeclaredMethod(O_GET)
//                returnValue = oGet2.invoke(returnObject, y) as Float
//            } catch (ex: NoSuchMethodException) {
//                Logger.getLogger(Curve::class.java.name).log(Level.SEVERE, null, ex)
//            } catch (ex: SecurityException) {
//                Logger.getLogger(Curve::class.java.name).log(Level.SEVERE, null, ex)
//            } catch (ex: IllegalAccessException) {
//                Logger.getLogger(Curve::class.java.name).log(Level.SEVERE, null, ex)
//            } catch (ex: IllegalArgumentException) {
//                Logger.getLogger(Curve::class.java.name).log(Level.SEVERE, null, ex)
//            } catch (ex: InvocationTargetException) {
//                Logger.getLogger(Curve::class.java.name).log(Level.SEVERE, null, ex)
//            }
//            return returnValue
//        }
//
//        fun _GetSet(`object`: Any, x: Int, y: Int, value: Float): Float {
//            val clazz: Class<*> = `object`.javaClass
//            var returnValue = 0.0f
//            val get: Method?
//            val oGet2: Method
//            val returnObject: Any?
//            try {
//                get = clazz.getDeclaredMethod(O_GET)
//                returnObject = get.invoke(`object`, x)
//                oGet2 = returnObject.javaClass.getDeclaredMethod(O_SET)
//                returnValue = oGet2.invoke(returnObject, y, value) as Float
//            } catch (ex: NoSuchMethodException) {
//                Logger.getLogger(Curve::class.java.name).log(Level.SEVERE, null, ex)
//            } catch (ex: SecurityException) {
//                Logger.getLogger(Curve::class.java.name).log(Level.SEVERE, null, ex)
//            } catch (ex: IllegalAccessException) {
//                Logger.getLogger(Curve::class.java.name).log(Level.SEVERE, null, ex)
//            } catch (ex: IllegalArgumentException) {
//                Logger.getLogger(Curve::class.java.name).log(Level.SEVERE, null, ex)
//            } catch (ex: InvocationTargetException) {
//                Logger.getLogger(Curve::class.java.name).log(Level.SEVERE, null, ex)
//            }
//            return returnValue
//        }

        fun _Minus(object1: Any, object2: Any): Any {
            return ooOOoooOOoo(object1, object2, O_MINUS)
        }

        /**
         * Resolves the object types and processes the mathematical operation
         * accordingly, whether it's **overridden**(e.g oPlus, minus..etc)
         * or not.
         *
         * @param object1
         * @param object2
         * @param O_METHOD
         * @return
         */
        private fun ooOOoooOOoo(object1: Any, object2: Any, O_METHOD: String): Any {
            val class1: Class<*> = object1.javaClass
            val class2: Class<*> = object2.javaClass
            val method1: Method
            val method2: Method
            var returnObject: Any? = null
            try {
                method1 = class1.getDeclaredMethod(O_METHOD, class2)
                returnObject = method1.invoke(object1, object2)
            } catch (nox: NoSuchMethodException) {
                try { //try teh other way around.
                    method2 = class2.getDeclaredMethod(O_METHOD, class1)
                    returnObject = method2.invoke(object2, object1)
                } catch (ex: Exception) {
                    //we should only get here if both our objects are primitives.
                    returnObject = when (O_METHOD) {
                        O_PLUS -> object1.toString().toDouble() + object2.toString()
                            .toDouble() //both objects are integrals
                        O_MINUS -> object1.toString().toDouble() - object2.toString()
                            .toDouble() //both objects are integrals
                        O_MULTIPLY -> object1.toString().toDouble() * object2.toString()
                            .toDouble() //both objects are integrals
                        else -> object1.toString().toDouble() * object2.toString().toDouble()
                    }
                    //                    throw nox;//catch it there↓↓
                }
            } catch (ex: SecurityException) { //←←here
                Logger.getLogger(TempDump::class.java.name).log(Level.SEVERE, null, ex)
            } catch (ex: IllegalAccessException) {
                Logger.getLogger(TempDump::class.java.name).log(Level.SEVERE, null, ex)
            } catch (ex: IllegalArgumentException) {
                Logger.getLogger(TempDump::class.java.name).log(Level.SEVERE, null, ex)
            } catch (ex: InvocationTargetException) {
                Logger.getLogger(TempDump::class.java.name).log(Level.SEVERE, null, ex)
            }
            return returnObject!!
        }
    }

    class Atomics {
        class renderViewShadow {
            var cramZNear: CBool = CBool(false)
            var forceUpdate: CBool = CBool(false)

            var fov_x: CFloat = CFloat()
            var fov_y: CFloat = CFloat()

            var globalMaterial: idMaterial? = null
            var shaderParms = Array(RenderWorld.MAX_GLOBAL_SHADER_PARMS) { CFloat() }
            var time: CInt = CInt()
            var viewID: CInt = CInt()

            val viewaxis: idMat3 = idMat3()
            val vieworg: idVec3 = idVec3()

            var x: CInt = CInt()
            var y: CInt = CInt()

            var width: CInt = CInt()
            var height: CInt = CInt()
        }

        class renderEntityShadow {
            var allowSurfaceInViewID: CInt = CInt()
            val axis: idMat3 = idMat3()
            var bodyId: CInt = CInt()
            val bounds: idBounds = idBounds()
            var callback: RenderWorld.deferredEntityCallback_t? = null
            var callbackData: ByteBuffer? = null
            var customShader: idMaterial? = null
            var customSkin: idDeclSkin? = null
            var entityNum: CInt = CInt()
            var forceUpdate: CInt = CInt()
            var gui: Array<idUserInterface?> = arrayOfNulls(RenderWorld.MAX_RENDERENTITY_GUI)
            var hModel: idRenderModel? = null
            var joints: Array<idJointMat>? = null
            var modelDepthHack: CFloat = CFloat()
            var noDynamicInteractions: CBool = CBool()
            var noSelfShadow: CBool = CBool()
            var noShadow: CBool = CBool()
            var numJoints: CInt = CInt()
            val origin: idVec3 = idVec3()
            var referenceShader: idMaterial? = null
            var referenceSound: idSoundEmitter? = null
            var remoteRenderView: RenderWorld.renderView_s? = null
            var shaderParms = Array(Material.MAX_ENTITY_SHADER_PARMS) { CFloat() }
            var suppressShadowInLightID: CInt = CInt()
            var suppressShadowInViewID: CInt = CInt()
            var suppressSurfaceInViewID: CInt = CInt()
            var timeGroup: CInt = CInt()
            var weaponDepthHack: CBool = CBool()
            var xrayIndex: CInt = CInt()
        }

        class renderLightShadow {
            //

            var allowLightInViewID: CInt = CInt()


            val axis: idMat3 = idMat3()


            val end: idVec3 = idVec3()


            val lightCenter: idVec3 = idVec3()

            //

            var lightId: CInt = CInt()


            val lightRadius: idVec3 = idVec3()

            //

            var noShadows: CBool = CBool()


            var noSpecular: CBool = CBool()


            val origin: idVec3 = idVec3()


            var parallel: CBool = CBool()

            //

            var pointLight: CBool = CBool()

            //

            var prelightModel: idRenderModel? = null


            var referenceSound: idSoundEmitter? = null


            val right: idVec3 = idVec3()

            //
            //

            var shader: idMaterial? = null


            var shaderParms: FloatArray = FloatArray(Material.MAX_ENTITY_SHADER_PARMS)


            val start: idVec3 = idVec3()

            //

            var suppressLightInViewID: CInt = CInt()

            //

            val target: idVec3 = idVec3()


            val up: idVec3 = idVec3()
        }
    }

    /**
     * Decorator classes so when we want to know how big a pointer is we call
     * Pointer.SIZE.
     */
    class CPP_class {
        //TODO:create enum instead.
        object Pointer {
            /**
             * A 32bit C++ pointer is 32bits wide, duh!
             */
            @Transient

            val SIZE = 32
        }

        object Bool {
            /**
             * A C++ bool is 8bits.
             */
            @Transient

            val SIZE = java.lang.Byte.SIZE
        }

        object Char {
            /**
             * A C++ char is also 8bits wide.
             */
            @Transient

            val SIZE = java.lang.Byte.SIZE
        }

        object Enum {

            /**
             * A C++ long is 4 bytes.
             */
            @Transient
            val Long = Integer.SIZE

            /**
             * A C++ enum is as big as an int.
             */
            @Transient

            val SIZE = Integer.SIZE
        }

        object Long {
            /**
             * A C++ long is 4 bytes.
             */
            @Transient

            val SIZE = Integer.SIZE
        }
    }

    /**
     * you lazy ass bitch!!
     */
    class TODO_Exception : UnsupportedOperationException() {
        init {
            printStackTrace()
            System.err.println(
                """
                        Woe to you, Oh Earth and Sea, for the Devil sends the
                        beast with wrath, because he knows the time is short...
                        Let him who hath understanding reckon the number of the beast,
                        for it is a human number,
                        its numbers, is
                        """.trimIndent()
            )
            System.exit(666)
        }
    }

    class Deprecation_Exception : UnsupportedOperationException() {
        init {
            printStackTrace()
            System.err.println(
                """
                        DARKNESS!!
                        Imprisoning me
                        All that I see
                        Absolute horror
                        I cannot live...I cannot die...body my holding cell!
                        """.trimIndent()
            )
            System.exit(666)
        }
    }

    class TypeErasure_Expection : CannotUndoException() {
        init {
            printStackTrace()
            System.err.println(
                """
    The future is always blank.
    Only your willpower can leave footsteps there.
    """.trimIndent()
            )
            System.exit(666)
        }
    }
}
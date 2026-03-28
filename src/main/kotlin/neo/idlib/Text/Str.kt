package neo.idlib.Text

import neo.TempDump
import neo.TempDump.SERiAL
import neo.TempDump.TODO_Exception
import neo.framework.CmdSystem.cmdFunction_t
import neo.idlib.Text.Token.idToken
import neo.idlib.idLib
import neo.idlib.math.INTSIGNBITNOTSET
import neo.idlib.math.idVec4
import java.nio.ByteBuffer
import java.nio.file.Paths
import java.util.*


object Str {
    const val C_COLOR_BLACK = '9'.code
    const val C_COLOR_BLUE = '4'.code
    const val C_COLOR_CYAN = '5'.code
    const val C_COLOR_DEFAULT = '0'.code

    // color escape character
    const val C_COLOR_ESCAPE = '^'.code
    const val C_COLOR_GRAY = '8'.code
    const val C_COLOR_GREEN = '2'.code
    const val C_COLOR_MAGENTA = '6'.code
    const val C_COLOR_RED = '1'.code
    const val C_COLOR_WHITE = '7'.code
    const val C_COLOR_YELLOW = '3'.code
    const val FILE_HASH_SIZE = 1024
    val S_COLOR_BLACK: String = "^9"
    val S_COLOR_BLUE: String = "^4"
    val S_COLOR_CYAN: String = "^5"

    // color escape string
    val S_COLOR_DEFAULT: String = "^0"
    val S_COLOR_GRAY: String = "^8"
    val S_COLOR_GREEN: String = "^2"
    val S_COLOR_MAGENTA: String = "^6"
    val S_COLOR_RED: String = "^1"
    val S_COLOR_WHITE: String = "^7"
    val S_COLOR_YELLOW: String = "^3"

    // make idStr a multiple of 16 bytes long
    // don't make too large to keep memory requirements to a minimum
    const val STR_ALLOC_BASE = 20
    const val STR_ALLOC_GRAN = 32

    //
    //    static idDynamicBlockAlloc<Character> stringDataAllocator = new idDynamicBlockAlloc<>(1 << 18, 128);
    //
    val g_color_table: Array<idVec4> = arrayOf(
        idVec4(0.0f, 0.0f, 0.0f, 1.0f),
        idVec4(1.0f, 0.0f, 0.0f, 1.0f),  // S_COLOR_RED
        idVec4(0.0f, 1.0f, 0.0f, 1.0f),  // S_COLOR_GREEN
        idVec4(1.0f, 1.0f, 0.0f, 1.0f),  // S_COLOR_YELLOW
        idVec4(0.0f, 0.0f, 1.0f, 1.0f),  // S_COLOR_BLUE
        idVec4(0.0f, 1.0f, 1.0f, 1.0f),  // S_COLOR_CYAN
        idVec4(1.0f, 0.0f, 1.0f, 1.0f),  // S_COLOR_MAGENTA
        idVec4(1.0f, 1.0f, 1.0f, 1.0f),  // S_COLOR_WHITE
        idVec4(0.5f, 0.5f, 0.5f, 1.0f),  // S_COLOR_GRAY
        idVec4(0.0f, 0.0f, 0.0f, 1.0f),  // S_COLOR_BLACK
        idVec4(0.0f, 0.0f, 0.0f, 1.0f),
        idVec4(0.0f, 0.0f, 0.0f, 1.0f),
        idVec4(0.0f, 0.0f, 0.0f, 1.0f),
        idVec4(0.0f, 0.0f, 0.0f, 1.0f),
        idVec4(0.0f, 0.0f, 0.0f, 1.0f),
        idVec4(0.0f, 0.0f, 0.0f, 1.0f)
    )
    val units: Array<Array<String>> = arrayOf(arrayOf("B", "KB", "MB", "GB"), arrayOf("B/s", "KB/s", "MB/s", "GB/s"))

    /*
     ============
     va

     does a varargs printf into a temp buffer
     NOTE: not thread safe
     ============
     */
    //    @Deprecated

    fun va(fmt: String, vararg args: Any?): String {
//////	va_list argptr;
////        char[] argptr;
////        int index = 0;
////        char[][] string = new char[4][16384];	// in case called by nested functions
////        char[] buf;
////
////        buf = string[index];
////        index = (index + 1) & 3;
////
//////	va_start( argptr, fmt );
//////	vsprintf( buf, fmt, argptr );
//////	va_end( argptr );
////
//        return new String(buf);
        return String.format(fmt, *args)
    }

    enum class Measure_t {
        MEASURE_SIZE,
        MEASURE_BANDWIDTH
    }

    open class idStr : SERiAL {
        //
        //
        protected val baseBuffer: CharArray = CharArray(STR_ALLOC_BASE)
        var alloced = 0
        var data: String =
            "" //i·ro·ny: when your program breaks because of two measly double quotes. stu·pid·i·ty: when it takes you 2 days to find said "bug".
        var len //TODO:data is a pointer in the original class.
                = 0

        constructor() {
            Init()
        }

        constructor(text: idStr) {
            val l: Int
            Init()
            l = text.Length()
            EnsureAlloced(l + 1)
            //	strcpy( data, text.data );
            data = text.data
            len = l
        }

        fun StripTrailing(c: Char) { // strip char from end as many times as the char occurs
            var i = Length()
            while (i > 0 && data[i - 1] == c) {
                len--
                data = data.substring(0, len)
                i--
            }
        }

        constructor(text: idStr, start: Int, end: Int) {
            var start = start
            var end = end
            var i: Int
            var l: Int
            Init()
            if (end > text.Length()) {
                end = text.Length()
            }
            if (start > text.Length()) {
                start = text.Length()
            } else if (start < 0) {
                start = 0
            }
            l = end - start
            if (l < 0) {
                l = 0
            }
            EnsureAlloced(l + 1)

//	for ( i = 0; i < l; i++ ) {
//		data[ i ] = text.data[ start + i ];
//	}
            data = text.data.substring(start, end)

//	data+= '\0';
            len = l
        }

        constructor(text: String) {
            val l: Int
            Init()
            if (text != null) {
//		l = strlen( text );
                l = text.length
                EnsureAlloced(l + 1)
                //		strcpy( data, text );
                data = text
                len = l
            }
        }

        constructor(text: CharArray) {
            val l: Int
            Init()
            if (text != null) {
//		l = strlen( text );
                l = text.size
                EnsureAlloced(l + 1)
                //		strcpy( data, text );
                data = TempDump.ctos(text)
                len = l
            }
        }

        constructor(text: String, start: Int, end: Int) {
            var start = start
            var end = end
            var i: Int
            //	int l = strlen( text );
            var l = text.length
            Init()
            if (end > l) {
                end = l
            }
            if (start > l) {
                start = l
            } else if (start < 0) {
                start = 0
            }
            l = end - start
            if (l < 0) {
                l = 0
            }
            EnsureAlloced(l + 1)
            data = text.substring(start, end)

//	data += '\0';
            len = l
        }

        constructor(b: Boolean) {
            Init()
            EnsureAlloced(2)
            data = if (b) "1" else "0"
            //	data+= '\0';
            len = 1
        }

        constructor(c: Char) {
            Init()
            EnsureAlloced(2)
            data = "" + c
            //	data+= '\0';
            len = 1
        }

        //public						~idStr( void ) {
        //	FreeData();
        //}
        //
        constructor(i: Int) {
//	char []text=new char[ 64 ];
            val text = Integer.toString(i)
            val l = text.length
            Init()
            //	l = sprintf( text, "%d", i );
//	l = sprintf( text, "%d", i );
            EnsureAlloced(l + 1)
            //	strcpy( data, text );
            data = text
            len = l
        }

        constructor(u: Long) {
            val text = java.lang.Long.toString(u)
            val l = text.length
            Init()
            //	l = sprintf( text, "%u", u );
            EnsureAlloced(l + 1)
            //	strcpy( data, text );
            data = text
            len = l
        }

        //public	operator			const char *( void ) const;
        //public	operator			const char *( void );
        //
        constructor(f: Float) {
            val text = f.toString()
            val l = text.length
            Init()
            EnsureAlloced(l + 1)
            //	strcpy( data, text );
            data = text
            len = l
        }

        open fun  /*size_t*/Size(): Int {
            return  /*sizeof( *this ) +*/Allocated()
        }

        @Deprecated("")
        fun c_str(): CharArray {
            return data.toCharArray()
        }

        operator fun get(index: Int): Char {
            assert(index >= 0 && index <= len)
            return data[index]
        }

        operator fun set(index: Int, value: Char): Char {
            //assert ((index >= 0) && (index <= len));
            if (index == len
                || 0 == len
            ) { //just append if length == 0;
                data += value
            } else {
                data = data.substring(0, index) + value + data.substring(index + 1)
            }
            return value
        }

        open fun set(text: idStr) {
            val l: Int
            l = text.Length()
            EnsureAlloced(l + 1, false)
            //	memcpy( data, text.data, l );
//	data[l] = '\0';
            data = text.data
            len = l
        }

        //public	void				operator=( const char *text );
        open fun set(text: String?): idStr {
            val l: Int
            if (text == null) {
                // safe behaviour if NULL
                EnsureAlloced(1, false)
                len = 0
                return this
            }
            l = text.length
            EnsureAlloced(l + 1, false)
            data = text
            len = l
            return this
        }

        fun set(text: CharArray): idStr {
            return this.set(TempDump.ctos(text))
        }

        //public	friend idStr		operator+( const idStr &a, const idStr &b );
        operator fun plus(b: idStr): idStr {
            val result = idStr(data)
            result.Append(b.data)
            return result
        }

        //public	friend idStr		operator+( const idStr &a, const char *b );
        operator fun plus(b: String): idStr {
            val result = idStr(data)
            result.Append(b)
            return result
        }

        /*
         ============
         idStr::StripQuotes

         Removes the quotes from the beginning and end of the string
         ============
         */
        //public	friend idStr		operator+( const idStr &a, const float b );
        operator fun plus(b: Float): idStr {
            val text: String
            val result = idStr(data)
            text = String.format("%f", b)
            result.Append(text)
            return result
        }

        //public	friend idStr		operator+( const idStr &a, const int b );
        //public	friend idStr		operator+( const idStr &a, const unsigned b );
        operator fun plus(b: Long): idStr {
            val text: String
            val result = idStr(data)
            text = String.format("%d", b)
            result.Append(text)
            return result
        }

        operator fun plus(b: Boolean): idStr {
            val result = idStr(data)
            result.Append(if (b) "true" else "false")
            return result
        }

        //public	friend idStr		operator+( const idStr &a, const char b );
        operator fun plus(b: Char): idStr {
            val result = idStr(data)
            result.Append(b)
            return result
        }

        //public	 idStr		plus( final idStr a, final int b ){return plus(a, b);}
        //public	idStr &				operator+=( const idStr &a );
        fun plusAssign(a: idStr): idStr {
            Append(a)
            return this
        }

        //public	idStr &				operator+=( const char *a );
        fun plusAssign(a: String): idStr {
            Append(a)
            return this
        }

        //public	idStr &				operator+=( const float a );
        fun plusAssign(a: Float): idStr {
            Append("" + a)
            return this
        }

        //public	idStr &				operator+=( const char a );
        fun plusAssign(a: Char): idStr {
            Append(a)
            return this
        }

        fun plusAssign(a: Long): idStr {
            Append("" + a)
            return this
        }

        fun plusAssign(a: Boolean): idStr {
            Append(java.lang.Boolean.toString(a))
            return this
        }

        override fun hashCode(): Int {
            var hash = 5
            hash = 59 * hash + Objects.hashCode(data)
            return hash
        }

        /**
         * The idStr equals basically compares to see if the string begins with
         * the other string.
         *
         * @param obj the **other** string.
         * @return
         * @see java.lang.String.startsWith
         */
        override fun equals(obj: Any?): Boolean {
            if (obj == null) {
                return false
            }
            if (obj.javaClass == String::class.java) { //when comparing pointers it's usually only about what they point to.
                if (!(obj as String).isEmpty()) {
                    return data.startsWith(obj) //TODO:should we check first character against first character only
                }
            }
            if (obj.javaClass == idStr::class.java) {
                if (!(obj as idStr).IsEmpty()) {
                    return data.startsWith(obj.data)
                }
            }
            return if (obj.javaClass == Char::class.java) {
                data.startsWith((obj as Char).toString())
            } else false
        }

        // case sensitive compare
        fun Cmp(text: String): Int {
            assert(text != null)
            return Cmp(data, text)
        }

        fun Cmp(text: idStr): Int {
            return Cmp(text.toString())
        }

        fun Cmpn(text: String, n: Int): Int {
            assert(text != null)
            return Cmpn(data, text, n)
        }

        fun CmpPrefix(text: String): Int {
            assert(null != text)
            return Cmpn(data, text,  /*strlen( text )*/text.length)
        }

        // case insensitive compare
        fun Icmp(text: String): Int {
            assert(text != null)
            return Icmp(data, text)
        }

        fun Icmp(text: idStr): Int {
            return this.Icmp(text.toString())
        }

        fun Icmpn(text: String, n: Int): Int {
            assert(text != null)
            return Icmpn(data, text, n)
        }

        fun IcmpPrefix(text: String): Int {
            assert(text != null)
            return Icmpn(data, text, text.length)
        }

        // case insensitive compare ignoring color
        fun IcmpNoColor(text: String): Int {
            assert(text != null)
            return IcmpNoColor(data, text)
        }

        fun IcmpNoColor(text: idStr): Int {
            return this.IcmpNoColor(text.toString())
        }

        // compares paths and makes sure folders come first
        fun IcmpPath(text: String): Int {
            assert(text != null)
            return IcmpPath(data, text)
        }

        fun IcmpnPath(text: String, n: Int): Int {
            assert(text != null)
            return IcmpnPath(data, text, n)
        }

        fun IcmpPrefixPath(text: String): Int {
            assert(text != null)
            return IcmpnPath(data, text, text.length)
        }

        fun Length(): Int {
            return len
        }

        open fun Allocated(): Int {
            return if ( /*data != baseBuffer*/true) {
                alloced
            } else {
                0
            }
        }

        fun Empty() {
            EnsureAlloced(1)
            //	data ="\0";
            data = ""
            len = 0
        }

        fun IsEmpty(): Boolean {
//	return ( this.Cmp( data, "" ) == 0 );
            return data.isEmpty()
        }

        fun Clear() {
            FreeData()
            Init()
        }

        fun Append(a: idStr) {
            Append(a.data)
        }

        fun Append(a: Char) {
            EnsureAlloced(len + 2)
            data += a
            len++ //TODO:remove \0
            //	data+= '\0';
        }

        fun Append(text: String) {
            val newLen: Int
            newLen = len + text.length
            EnsureAlloced(newLen + 1)
            data += text
            len = newLen
            //	data[ len ] = '\0';
        }

        fun Append(text: CharArray) {
            Append(TempDump.ctos(text))
        }

        fun Append(text: String, l: Int) {
            val newLen: Int
            if (text != null && l > 0) {
                newLen = len + l
                EnsureAlloced(newLen + 1)
                //		for ( i = 0; text[ i ] && i < l; i++ ) {
//			data[ len + i ] = text[ i ];
//		}
                data = data.substring(0, len) + text.substring(0, l)
                len = newLen
                //		data[ len ] = '\0';
            }
        }

        fun Insert(a: Char, index: Int) {
            var index = index
            val l: Int
            if (index < 0) {
                index = 0
            } else if (index > len) {
                index = len
            }
            l = 1
            EnsureAlloced(len + l + 1)
            //	for ( i = len; i >= index; i-- ) {
//		data[i+l] = data[i];
//	}
//	data[index] = a;
            data = data.substring(0, index) + a + data.substring(index)
            len++
        }

        fun Insert(text: String, index: Int) {
            var index = index
            val l: Int
            if (index < 0) {
                index = 0
            } else if (index > len) {
                index = len
            }

//	l = strlen( text );
            l = text.length
            EnsureAlloced(len + l + 1)
            //	for ( i = len; i >= index; i-- ) {
//		data[i+l] = data[i];
//	}
//	for ( i = 0; i < l; i++ ) {
//		data[index+i] = text[i];
//	}
            data = data.substring(0, index) + text + data.substring(index)
            len += l
        }

        fun ToLower() {
//	for (int i = 0; data[i]; i++ ) {
//		if ( CharIsUpper( data[i] ) ) {
//			data[i] += ( 'a' - 'A' );
//		}
//	}
            data = data.lowercase(Locale.getDefault())
        }

        fun ToUpper() {
//	for (int i = 0; data[i]; i++ ) {
//		if ( CharIsLower( data[i] ) ) {
//			data[i] -= ( 'a' - 'A' );
//		}
//	}
            data = data.uppercase(Locale.getDefault())
        }

        fun RemoveColors(): idStr {
            data = RemoveColors(data)
            //            len = Length( data );
            len = data.length
            return this
        }

        fun CapLength(newlen: Int) {
            if (len <= newlen) {
                return
            }
            data = data.substring(0, newlen)
            len = newlen
        }

        fun Fill(ch: Char, newlen: Int) {
            EnsureAlloced(newlen + 1)
            len = newlen
            val arr = CharArray(newlen)
            Arrays.fill(arr, ch)
            data = String(arr)

//	data[ len ] = 0;
        }


        fun Find(c: Char, start: Int = 0, end: Int = -1): Int {
            var end = end
            if (end == -1) {
                end = len
            }
            return FindChar(data, c, start, end)
        }

        //public	static int			snPrintf( char *dest, int size, const char *fmt, ... ) id_attribute((format(printf,3,4)));

        fun Find(text: String, casesensitive: Boolean = true, start: Int = 0, end: Int = -1): Int {
            var end = end
            if (end == -1) {
                end = len
            }
            return FindText(data, text, casesensitive, start, end)
        }

        fun Filter(filter: String, casesensitive: Boolean): Boolean {
            return this.Filter(filter, data, casesensitive)
        }

        fun Last(c: Char): Int { // return the index to the last occurance of 'c', returns -1 if not found
//	int i;
//
//	for( i = Length(); i > 0; i-- ) {
//		if ( data[ i - 1 ] == c ) {
//			return i - 1;
//		}
//	}
//
//	return -1;
            return data.lastIndexOf(c)
        }

        fun Left(len: Int, result: idStr): idStr? { // store the leftmost 'len' characters in the result
            return Mid(0, len, result)
        }

        fun Right(len: Int, result: idStr): idStr? { // store the rightmost 'len' characters in the result
            if (len >= Length()) {
                result.set(this)
                return result
            }
            return Mid(Length() - len, len, result)
        }

        // store 'len' characters starting at 'start' in result
        fun Mid(start: Int, len: Int, result: idStr): idStr? {
            var len = len
            val i: Int
            result.Empty()
            i = Length()
            if (i == 0 || len <= 0 || start >= i) {
                return null
            }
            if (start + len >= i) {
                len = i - start
            }
            result.Append(data.substring(start), len)
            return result
        }

        fun Left(len: Int): idStr { // return the leftmost 'len' characters
            return Mid(0, len)
        }

        fun Right(len: Int): idStr { // return the rightmost 'len' characters
            return if (len >= Length()) {
                this
            } else Mid(Length() - len, len)
        }

        fun Mid(start: Int, len: Int): idStr { // return 'len' characters starting at 'start'
            var len = len
            val i: Int
            val result = idStr()
            i = Length()
            if (i == 0 || len <= 0 || start >= i) {
                return result
            }
            if (start + len >= i) {
                len = i - start
            }

//	result.Append( &data[ start ], len );
//	result.Append( &data[ start ], len );
            result.Append(data.substring(start), len)
            return result
        }

        fun StripLeading(c: Char) { // strip char from front as many times as the char occurs
//	while( data[ 0 ] == c ) {
//		memmove( &data[ 0 ], &data[ 1 ], len );
//		len--;
//	}
            while (c == data[0]) {
                len--
                if (data.length == 1) {
                    data = ""
                    break
                }
                data = data.substring(1)
            }
        }

        fun StripLeading(string: String) { // strip string from front as many times as the string occurs
            val l: Int

//	l = strlen( string );
            l = string.length
            if (l > 0) {
                while (data.startsWith(string)) {
//			memmove( data, data + l, len - l + 1 );
                    len -= l
                    if (data.length == l) {
                        data = ""
                        break
                    }
                    data = data.substring(l)
                }
            }
        }

        fun StripLeadingOnce(string: String): Boolean { // strip string from front just once if it occurs
            val l: Int

//	l = strlen( string );
            l = string.length
            //	if ( ( l > 0 ) && !Cmpn( string, l ) ) {
            if (l > 0 && data.startsWith(string)) {
//		memmove( data, data + l, len - l + 1 );
                data = data.substring(l)
                len -= l
                return true
            }
            return false
        }


        fun StripTrailing(string: String) { // strip string from end as many times as the string occurs
            val l: Int

//	l = strlen( string );
            l = string.length
            if (l > 0) {
                while (len >= l && data.endsWith(string)) {
                    len -= l
                    //			data[len] = '\0';
                    data = data.substring(0, len)
                }
            }
        }

        fun StripTrailingOnce(string: String): Boolean { // strip string from end just once if it occurs
            val l: Int

//	l = strlen( string );
            l = string.length
            if (l > 0 && len >= l && data.endsWith(string)) {
                len -= l
                //		data[len] = '\0';
                data = data.substring(0, len)
                return true
            }
            return false
        }

        fun Strip(c: Char) { // strip char from front and end as many times as the char occurs
            StripLeading(c)
            StripTrailing(c)
        }

        fun Strip(string: String) { // strip string from front and end as many times as the string occurs
            StripLeading(string)
            StripTrailing(string)
        }

        fun StripTrailingWhitespace() { // strip trailing white space characters
//	int i;

            // cast to unsigned char to prevent stripping off high-ASCII characters
//	for( i = Length(); i > 0 && (data[ i - 1 ]) <= ' '; i-- ) {
//		data[ i - 1 ] = '\0';
//		len--;
//	}
            data = data.trim { it <= ' ' }
            len = data.length
        }

        fun StripQuotes(): idStr { // strip quotes around string
            if (data[0] != '\"') {
                return this
            }

            // Remove the trailing quote first
            if (data[len - 1] == '\"') {
//		data[len-1] = '\0';
                data = data.substring(0, len - 1)
                len--
            }

            // Strip the leading quote now
            len--
            data = data.substring(1)
            //	memmove( &data[ 0 ], &data[ 1 ], len );
//	data[len] = '\0';
            return this
        }

        fun Replace(old: String, nw: String) {
            data = data.replace(old, nw)
            len = data.length
            //	int		oldLen, newLen, i, j, count;
//	idStr	oldString=new idStr( data );
//
////	oldLen = strlen( old );
////	newLen = strlen( nw );
//	oldLen = old.length();
//	newLen = nw.length();
//
//	// Work out how big the new string will be
//	count = 0;
//	for( i = 0; i < oldString.Length(); i++ ) {
//		if( !idStr.Cmpn( oldString[i], old, oldLen ) ) {
//			count++;
//			i += oldLen - 1;
//		}
//	}
//
//	if( count!=0 ) {
//		EnsureAlloced( len + ( ( newLen - oldLen ) * count ) + 2, false );
//
//		// Replace the old data with the new data
//		for( i = 0, j = 0; i < oldString.Length(); i++ ) {
//			if( !idStr.Cmpn( oldString[i], old, oldLen ) ) {
//				memcpy( data + j, nw, newLen );
//				i += oldLen - 1;
//				j += newLen;
//			} else {
//				data[j] = oldString[i];
//				j++;
//			}
//		}
//		data[j] = 0;
//		len = strlen( data );
//	}
        }

        /*
         =====================================================================

         filename methods

         =====================================================================
         */
        // hash key for the filename (skips extension)
        fun FileNameHash(): Int {
            var i: Int
            var hash: Int
            var letter: Char
            hash = 0
            i = 0
            //	while( data[i] != '\0' ) {
            while (i < data.length) {
                letter = ToLower(data[i])
                if (letter == '.') {
                    break // don't include extension
                }
                if (letter == '\\') {
                    letter = '/'
                }
                hash += (letter.code * (i + 119))
                i++
            }
            hash = hash and FILE_HASH_SIZE - 1
            return hash.toInt()
        }

        fun BackSlashesToSlashes(): idStr { // convert slashes
//	int i;
//
//	for ( i = 0; i < len; i++ ) {
//		if ( data[ i ] == '\\' ) {
//			data[ i ] = '/';
//		}
//	}
            data = data.replace('\\', '/')
            return this
        }

        fun SetFileExtension(extension: String): idStr { // set the given file extension
            StripFileExtension()
            //	if ( *extension != '.' ) {
            if (extension[0] != '.') {
                Append('.')
            }
            Append(extension)
            return this
        }

        fun SetFileExtension(extension: idStr): idStr {
            return SetFileExtension(extension.toString())
        }

        fun StripFileExtension(): idStr { // remove any file extension
            val i: Int

//            for (i = len - 1; i >= 0; i--) {
//                if (data.charAt(i) == '.') {
////			data[i] = '\0';
//                    len = i;
//                    data = data.substring(0, len);
//                    break;
//                }
//            }
            i = data.lastIndexOf('.')
            if (i > -1) {
                len = i
                data = data.substring(0, len)
            }
            return this
        }

        fun StripAbsoluteFileExtension(): idStr { // remove any file extension looking from front (useful if there are multiple .'s)
            var i: Int
            i = 0
            while (i < len) {
                if (data[i] == '.') {
//			data[i] = '\0';
                    len = i
                    data = data.substring(0, len)
                    break
                }
                i++
            }
            return this
        }

        fun DefaultFileExtension(extension: String): idStr { // if there's no file extension use the default

            // do nothing if the string already has an extension
//            for (i = len - 1; i >= 0; i--) {
            if (data.contains(".")) {
                return this
            }
            //            }
            if (!extension.startsWith(".")) {
                Append('.')
            }
            Append(extension)
            return this
        }

        fun DefaultPath(basepath: CharArray): idStr { // if there's no path use the default
//	if ( ( ( *this )[ 0 ] == '/' ) || ( ( *this )[ 0 ] == '\\' ) ) {
            if (data[0] == '/' || data[0] == '\\') {
                // absolute path location
                return this
            }

//	*this = basepath + *this;
            data = basepath.toString() + data //TODO:bad..where to put the extension
            return this
        }

        fun AppendPath(text: String) { // append a partial path
            var pos: Int
            var i = 0
            val dataArray = data.toCharArray()
            if (text != null && text.length > 0) {
                pos = len
                EnsureAlloced(len + text.length + 2)
                if (pos != 0) {
                    if (dataArray[pos - 1] != '/') {
                        dataArray[pos++] = '/'
                    }
                }
                if (text[i] == '/') {
                    i++
                }
                while (i < text.length) {
                    if (text[i] == '\\') {
                        dataArray[pos++] = '/'
                    } else {
                        dataArray[pos++] = text[i]
                    }
                    i++
                }
                len = pos
                //		data[ pos ] = '\0';
                data = TempDump.ctos(dataArray)
            }
        }

        fun AppendPath(text: idStr) {
            Append(text.toString())
        }

        fun StripFilename(): idStr { // remove the filename from a path
            var pos: Int
            pos = Length() - 1
            while (pos > 0 && data[pos] != '/' && data[pos] != '\\') {
                pos--
            }
            if (pos < 0) {
                pos = 0
            }
            CapLength(pos)
            return this
        }

        // remove the path from the filename
        fun StripPath(): idStr {
            var pos: Int
            pos = Length()
            while (pos > 0 && data[pos - 1] != '/' && data[pos - 1] != '\\') {
                pos--
            }
            val temp = Right(Length() - pos)
            data = temp.data
            len = data.length
            return this
        }

        fun ExtractFilePath(dest: idStr) { // copy the file path to another string
            var pos: Int

            //
            // back up until a \ or the start
            //
            pos = Length()
            while (pos > 0 && data[pos - 1] != '/' && data[pos - 1] != '\\') {
                pos--
            }
            Left(pos, dest)
        }

        fun ExtractFileName(dest: idStr) { // copy the filename to another string
            var pos: Int

            //
            // back up until a \ or the start
            //
            pos = Length() - 1
            while (pos > 0 && data[pos - 1] != '/' && data[pos - 1] != '\\') {
                pos--
            }
            Right(Length() - pos, dest)
        }

        fun ExtractFileBase(dest: idStr) { // copy the filename minus the extension to another string
            var pos: Int
            val start: Int

            //
            // back up until a \ or the start
            //
            pos = Length() - 1
            while (pos > 0 && data[pos - 1] != '/' && data[pos - 1] != '\\') {
                pos--
            }
            start = pos
            while (pos < Length() && data[pos] != '.') {
                pos++
            }
            Mid(start, pos - start, dest)
        }

        // copy the file extension to another string
        fun ExtractFileExtension(dest: idStr) {
            var pos: Int

            //
            // back up until a . or the start
            //
            pos = Length() - 1
            while (pos > 0 && data[pos - 1] != '.') {
                pos--
            }
            if (pos == 0) {
                // no extension
                dest.Empty()
            } else {
                Right(Length() - pos, dest)
            }
        }

        // format value in the requested unit and measurement
        fun CheckExtension(ext: String): Boolean {
            return CheckExtension(data, ext)
        }

        /*
         ============
         idStr::Filter

         Returns true if the string conforms the given filter.
         Several metacharacter may be used in the filter.

         *          match any string of zero or more characters
                   match any single character
         [abc...]   match any of the enclosed characters; a hyphen can
         be used to specify a range (e.g. a-z, A-Z, 0-9)

         ============
         */
        /*static*/   fun Filter(filter: String, name: String, casesensitive: Boolean): Boolean {
            var name = name
            val buf = idStr()
            var i: Int
            var index: Int
            var found: Boolean
            var filterIndex = 0
            while (filterIndex < filter.length) {
                var filterChar: Char = filter[filterIndex]
                if (filterChar == '*') {
                    filterIndex++
                    buf.Empty()
                    i = 0
                    while (filterIndex < filter.length) {
                        if (filterChar == '*' || filterChar.code == 0 || filterChar == '[' && filter[filterIndex + 1] != '[') {
                            break
                        }
                        buf.plusAssign(filterChar)
                        if (filterChar == '[') {
                            filterIndex++
                        }
                        filterIndex++
                        i++
                        filterChar = filter[filterIndex]
                    }
                    if (buf.Length() > 0) {
                        index =  /*new idStr(name).*/Find(buf.toString(), casesensitive) //TODO:remove stuff
                        if (index == -1) {
                            return false
                        }
                        //				name += index + strlen(buf);
                        name = name.substring(index + buf.Length(), name.length - 1)
                    }
                } else if (filterChar.code == 0) {
                    filterIndex++
                    //			name++;
                    name = name.substring(1)
                } else if (filterChar == '[') {
                    if (filter[filterIndex + 1] == '[') {
                        if (name[0] != '[') {
                            return false
                        }
                        filterIndex += 2
                        name = name.substring(1)
                    } else {
                        filterIndex++
                        found = false
                        while (filterIndex < filter.length && !found) {
                            if (filterChar == ']' && filter[filterIndex + 1] != ']') {
                                break
                            }
                            if (filter[filterIndex + 1] == '-' && filter.length > filterIndex + 2 && (filter[filterIndex + 2] != ']' || filter[filterIndex + 3] == ']')
                            ) {
                                if (casesensitive) {
                                    if (name[0] >= filterChar
                                        && name[0] <= filter[filterIndex + 2]
                                    ) {
                                        found = true
                                    }
                                } else {
//							if ( ::toupper(*name) >= ::toupper(*filterIndex) && ::toupper(*name) <= ::toupper(*(filterIndex+2)) ) {
                                    if (name[0] >= filterChar && name[0] <= filter[filterIndex + 2]) {
                                        found = true
                                    }
                                }
                                filterIndex += 3
                            } else {
                                if (casesensitive) {
                                    if (filterChar == name[0]) {
                                        found = true
                                    }
                                } else {
//							if ( ::toupper(*filterIndex) == ::toupper(*name) ) {
                                    if (filterChar == name[0]) {
                                        found = true
                                    }
                                }
                                filterIndex++
                            }
                        }
                        if (!found) {
                            return false
                        }
                        while (filterIndex < filter.length) {
                            if (filterChar == ']' && filter[filterIndex + 1] != ']') {
                                break
                            }
                            filterIndex++
                        }
                        filterIndex++
                        name = name.substring(1)
                    }
                } else {
                    if (casesensitive) {
                        if (filterChar != name[0]) {
                            return false
                        }
                    } else {
//				if ( ::toupper(*filterIndex) != ::toupper(*name) ) {
                        if (filterChar != name[0]) {
                            return false
                        }
                    }
                    filterIndex++
                    name = name.substring(1)
                }
            }
            return true
        }

        /*
         ============
         sprintf

         Sets the value of the string using a printf interface.
         ============
         */
        //public	friend int			sprintf( idStr &dest, const char *fmt, ... );
        //public <T>int sprintf( idStr string, final T...fmt) {
        //return sprintf(string.data.toCharArray(), fmt);
        //}
        //public <T>int sprintf( char[] string, final T...fmt) {
        //	int l = 0;
        //	char[] argptr;
        //	char []buffer=new char[32000];
        //
        //	va_start( argptr, fmt );
        //	l = idStr.vsnPrintf( buffer, sizeof(buffer)-1, fmt, argptr );
        //	va_end( argptr );
        //	buffer[sizeof(buffer)-1] = '\0';
        ////
        //	string = buffer;
        //	return l;
        //}
        /*
         ============
         vsprintf

         Sets the value of the string using a vprintf interface.
         ============
         */
        //public	friend int			vsprintf( idStr &dest, const char *fmt, va_list ap );
        fun vsprintf(string: idStr, fmt: String, vararg args: Any): Int { //char[] argptr) {
            val l: Int
            val buffer = emptyArray<String>() //new char[32000];
            l = vsnPrintf(buffer, 32000, fmt, *args)
            //	buffer[buffer.length-1] = '\0';

//	string = buffer;
            string.set(buffer[0])
            return l
        }

        // reallocate string data buffer
        fun ReAllocate(amount: Int, keepold: Boolean) {
//            char[] newbuffer;
            val newsize: Int
            val mod: Int
            assert(amount > 0)
            mod = amount % STR_ALLOC_GRAN
            newsize = if (0 == mod) {
                amount
            } else {
                amount + STR_ALLOC_GRAN - mod
            }
            alloced = newsize

//#ifdef USE_STRING_DATA_ALLOCATOR
//	newbuffer = stringDataAllocator.Alloc( alloced );
//#else
//            newbuffer = new char[alloced];
//#endif
//            if ( keepold && data ) {
//		data[ len ] = '\0';
//		strcpy( newbuffer, data );
//            }
//
//            if ( data && data != baseBuffer ) {
//#ifdef USE_STRING_DATA_ALLOCATOR
//		stringDataAllocator.Free( data );
//#else
//		delete [] data;
//#endif
//            }
//	data = newbuffer;
        }

        fun FreeData() { // free allocated string memory
            if (data != null /*&& data != baseBuffer*/) {
//#ifdef USE_STRING_DATA_ALLOCATOR
//		stringDataAllocator.Free( data );
//#else
//		delete[] data;
//#endif
//		data = baseBuffer;
            }
        }

        // format value in the given measurement with the best unit, returns the best unit
        fun BestUnit(format: String, value: Float, measure: Measure_t): Int {
            var value = value
            var unit = 1
            while (unit <= 3 && 1 shl unit * 10 < value) {
                unit++
            }
            unit--
            value /= (1 shl unit * 10).toFloat()
            //	sprintf( *this, format, value );
            data = String.format(format, value)
            data += " "
            data += units[measure.ordinal][unit] //TODO:ordinal
            return unit
        }

        fun SetUnit(format: String, value: Float, unit: Int, measure: Measure_t) {
            var value = value
            value /= (1 shl unit * 10).toFloat()
            //	sprintf( *this, format, value );
            data = String.format(format, value)
            data += " "
            data += units[measure.ordinal][unit]
        }

        override fun AllocBuffer(): ByteBuffer {
            return ByteBuffer.wrap(this.data.encodeToByteArray())
        }

        override fun Read(buffer: ByteBuffer) {
            len = buffer.limit()
            data = ""
            for (i in 0 until buffer.limit()) {
                data += Char(buffer.array()[i].toUShort())
            }
            alloced = len
        }

        override fun Write(): ByteBuffer {
            return AllocBuffer()
        }

        fun DynamicMemoryUsed(): Int {
//	return ( data == baseBuffer )  0 : alloced;
            return alloced
        }

        protected fun Init() {
            len = 0
            alloced = STR_ALLOC_BASE
            //	data = baseBuffer;
//	data[ 0 ] = '\0';
            data = ""
            //#ifdef ID_DEBUG_UNINITIALIZED_MEMORY
//	memset( baseBuffer, 0, sizeof( baseBuffer ) );
//#endif
        } // initialize string using base buffer

        protected fun EnsureAlloced(amount: Int) {
            EnsureAlloced(amount, true)
        }

        fun EnsureAlloced(amount: Int, keepold: Boolean) { // ensure string data buffer is large anough
            if (amount > alloced) {
                ReAllocate(amount, keepold)
            }
        }

        fun substring(beginIndex: Int): String {
            return data.substring(beginIndex)
        }

        override fun toString(): String {
            return data.trim(Char(0))
        }

        class ShowMemoryUsage_f : cmdFunction_t() {
            override fun run(args: neo.idlib.CmdArgs.idCmdArgs?) {
//#ifdef USE_STRING_DATA_ALLOCATOR
                idLib.common.Printf("%6d KB string memory (%d KB free in %d blocks, %d empty base blocks)\n")
                //                        stringDataAllocator.GetBaseBlockMemory() >> 10,
//                        stringDataAllocator.GetFreeBlockMemory() >> 10,
//                        stringDataAllocator.GetNumFreeBlocks(),
//                        stringDataAllocator.GetNumEmptyBaseBlocks());
//#endif
            }

            companion object {
                private val instance: cmdFunction_t = ShowMemoryUsage_f()
                fun getInstance(): cmdFunction_t {
                    return instance
                }
            }
        }

        fun IsNumeric(): Boolean {
            return try {
                data.toFloat()
                true
            } catch (e: NumberFormatException) {
                false
            }
        }

        fun LengthWithoutColors(): Int {
            return LengthWithoutColors(data)
        }

        fun HasUpper(): Boolean {
            return HasUpper(data)
        }

        fun HasLower(): Boolean {
            return HasLower(data)
        }

        fun IsColor(): Boolean {
            return IsColor(data)
        }

        class formatList_t(var gran: Int, var count: Int)
        companion object {
            @Transient

            val SIZE = (Integer.SIZE
                    + TempDump.CPP_class.Pointer.SIZE //Character.SIZE //pointer.//TODO:ascertain a char pointer size. EDIT: done.
                    + Integer.SIZE
                    + Char.SIZE_BITS * STR_ALLOC_BASE) //TODO:char size

            @Transient
            val BYTES = SIZE / java.lang.Byte.SIZE

            // elements of list need to decend in size
            var formatList: Array<formatList_t> = arrayOf(
                formatList_t(1000000000, 0),
                formatList_t(1000000, 0),
                formatList_t(1000, 0)
            )
            var index = 0

            //int numFormatList = sizeof(formatList) / sizeof( formatList[0] );
            var numFormatList = formatList.size
            var str: Array<StringBuffer> = Array(4) { StringBuffer() } // in case called by nested functions
            fun parseStr(str: String): idStr {
                return idStr(str)
            }

            //public	char &				operator[]( int index );
            //
            //public	void				operator=( const idStr &text );
            //public	friend idStr		operator+( const char *a, const idStr &b );
            fun plus(a: String, b: idStr): idStr {
                val result = idStr(a)
                result.Append(b.data)
                return result
            }

            // char * methods to replace library functions
            fun Length(s: CharArray): Int {
                var i: Int
                i = 0
                while (i < s.size && s[i].code != 0) {
                    i++
                }
                return i
            }


            fun IsNumeric(s: String): Boolean {
                return try {
                    s.toFloat()
                    true
                } catch (e: NumberFormatException) {
                    false
                }
            }

            fun ToLower(s: CharArray): CharArray {
                var i = 0
                while (i < s.size && s[i].code != 0) {
                    if (CharIsUpper(s[i].code)) {
                        s[i] = s[i].lowercaseChar()
                    }
                    i++
                }
                return s
            }

            fun ToUpper(s: CharArray): CharArray {
                var i = 0
                while (i < s.size && s[i].code != 0) {
                    if (CharIsLower(s[i].code)) {
                        s[i] = s[i].uppercaseChar()
                    }
                    i++
                }
                return s
            }


            fun isdigit(c: Char): Boolean {
                return c in '0'..'9'
            }

            fun IsColor(s: String): Boolean {
                val sArray = s.toCharArray()
                return sArray[0].code == C_COLOR_ESCAPE && sArray.size > 1 && sArray[1] != ' '
            }

            fun HasLower(s: String?): Boolean {
                return if (s == null) {
                    false
                } else s.uppercase(Locale.getDefault()) != s
                //	while ( *s ) {
//		if ( CharIsLower( *s ) ) {
//			return true;
//		}
//		s++;
//	}
            }

            //public	friend idStr		operator+( const idStr &a, const bool b );
            fun HasUpper(s: String?): Boolean {
                return if (s == null) {
                    false
                } else s.lowercase(Locale.getDefault()) != s
                //	while ( *s ) {
//		if ( CharIsLower( *s ) ) {
//			return true;
//		}
//		s++;
//	}
            }

            fun LengthWithoutColors(s: String?): Int {
                val len: Int
                var p = 0
                if (s == null) {
                    return 0
                }
                //char[]sArray=s.toCharArray();
                len = s.length
                //	p = s;
//	while( sArray[p]!=0 ) {
                if (IsColor(s)) {
                    p += 2
                    //			continue;
                }
                //		p++;
//		len++;
//	}
                return len - p
            }

            fun RemoveColors(s: String): String {
                var string = ""
                var a = 0
                while (a < s.length) {
                    if (IsColor(s.substring(a))) {
                        a++
                    } else {
                        string += s[a]
                    }
                    a++
                }
                //	*d = '\0';
                return string
            }

            fun Cmp(s1: CharArray, s2: CharArray): Int {
                return Cmp(TempDump.ctos(s1), TempDump.ctos(s2))
            }

            fun Cmp(s1: idStr, s2: idStr): Int {
                return Cmp(s1.toString(), s2.toString())
            }

            fun Cmp(s1: String, s2: String): Int {
                return ("" + s1).compareTo("" + s2)
            }

            //public	idStr &				operator+=( const int a );
            //public	idStr &				operator+=( const unsigned a );
            fun Cmpn(s1: String, s2: String, n: Int): Int { //TODO:see if we can return booleans
                if (s1.isNotEmpty() && s2.isNotEmpty()) {
                    if (s1.length >= n && s2.length >= n) {
                        return Cmp(s1.substring(0, n), s2.substring(0, n))
                    }
                }
                return 1 //not equal
            }

            //public	idStr &				operator+=( const bool a );
            fun Icmp(t1: idToken, s2: String): Int {
                return Icmp(t1.data, s2)
            }

            //
            //						// case sensitive compare
            //public	friend bool			operator==( const idStr &a, const idStr &b );
            //public	friend bool			operator==( const idStr &a, const char *b );
            //public	friend bool			operator==( const char *a, const idStr &b );
            //
            //public	friend bool			operator!=( const idStr &a, const idStr &b );
            //public	friend bool			operator!=( const idStr &a, const char *b );
            //public	friend bool			operator!=( const char *a, const idStr &b );
            fun Icmp(t1: idStr, s2: String): Int {
                return Icmp(t1.data, s2)
            }

            fun Icmp(t1: idStr, s2: idStr): Int {
                return Icmp(t1.data, s2.data)
            }

            fun Icmp(t1: CharArray, s2: CharArray): Int {
                return Icmp(TempDump.ctos(t1), TempDump.ctos(s2))
            }

            fun Icmp(s1: String?, s2: String?): Int {
                return ("" + s1).compareTo("" + s2, ignoreCase = true)
            }

            fun Icmpn(s1: String, s2: String, n: Int): Int {
                if (s1.isNotEmpty() && s2.isNotEmpty()) {
                    if (s1.length >= n && s2.length >= n) {
                        return Icmp(s1.substring(0, n), s2.substring(0, n))
                    }
                }
                return 1 //not equal
            }

            fun Icmpn(s1: idStr, s2: idStr, n: Int): Int {
                return Icmpn(s1.toString(), s2.toString(), n)
            }

            fun Icmpn(s1: idStr, s2: String, n: Int): Int {
                return Icmpn(s1.toString(), s2, n)
            }

            fun IcmpNoColor(s1: String, s2: String): Int {
                val s1Array = s1.toCharArray()
                val s2Array = s2.toCharArray()
                var c1 = 0
                var c2 = 0
                var d: Int
                do {
                    while (IsColor(s1)) {
                        c1 += 2
                    }
                    while (IsColor(s2)) {
                        c2 += 2
                    }
                    c1++
                    c2++
                    d = s1Array[c1] - s2Array[c2]
                    while (d != 0) {
                        if (c1 <= 'Z'.code && c1 >= 'A'.code) {
                            d += 'a' - 'A'
                            if (0 == d) {
                                break
                            }
                        }
                        if (c2 <= 'Z'.code && c2 >= 'A'.code) {
                            d -= 'a' - 'A'
                            if (0 == d) {
                                break
                            }
                        }
                        return (INTSIGNBITNOTSET(d) shl 1) - 1
                    }
                } while (c1 != 0)
                return 0 // strings are equal
            }

            // compares paths and makes sure folders come first
            fun IcmpPath(s1: String, s2: String): Int {
                return Paths.get(s1)
                    .compareTo(Paths.get(s2)) //TODO: whats the "make sure fodlers come first" all about

//            char[] s1Array = s1.toCharArray();
//            char[] s2Array = s2.toCharArray();
//            int i1 = 0, i2 = 0, d;
//            char c1, c2;
//
////#if 0
////#if !defined( _WIN32 )
////	idLib.common.Printf( "WARNING: IcmpPath used on a case-sensitive filesystem\n" );
////#endif
//            do {
//                c1 = s1Array[i1++];
//                c2 = s2Array[i2++];
//
//                d = c1 - c2;
//                while (d != 0) {
//                    if (c1 <= 'Z' && c1 >= 'A') {
//                        d += ('a' - 'A');
//                        if (0 == d) {
//                            break;
//                        }
//                    }
//                    if (c1 == '\\') {
//                        d += ('/' - '\\');
//                        if (0 == d) {
//                            break;
//                        }
//                    }
//                    if (c2 <= 'Z' && c2 >= 'A') {
//                        d -= ('a' - 'A');
//                        if (0 == d) {
//                            break;
//                        }
//                    }
//                    if (c2 == '\\') {
//                        d -= ('/' - '\\');
//                        if (0 == d) {
//                            break;
//                        }
//                    }
//                    // make sure folders come first
//                    while (c1 != 0) {
//                        if (c1 == '/' || c1 == '\\') {
//                            break;
//                        }
//                        c1 = s1Array[i1++];
//                    }
//                    while (c2 != 0) {
//                        if (c2 == '/' || c2 == '\\') {
//                            break;
//                        }
//                        c2 = s2Array[i2++];
//                    }
//                    if (c1 != 0 && c2 == 0) {
//                        return -1;
//                    } else if (c1 == 0 && c2 != 0) {
//                        return 1;
//                    }
//                    // same folder depth so use the regular compare
//                    return (INTSIGNBITNOTSET(d) << 1) - 1;
//                }
//            } while (c1 != 0);
//
//            return 0;
            }

            fun IcmpnPath(s1: String, s2: String, n: Int): Int { // compares paths and makes sure folders come first
                var n = n
                val s1Array = s1.toCharArray()
                val s2Array = s2.toCharArray()
                var c1 = 0
                var c2 = 0
                var d: Int
                assert(n >= 0)
                do {
                    c1++
                    c2++
                    if (0 == n--) {
                        return 0 // strings are equal until end point
                    }
                    d = s1Array[c1] - s2Array[c2]
                    while (d != 0) {
                        if (c1 <= 'Z'.code && c1 >= 'A'.code) {
                            d += 'a' - 'A'
                            if (0 == d) {
                                break
                            }
                        }
                        if (c1 == '\\'.code) {
                            d += '/' - '\\'
                            if (0 == d) {
                                break
                            }
                        }
                        if (c2 <= 'Z'.code && c2 >= 'A'.code) {
                            d -= 'a' - 'A'
                            if (0 == d) {
                                break
                            }
                        }
                        if (c2 == '\\'.code) {
                            d -= '/' - '\\'
                            if (0 == d) {
                                break
                            }
                        }
                        // make sure folders come first
                        while (c1 != 0) {
                            if (c1 == '/'.code || c1 == '\\'.code) {
                                break
                            }
                            c1++
                        }
                        while (c2 != 0) {
                            if (c2 == '/'.code || c2 == '\\'.code) {
                                break
                            }
                            c2++
                        }
                        if (c1 != 0 && c2 == 0) {
                            return -1
                        } else if (c1 == 0 && c2 != 0) {
                            return 1
                        }
                        // same folder depth so use the regular compare
                        return (INTSIGNBITNOTSET(d) shl 1) - 1
                    }
                } while (c1 != 0)
                return 0
            }

            /*
         ================
         idStr::Append

         never goes past bounds or leaves without a terminating 0
         ================
         */
            fun Append(dest: CharArray, size: Int, src: String) {
                val l1: Int
                l1 = TempDump.strLen(dest)
                if (l1 >= size) {
                    idLib.common.Error("idStr::Append: already overflowed")
                }
                Copynz(dest, l1, src, size - l1)
            }

            fun Append(dest: String, size: Int, src: String): String? {
                val l1: Int
                val l2: Int
                l1 = dest.length
                if (l1 >= size) {
                    idLib.common.Error("idStr::Append: already overflowed")
                    return null
                }
                l2 = dest.length + src.length
                return if (l2 > size) {
                    (dest + src).substring(0, size - l1)
                } else dest + src
            }


            fun Copynz(dest: CharArray, src: String, destsize: Int): CharArray? {
                return Copynz(dest, 0, src, destsize)
            }

            /*
         =============
         idStr::Copynz

         Safe strncpy that ensures a trailing zero
         =============
         */


            fun Copynz(dest: CharArray, offset: Int, src: String?, destsize: Int): CharArray? {
                if (null == src) {
                    idLib.common.Warning("idStr::Copynz: NULL src")
                    return null
                }
                if (destsize < 1) {
                    idLib.common.Warning("idStr::Copynz: destsize < 1")
                    return null
                }
                val len = Math.min(destsize - 1, src.length)
                System.arraycopy(src.toCharArray(), 0, dest, offset, len)
                dest[offset + len] = Char(0)
                return dest
            }

            fun Copynz(dest: CharArray, src: CharArray, destsize: Int) {
                Copynz(dest, TempDump.ctos(src), destsize)
            }

            //        @Deprecated
            //        public static void Copynz(String dest, final String src, int destsize) {
            //            if (null == src) {
            //                idLib.common.Warning("idStr::Copynz: NULL src");
            //                return;
            //            }
            //            if (destsize < 1) {
            //                idLib.common.Warning("idStr::Copynz: destsize < 1");
            //                return;
            //            }
            //
            //            idStr.Copynz(dest.toCharArray(), src, destsize);
            //        }
            fun Copynz(dest: Array<String>, src: String?, destsize: Int) {
                if (null == src) {
                    idLib.common.Warning("idStr::Copynz: NULL src")
                    return
                }
                if (destsize < 1) {
                    idLib.common.Warning("idStr::Copynz: destsize < 1")
                    return
                }
                dest[0] = String(Copynz(null as CharArray, src, destsize) ?: CharArray(0))
            }

            fun Copynz(dest: StringBuilder?, vararg src: String?) {
                if (src.isEmpty()) {
                    idLib.common.Warning("idStr::Copynz: NULL src")
                    return
                }
                if (null == dest) {
                    idLib.common.Warning("idStr::Copynz: NULL dest")
                    return
                }
                for (s in src) {
                    dest.append(s)
                }
            }


            fun snPrintf(dest: StringBuffer, size: Int, fmt: String, vararg args: Any): Int {
                var len: Int
                val bufferSize = 32000
                val buffer = StringBuffer(bufferSize)
                //
//	va_start( argptr, fmt );
//	len = vsprintf( buffer, fmt, argptr );
//	va_end( argptr );
                len = buffer.append(String.format(fmt, *args)).length
                if (len >= bufferSize) {
                    idLib.common.Error("idStr::snPrintf: overflowed buffer")
                }
                if (len >= size) {
                    idLib.common.Warning("idStr::snPrintf: overflow of %d in %d\n", len, size)
                    len = size
                }
                //            idStr.Copynz(dest, buffer, size);
                dest.delete(0, dest.capacity()) //clear
                dest.append(buffer) //TODO: use replace instead
                return len
            }


            fun snPrintf(dest: Array<String>, size: Int, fmt: String, vararg args: Any): Int {
                throw TODO_Exception()
                //	int len;
//	va_list argptr;
//	char buffer[32000];	// big, but small enough to fit in PPC stack
//
//	va_start( argptr, fmt );
//	len = vsprintf( buffer, fmt, argptr );
//	va_end( argptr );
//	if ( len >= sizeof( buffer ) ) {
//		idLib::common->Error( "idStr::snPrintf: overflowed buffer" );
//	}
//	if ( len >= size ) {
//		idLib::common->Warning( "idStr::snPrintf: overflow of %i in %i\n", len, size );
//		len = size;
//	}
//	idStr::Copynz( dest, buffer, size );
//	return len;
            }


            fun snPrintf(dest: CharArray, size: Int, fmt: String, vararg args: Any): Int {
                return snPrintf(0, dest, size, fmt, *args)
            }


            fun snPrintf(offset: Int, dest: CharArray, size: Int, fmt: String, vararg args: Any): Int {
                var length: Int
                //            char[] argptr;
                val buffer = StringBuilder(32000) // big, but small enough to fit in PPC stack

//	va_start( argptr, fmt );
//	len = vsprintf( buffer, fmt, argptr );
//	va_end( argptr );
                length = buffer.append(String.format(fmt, *args)).length
                if (length >= dest.size) {
                    idLib.common.Error("idStr::snPrintf: overflowed buffer")
                }
                if (length >= size) {
                    idLib.common.Warning("idStr::snPrintf: overflow of %d in %d\n", length, size)
                    length = size
                }
                Copynz(dest, offset, buffer.toString(), size)
                return length
            }

            /*
         ============
         idStr::vsnPrintf

         vsnprintf portability:

         C99 standard: vsnprintf returns the number of characters (excluding the trailing
         '\0') which would have been written to the final string if enough space had been available
         snprintf and vsnprintf do not write more than size bytes (including the trailing '\0')

         win32: _vsnprintf returns the number of characters written, not including the terminating null character,
         or a negative value if an output error occurs. If the number of characters to write exceeds count, then count
         characters are written and -1 is returned and no trailing '\0' is added.

         idStr::vsnPrintf: always appends a trailing '\0', returns number of characters written (not including terminal \0)
         or returns -1 on failure or if the buffer would be overflowed.
         ============
         */
            fun vsnPrintf(dest: Array<String>, size: Int, fmt: String, vararg args: Any): Int {
                var ret = 0
                ret = String.format(fmt, *args).also { dest[0] = it }.length
                if (ret < 0 || ret >= size) {
                    dest[0] = ""
                    return -1
                }
                return ret
            }

            //public	void				Append( const char *text );
            /*
         ============
         idStr::FindChar

         returns -1 if not found otherwise the index of the char
         ============
         */

            fun FindChar(str: String, c: Char, start: Int = 0, end: Int = -1): Int {
                var end = end
                val strArray = str.toCharArray()
                var i: Int
                if (end == -1) {
//		end = strlen( str ) - 1;
                    end = str.length
                }
                i = start
                while (i < end) {
                    if (strArray[i] == c) {
                        return i
                    }
                    i++
                }
                return -1
            }

            /*
         ============
         idStr::FindText

         returns -1 if not found otherwise the index of the text
         ============
         */


            fun FindText(
                str: String,
                text: String,
                casesensitive: Boolean = true,
                start: Int = 0,
                end: Int = -1
            ): Int {
                var end = end
                if (end == -1) {
                    end = str.length
                }
                val indexOfText = str.substring(start, end).indexOf(text)
                return if (casesensitive) {
                    if (indexOfText == -1) indexOfText else indexOfText + start
                } else {
                    str.substring(start, end).lowercase(Locale.getDefault())
                        .indexOf(text.lowercase(Locale.getDefault()))
                }
            }

            /*
         =============
         idStr::StripMediaName

         makes the string lower case, replaces backslashes with forward slashes, and removes extension
         =============
         */
            fun StripMediaName(name: String, mediaName: idStr) {
//	char c;
                mediaName.Empty()
                for (c in name.toCharArray()) {
                    // truncate at an extension
                    if (c == '.') {
                        break
                    }
                    // convert backslashes to forward slashes
                    if (c == '\\') {
                        mediaName.Append('/')
                    } else {
                        mediaName.Append(ToLower(c))
                    }
                }
            }

            fun CheckExtension(name: String, ext: String): Boolean {
                var c1 = name.length - 1
                var c2 = ext.length - 1
                var d: Int
                //TODO:double check if its working
                do {
                    d = name[c1] - ext[c2]
                    while (d != 0) {
                        if (c1 <= 'Z'.code && c1 >= 'A'.code) {
                            d += 'a' - 'A'
                            if (0 == d) {
                                break
                            }
                        }
                        if (c2 <= 'Z'.code && c2 >= 'A'.code) {
                            d -= 'a' - 'A'
                            if (0 == d) {
                                break
                            }
                        }
                        return false
                    }
                    c1--
                    c2--
                } while (c1 > 0 && c2 > 0)
                return c1 >= 0
            }

            fun FloatArrayToString(array: FloatArray, length: Int, precision: Int): String {
                var i: Int
                val n: Int
                var format: String
                val s: StringBuffer

                // use an array of string so that multiple calls won't collide
                str[index] = StringBuffer(16384)
                s = str[index]
                index = index + 1 and 3
                format = String.format("%%.%df", precision)
                n = snPrintf(s, s.capacity(), format, array[0])
                format = String.format(" %%.%df", precision)
                i = 1
                while (i < length) {
                    s.append(String.format(format, array[i]))
                    i++
                }
                return s.toString()
            }

            // hash keys
            fun Hash(string: CharArray): Int {
                var i: Int
                var hash = 0
                i = 0
                while (i < string.size && string[i] != '\u0000') {
                    hash += string[i].code * (i + 119)
                    i++
                }
                return hash
            }

            fun Hash(string: String): Int {
                return Hash(string.toCharArray())
            }

            fun Hash(string: CharArray, length: Int): Int {
                var i: Int
                var hash = 0
                i = 0
                while (i < length) {
                    hash += string[i].code * (i + 119)
                    i++
                }
                return hash
            }

            // case insensitive
            fun IHash(string: CharArray): Int {
                var i: Int
                var hash = 0
                i = 0
                while (i < string.size && string[i] != '\u0000') {
                    //TODO:eliminate '\0' from char strings.
                    hash += ToLower(string[i]).code * (i + 119)
                    i++
                }
                return hash
            }

            // case insensitive
            fun IHash(string: CharArray, length: Int): Int {
                var i: Int
                var hash = 0
                i = 0
                while (i < length) {
                    hash += ToLower(string[i]).code * (i + 119)
                    i++
                }
                return hash
            }

            // character methods
            fun ToLower(c: Char): Char {
                return if (c <= 'Z' && c >= 'A') {
                    (c.code + ('a' - 'A')).toChar()
                } else c
            }

            fun ToUpper(c: Char): Char {
                return if (c >= 'a' && c <= 'z') {
                    (c.code - ('a' - 'A')).toChar()
                } else c
            }


            fun CharIsPrintable(c: Int): Boolean {
                // test for regular ascii and western European high-ascii chars
                return c >= 0x20 && c <= 0x7E || c >= 0xA1 && c <= 0xFF
            }

            fun CharIsLower(c: Int): Boolean {
                // test for regular ascii and western European high-ascii chars
                return c >= 'a'.code && c <= 'z'.code || c >= 0xE0 && c <= 0xFF
            }

            fun CharIsUpper(c: Int): Boolean {
                // test for regular ascii and western European high-ascii chars
                return c <= 'Z'.code && c >= 'A'.code || c >= 0xC0 && c <= 0xDF
            }

            fun CharIsAlpha(c: Int): Boolean {
                // test for regular ascii and western European high-ascii chars
                return (c >= 'a'.code && c <= 'z'.code || c >= 'A'.code && c <= 'Z'.code
                        || c >= 0xC0 && c <= 0xFF)
            }

            fun CharIsNumeric(c: Int): Boolean {
                return c <= '9'.code && c >= '0'.code
            }

            fun CharIsNewLine(c: Char): Boolean {
                return c == '\n' || c == '\r' /*|| c == '\v'*/
            }

            /*
         ============
         idStr::Last

         returns -1 if not found otherwise the index of the char
         ============
         */
            fun CharIsTab(c: Char): Boolean {
                return c == '\t'
            }

            fun ColorIndex(c: Int): Int {
                return c and 15
            }


            fun ColorForIndex(i: Int): idVec4 {
                return g_color_table[i and 15]
            }

            fun InitMemory() {
//#ifdef USE_STRING_DATA_ALLOCATOR
//	stringDataAllocator.Init();
//#endif
            }

            fun ShutdownMemory() {
//#ifdef USE_STRING_DATA_ALLOCATOR
//	stringDataAllocator.Shutdown();
//#endif
            }

            fun PurgeMemory() {
//#ifdef USE_STRING_DATA_ALLOCATOR
//	stringDataAllocator.FreeEmptyBaseBlocks();
//#endif
            }


            fun FormatNumber(number: Int): idStr {
                var number = number
                val string = idStr()
                var hit: Boolean

                // reset
                for (i in 0 until numFormatList) {
                    val li = formatList[i]
                    li.count = 0
                }

                // main loop
                do {
                    hit = false
                    for (i in 0 until numFormatList) {
                        val li = formatList[i]
                        if (number >= li.gran) {
                            li.count++
                            number -= li.gran
                            hit = true
                            break
                        }
                    }
                } while (hit)

                // print out
                var found = false
                for (i in 0 until numFormatList) {
                    val li = formatList[i]
                    if (li.count != 0) {
                        if (!found) {
                            string.plusAssign(va("%d,", li.count))
                        } else {
//				string += va( "%3.3i,", li.count );
                            string.plusAssign(va("%3.3i,", li.count))
                        }
                        found = true
                    } else if (found) {
//			string += va( "%3.3i,", li->count );
                        string.plusAssign(va("%3.3i,", li.count))
                    }
                }
                if (found) {
//		string += va( "%3.3i", number );
                    string.plusAssign(va("%3.3i,", number))
                } else {
//		string += va( "%d", number );
                    string.plusAssign(va("%d,", number))
                }

                // pad to proper size
                val count = 11 - string.Length()
                for (i in 0 until count) {
                    string.Insert(" ", 0)
                }
                return string
            }
        }
    }
}
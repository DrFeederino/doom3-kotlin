package neo.idlib

import neo.Game.Projectile.idProjectile.projectileFlags_s
import neo.Game.idEntity.entityFlags_s
import neo.framework.CVarSystem
import neo.framework.CVarSystem.idCVarSystem
import neo.framework.Common
import neo.framework.Common.idCommon
import neo.framework.FileSystem_h
import neo.framework.FileSystem_h.idFileSystem
import neo.idlib.BV.idBounds
import neo.idlib.Dict_h.idDict
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.ctos
import neo.idlib.math.*
import neo.sys.idSys
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.experimental.xor

/*
 ===============================================================================

 Byte order functions

 ===============================================================================
 */
fun BigShort(l: Short): Short {
    return if (SWAP_TEST) {
        ShortSwap(l)
    } else {
        ShortNoSwap(l)
    }
}

fun BigLong(l: Int): Int {
    return if (SWAP_TEST) {
        LongSwap(l)
    } else {
        LongNoSwap(l)
    }
}

/*
 ===============================================================================

 Assertion

 ===============================================================================
 */
fun AssertFailed(file: String, line: Int, expression: String) {
    idLib.sys.DebugPrintf("\n\nASSERTION FAILED!\n%s(%d): '%s'\n", file, line, expression) //#ifdef _WIN32
    //	__asm int 0x03
    //#elif defined( __linux__ )
    //	__asm__ __volatile__ ("int $0x03");
    //#elif defined( MACOS_X )
    //	kill( getpid(), SIGINT );
    //#endif
}

/*
 ===============================================================================

 idLib contains stateless support classes and concrete types. Some classes
 do have static variables, but such variables are initialized once and
 read-only after initialization (they do not maintain a modifiable state).

 The interface pointers idSys, idCommon, idCVarSystem and idFileSystem
 should be set before using idLib. The pointers stored here should not
 be used by any part of the engine except for idLib.

 The frameNumber should be continuously set to the number of the current
 frame if frame base memory logging is required.

 ===============================================================================
 */
object idLib {

    var common: idCommon = Common.idCommonLocal()


    var cvarSystem: idCVarSystem = CVarSystem.idCVarSystemLocal()


    var fileSystem: idFileSystem = FileSystem_h.idFileSystemLocal()
    var frameNumber = 0
    lateinit var sys: idSys
    fun Init() {

        //	assert( sizeof( bool ) == 1 );
        // initialize little/big endian conversion
        Swap_Init() //
        //            // initialize memory manager
        //            Heap.Mem_Init();
        //
        // init string memory allocator
        idStr.InitMemory()

        // initialize generic SIMD implementation
        idSIMD.Init()

        // initialize math
        idMath.Init()

        // test idMatX
        //idMatX.Test()

        // test idPolynomial
        idPolynomial.Test()

        // initialize the dictionary string pools
        idDict.Init()
    }

    fun ShutDown() {

        // shut down the dictionary string pools
        idDict.Shutdown()

        // shut down the string memory allocator
        idStr.ShutdownMemory()

        // shut down the SIMD engine
        idSIMD.Shutdown()

        //            // shut down the memory manager
        //            Heap.Mem_Shutdown();
    }

    // wrapper to idCommon functions
    fun Error(vararg fmt: String?) { //	va_list		argptr;
        //	char		text[MAX_STRING_CHARS];
        //
        //	va_start( argptr, fmt );
        //	idStr::vsnPrintf( text, sizeof( text ), fmt, argptr );
        //	va_end( argptr );
        //
        //	common->Error( "%s", text );
    }

    fun Warning(vararg fmt: String?) {}
}

open class idException : RuntimeException {
    var error //[MAX_STRING_CHARS];
            : String = String()

    constructor() : super()
    constructor(text: String) : super(text) { //            strcpy(error, text);
        error = text
    }

    constructor(text: CharArray) : super(ctos(text)) { //            strcpy(error, text);
        error = ctos(text)
    }

    constructor(cause: Throwable) : super(cause)
}

const val MAX_STRING_CHARS = 1024 // max length of a string

//
//
//
///*
//===============================================================================
//
//	Types and defines used throughout the engine.
//
//===============================================================================
//*/
//
////typedef unsigned char			byte;		// 8 bits
////typedef unsigned short			word;		// 16 bits
////typedef unsigned int			dword;		// 32 bits
////typedef unsigned int			uint;
////typedef unsigned long			ulong;
//
////typedef int						qhandle_t;
//
// maximum world size
const val MAX_WORLD_COORD = 128 * 1024
const val MIN_WORLD_COORD = -128 * 1024
const val MAX_WORLD_SIZE: Int = MAX_WORLD_COORD - MIN_WORLD_COORD

//
// basic colors
/*
===============================================================================

Colors

===============================================================================
*/
val colorBlack: idVec4 = idVec4(0.0f, 0.0f, 0.0f, 1.0f)
val colorBlue: idVec4 = idVec4(0.0f, 0.0f, 1.0f, 1.0f)
val colorBrown: idVec4 = idVec4(0.40f, 0.35f, 0.08f, 1.0f)
val colorCyan: idVec4 = idVec4(0.0f, 1.0f, 1.0f, 1.0f)
val colorDkGrey: idVec4 = idVec4(0.25f, 0.25f, 0.25f, 1.0f)
val colorGreen: idVec4 = idVec4(0.0f, 1.0f, 0.0f, 1.0f)
val colorLtGrey: idVec4 = idVec4(0.75f, 0.75f, 0.75f, 1.0f)
val colorMagenta: idVec4 = idVec4(1.0f, 0.0f, 1.0f, 1.0f)
val colorMdGrey: idVec4 = idVec4(0.5f, 0.5f, 0.5f, 1.0f)
val colorOrange: idVec4 = idVec4(1.0f, 0.5f, 0.0f, 1.0f)
val colorPink: idVec4 = idVec4(0.73f, 0.40f, 0.48f, 1.0f)
val colorPurple: idVec4 = idVec4(0.60f, 0.0f, 0.60f, 1.0f)
val colorRed: idVec4 = idVec4(1.0f, 0.0f, 0.0f, 1.0f)
val colorWhite: idVec4 = idVec4(1.0f, 1.0f, 1.0f, 1.0f)
val colorYellow: idVec4 = idVec4(1.0f, 1.0f, 0.0f, 1.0f)

/*
================
Swap_Init
================
*/
private val SWAP_TEST: Boolean = Swap_IsBigEndian()
var colorMask: IntArray = intArrayOf(255, 0)

fun BIT(num: Int): Int {
    return 1 shl num
}

/*
================
ColorFloatToByte
================
*/
fun ColorFloatToByte(c: Float): Byte {
    return ((c * 255.0f).toInt() and colorMask[FLOATSIGNBITSET(c)]).toByte()
}

// packs color floats in the range [0,1] into an integer
/*
================
PackColor
================
*/
fun PackColor(color: idVec4): Long {
    val dw: Long
    val dx: Long
    val dy: Long
    val dz: Long
    dx = ColorFloatToByte(color.x).toLong()
    dy = ColorFloatToByte(color.y).toLong()
    dz = ColorFloatToByte(color.z).toLong()
    dw = ColorFloatToByte(color.w).toLong()
    return dx shl 0 or (dy shl 8) or (dz shl 16) or (dw shl 24)
}

/*
================
UnpackColor
================
*/
fun UnpackColor(color: Long, unpackedColor: idVec4) {
    unpackedColor.set(
        (color shr 0 and 255) * (1.0f / 255.0f),
        (color shr 8 and 255) * (1.0f / 255.0f),
        (color shr 16 and 255) * (1.0f / 255.0f),
        (color shr 24 and 255) * (1.0f / 255.0f)
    )
}

/*
================
PackColor
================
*/
fun PackColor(color: idVec3): Long {
    val dx: Long
    val dy: Long
    val dz: Long
    dx = ColorFloatToByte(color.x).toLong()
    dy = ColorFloatToByte(color.y).toLong()
    dz = ColorFloatToByte(color.z).toLong()
    return dx shl 0 or (dy shl 8) or (dz shl 16)
}

/*
================
UnpackColor
================
*/
fun UnpackColor(color: Long, unpackedColor: idVec3) {
    unpackedColor.set(
        (color shr 0 and 255) * (1.0f / 255.0f),
        (color shr 8 and 255) * (1.0f / 255.0f),
        (color shr 16 and 255) * (1.0f / 255.0f)
    )
}


fun LittleShort(l: Short): Short {
    return if (SWAP_TEST) {
        ShortSwap(l)
    } else {
        ShortNoSwap(l)
    }
}


fun LittleLong(l: Int): Int {
    return if (SWAP_TEST) {
        LongSwap(l)
    } else {
        LongNoSwap(l)
    }
}


fun LittleLong(l: Long): Int {
    return LittleLong(l.toInt()) //TODO:little or long?
}

fun LittleLong(b: ByteArray): Int {
    val l = BigInteger(b).toInt()
    return LittleLong(l)
}


fun BigFloat(l: Float): Float {
    return if (SWAP_TEST) {
        FloatSwap(l)
    } else {
        FloatNoSwap(l)
    }
}


fun LittleFloat(l: Float): Float {
    return if (SWAP_TEST) {
        FloatSwap(l)
    } else {
        FloatNoSwap(l)
    }
}


fun BigRevBytes(buffer: ByteBuffer, elcount: Int) {
    buffer.order(ByteOrder.BIG_ENDIAN)
}

fun LittleRevBytes(bp: FloatArray, elcount: Int) {
    if (SWAP_TEST) {
        val pb = IntArray(bp.size)
        for (a in bp.indices) {
            pb[a] = java.lang.Float.floatToIntBits(bp[a])
        }
        RevBytesSwap(pb,  /*elsize,*/elcount)
        for (b in pb.indices) {
            bp[b] = java.lang.Float.intBitsToFloat(pb[b])
        }
    }
}

fun LittleRevBytes(bp: ByteArray, offset: Int, elcount: Int) {
    if (SWAP_TEST) {
        RevBytesSwap(bp, 0,  /*elsize,*/elcount)
    } else {
        RevBytesNoSwap(bp,  /*elsize,*/elcount)
    }
}

fun LittleRevBytes(bp: ByteArray /*, int elsize*/, elcount: Int) {
    LittleRevBytes(bp, 0, elcount)
}

fun LittleRevBytes(v: idVec5) {
    if (SWAP_TEST) {
        val x = v.x
        val y = v.y
        v.x = v.t
        v.y = v.s
        v.s = y
        v.t = x
    }
}

fun LittleRevBytes(bounds: idBounds) {
    if (SWAP_TEST) {
        val a = idVec3(bounds[0])
        val b = idVec3(bounds[1])
        bounds[0] = b
        bounds[1] = a
    }
}

fun LittleRevBytes(angles: idAngles) {
    if (SWAP_TEST) {
        val pitch = angles.pitch
        angles.pitch = angles.roll
        angles.roll = pitch
    }
}

fun LittleBitField(bp: ByteArray, elsize: Int) {
    if (SWAP_TEST) {
        RevBitFieldSwap(bp, elsize)
    } else {
        RevBitFieldNoSwap(bp, elsize)
    }
}

fun LittleBitField(flags: entityFlags_s) {
    if (SWAP_TEST) { //TODO:expand this in the morning.
        flags.notarget = flags.networkSync or (false and flags.notarget.also { flags.networkSync = it })
        flags.noknockback = flags.hasAwakened or (false and flags.noknockback.also { flags.hasAwakened = it })
        flags.takedamage = flags.isDormant or (false and flags.takedamage.also { flags.isDormant = it })
        flags.hidden = flags.neverDormant or (false and flags.hidden.also { flags.neverDormant = it })
        flags.bindOrientated = flags.selected or (false and flags.bindOrientated.also { flags.selected = it })
        flags.solidForTeam =
            flags.forcePhysicsUpdate or (false and flags.solidForTeam.also { flags.forcePhysicsUpdate = it })
    }
}

fun LittleBitField(flags: projectileFlags_s) {
    if (SWAP_TEST) {
        flags.detonate_on_world =
            flags.detonate_on_actor or (false and flags.detonate_on_world.also { flags.detonate_on_actor = it })
        flags.isTracer = flags.noSplashDamage or (false and flags.isTracer.also { flags.noSplashDamage = it })
    }
}

fun SixtetsForInt(out: ByteArray, src: Int) //TODO:primitive byte cannot be passed by reference????
{
    if (SWAP_TEST) {
        SixtetsForIntLittle(out, src)
    } else {
        SixtetsForIntBig(out, src)
    }
}

fun IntForSixtets(`in`: ByteArray): Int {
    return if (SWAP_TEST) {
        IntForSixtetsLittle(`in`)
    } else {
        IntForSixtetsBig(`in`)
    }
}

/*
================
ShortSwap
================
*/
fun ShortSwap(l: Short): Short {
    val b1: Int
    val b2: Int
    b1 = (l.toInt() and 255)
    b2 = (l.toInt() shr 8 and 255)
    return ((b1 shl 8) + b2).toShort()
}

/*
================
ShortNoSwap
================
*/
fun ShortNoSwap(l: Short): Short {
    return l
}

/*
================
LongSwap
================
*/
fun LongSwap(l: Int): Int {
    val b1: Byte
    val b2: Byte
    val b3: Byte
    val b4: Byte
    b1 = (l shr 0 and 255).toByte()
    b2 = (l shr 8 and 255).toByte()
    b3 = (l shr 16 and 255).toByte()
    b4 = (l shr 24 and 255).toByte()
    return (b1.toInt() shl 24) + (b2.toInt() shl 16) + (b3.toInt() shl 8) + b4
}

/*
================
LongNoSwap
================
*/
fun LongNoSwap(l: Int): Int {
    return l
}

/*
================
FloatSwap
================
*/
fun FloatSwap(f: Float): Float { //	union {
    //		float	f;
    //		byte	b[4];
    //	} dat1, dat2;
    //
    //
    //	dat1.f = f;
    //	dat2.b[0] = dat1.b[3];
    //	dat2.b[1] = dat1.b[2];
    //	dat2.b[2] = dat1.b[1];
    //	dat2.b[3] = dat1.b[0];
    //
    //	return dat2.f;
    return ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putFloat(f).order(ByteOrder.BIG_ENDIAN).getFloat(0)
}

/*
================
FloatNoSwap
================
*/
fun FloatNoSwap(f: Float): Float {
    return f
}

/*
=====================================================================
RevBytesSwap

Reverses byte order in place.

INPUTS
bp       bytes to reverse
elsize   size of the underlying data type
elcount  number of elements to swap

RESULTS
Reverses the byte order in each of elcount elements.
===================================================================== */
fun RevBytesSwap(bp: ByteArray, offset: Int /*, int elsize*/, elcount: Int) {
    var elcount = elcount
    var p: Int
    var q: Int
    val elsize: Int
    p = offset
    elsize = bp.size
    if (elsize == 2) {
        q = p + 1
        while (elcount-- != 0) {
            bp[p] = bp[p] xor bp[q]
            bp[q] = bp[q] xor bp[p]
            bp[p] = bp[p] xor bp[q]
            p += 2
            q += 2
        }
        return
    }
    while (elcount-- != 0) {
        q = p + elsize - 1
        while (p < q) {
            bp[p] = bp[p] xor bp[q]
            bp[q] = bp[q] xor bp[p]
            bp[p] = bp[p] xor bp[q]
            ++p
            --q
        }
        p += elsize shr 1
    }
}

fun RevBytesSwap(bp: IntArray /*, int elsize*/, elcount: Int) {
    var elcount = elcount
    var p: Int
    var q: Int
    val elsize: Int //TODO:elsize is the number of bytes?
    p = 0
    elsize = bp.size
    if (elsize == 2) {
        q = p + 1
        while (elcount-- != 0) {
            bp[p] = bp[p] xor bp[q]
            bp[q] = bp[q] xor bp[p]
            bp[p] = bp[p] xor bp[q]
            p += 2
            q += 2
        }
        return
    }
    while (elcount-- != 0) {
        q = p + elsize - 1
        while (p < q) {
            bp[p] = bp[p] xor bp[q]
            bp[q] = bp[q] xor bp[p]
            bp[p] = bp[p] xor bp[q]
            ++p
            --q
        }
        p += elsize shr 1
    }
}

/*
=====================================================================
RevBytesSwap

Reverses byte order in place, then reverses bits in those bytes

INPUTS
bp       bitfield structure to reverse
elsize   size of the underlying data type

RESULTS
Reverses the bitfield of size elsize.
===================================================================== */
fun RevBitFieldSwap(bp: ByteArray, elsize: Int) {
    var elsize = elsize
    var i: Int
    var p: Int
    var t: Int
    var v: Int
    LittleRevBytes(bp,  /*elsize,*/1)
    p = 0
    while (elsize-- != 0) {
        v = bp[p].toInt()
        t = 0
        i = 7
        while (i != 0) {
            t = t shl 1
            v = v shr 1
            t = t or (v and 1)
            i--
        }
        bp[p++] = t.toByte()
    }
}

/*
================
RevBytesNoSwap
================
*/
fun RevBytesNoSwap(
    bp: ByteArray,  /*int elsize,*/
    elcount: Int
) {
    return
}

/*
================
RevBytesNoSwap
================
*/
fun RevBitFieldNoSwap(bp: ByteArray, elsize: Int) {
    return
}

/*
================
SixtetsForIntLittle
================
*/
fun SixtetsForIntLittle(out: ByteArray, src: Int) {
    val b = intArrayOf(
        src shr 0 and 0xff,  //TODO:check order
        src shr 8 and 0xff, src shr 16 and 0xff, src shr 24 and 0xff
    )
    out[0] = (b[0] and 0xfc shr 2).toByte()
    out[1] = ((b[0] and 0x3 shl 4) + (b[1] and 0xf0 shr 4)).toByte()
    out[2] = ((b[1] and 0xf shl 2) + (b[2] and 0xc0 shr 6)).toByte()
    out[3] = (b[2] and 0x3f).toByte()
}

/*
================
SixtetsForIntBig
TTimo: untested - that's the version from initial base64 encode
================
*/
fun SixtetsForIntBig(out: ByteArray, src: Int) {
    var src = src
    for (i in 0..3) {
        out[i] = (src and 0x3f).toByte()
        src = src shr 6
    }
}

/*
================
IntForSixtetsLittle
================
*/
fun IntForSixtetsLittle(`in`: ByteArray): Int {
    val b = IntArray(4)
    b[0] = b[0] or (`in`[0].toInt() shl 2)
    b[0] = b[0] or (`in`[1].toInt() and 0x30 shr 4)
    b[1] = b[1] or (`in`[1].toInt() and 0xf shl 4)
    b[1] = b[1] or (`in`[2].toInt() and 0x3c shr 2)
    b[2] = b[2] or (`in`[2].toInt() and 0x3 shl 6)
    b[2] = b[2] or `in`[3].toInt()
    return (b[0]) + (b[1] shl 8) + (b[2] shl 16) + (b[3] shl 0)
}

/*
================
IntForSixtetsBig
TTimo: untested - that's the version from initial base64 decode
================
*/
fun IntForSixtetsBig(`in`: ByteArray): Int {
    var ret = 0
    ret = ret or `in`[0].toInt()
    ret = ret or (`in`[1].toInt() shl 6)
    ret = ret or (`in`[2].toInt() shl 2 * 6)
    ret = ret or (`in`[3].toInt() shl 3 * 6)
    return ret
}

// move from Math.h to keep gcc happy

fun Max(x: Double, y: Double): Double {
    return if (x > y) x else y
}


fun Min(x: Double, y: Double): Double {
    return if (x < y) x else y
}


fun Max(x: Float, y: Float): Float {
    return if (x > y) x else y
}

fun Min(x: Float, y: Float): Float {
    return if (x < y) x else y
}


fun Max(x: Int, y: Int): Int {
    return if (x > y) x else y
}


fun Min(x: Int, y: Int): Int {
    return if (x < y) x else y
}

fun Swap_Init() { ////	byte	swaptest[2] = {1,0};
    //
    //	// set the byte swapping variables in a portable manner
    //	if ( !Swap_IsBigEndian() ) {
    ////	if ( *(short *)swaptest == 1) {
    //		// little endian ex: x86
    //		_BigShort = ShortSwap;
    //		_LittleShort = ShortNoSwap;
    //		_BigLong = LongSwap;
    //		_LittleLong = LongNoSwap;
    //		_BigFloat = FloatSwap;
    //		_LittleFloat = FloatNoSwap;
    //		_BigRevBytes = RevBytesSwap;
    //		_LittleRevBytes = RevBytesNoSwap;
    //		_LittleBitField = RevBitFieldNoSwap;
    //		_SixtetsForInt = SixtetsForIntLittle;
    //		_IntForSixtets = IntForSixtetsLittle;
    //	} else {
    //		// big endian ex: ppc
    //		_BigShort = ShortNoSwap;
    //		_LittleShort = ShortSwap;
    //		_BigLong = LongNoSwap;
    //		_LittleLong = LongSwap;
    //		_BigFloat = FloatNoSwap;
    //		_LittleFloat = FloatSwap;
    //		_BigRevBytes = RevBytesNoSwap;
    //		_LittleRevBytes = RevBytesSwap;
    //		_LittleBitField = RevBitFieldSwap;
    //		_SixtetsForInt = SixtetsForIntBig;
    //		_IntForSixtets = IntForSixtetsBig;
    //	}
}

/*
==========
Swap_IsBigEndian
==========
*/
fun Swap_IsBigEndian(): Boolean { //	byte	swaptest[2] = {1,0};
    //	return *(short *)swaptest != 1;
    return ByteOrder.BIG_ENDIAN == ByteOrder.nativeOrder()
}

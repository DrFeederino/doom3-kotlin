package neo.Tools.Compilers.RoqVQ

import neo.TempDump
import neo.framework.Common
import neo.idlib.Text.Lexer
import neo.idlib.Text.Parser.idParser
import neo.idlib.Text.Str
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Token.idToken
import neo.idlib.containers.idStrList
import java.util.*
import kotlin.math.abs

object RoqParam {
    fun parseRange(
        rangeStr: String,
        field: Int,
        skipnum: IntArray,
        startnum: IntArray,
        endnum: IntArray,
        numfiles: IntArray,
        padding: BooleanArray,
        numpadding: IntArray
    ): Int {
        val start = CharArray(64)
        val end = CharArray(64)
        val skip = CharArray(64)
        var stptr: Int
        var enptr: Int
        var skptr: Int
        var i: Int
        var realnum: Int
        i = 1
        realnum = 0
        //	stptr = start;
//	enptr = end;
//	skptr = skip;
        skptr = 0
        enptr = skptr
        stptr = enptr
        do {
            start[stptr++] = rangeStr[i++]
        } while (rangeStr[i] in '0'..'9')
        start[stptr] = '\u0000'
        if (rangeStr[i++] != '-') {
            Common.common.Error("Error: invalid range on middle \n")
        }
        do {
            end[enptr++] = rangeStr[i++]
        } while (rangeStr[i] in '0'..'9')
        end[enptr] = '\u0000'
        if (rangeStr[i] != ']') {
            if (rangeStr[i++] != '+') {
                Common.common.Error("Error: invalid range on close\n")
            }
            do {
                skip[skptr++] = rangeStr[i++]
            } while (rangeStr[i] in '0'..'9')
            skip[skptr] = '\u0000'
            skipnum[field] = TempDump.atoi(skip)
        } else {
            skipnum[field] = 1
        }
        startnum[field] = TempDump.atoi(start)
        endnum[field] = TempDump.atoi(end)
        numfiles[field] = abs(startnum[field] - endnum[field]) / skipnum[field] + 1
        realnum += numfiles[field]
        if (start[0] == '0' && start[1] != '\u0000') {
            padding[field] = true
            numpadding[field] = TempDump.strLen(start)
        } else {
            padding[field] = false
        }
        return realnum
    }

    fun parseTimecodeRange(
        rangeStr: String,
        field: Int,
        skipnum: IntArray,
        startnum: IntArray,
        endnum: IntArray,
        numfiles: IntArray,
        padding: BooleanArray,
        numpadding: IntArray
    ): Int {
        val start = CharArray(64)
        val end = CharArray(64)
        val skip = CharArray(64)
        var stptr: Int
        var enptr: Int
        var skptr: Int
        var i: Int
        var realnum: Int
        val hrs = intArrayOf(0)
        val mins = intArrayOf(0)
        val secs = intArrayOf(0)
        val frs = intArrayOf(0)
        i = 1 //skip the '['
        realnum = 0
        //	stptr = start;
//	enptr = end;
//	skptr = skip;
        skptr = 0
        enptr = skptr
        stptr = enptr
        do {
            start[stptr++] = rangeStr[i++]
        } while (rangeStr[i] in '0'..'9')
        start[stptr] = '\u0000'
        if (rangeStr[i++] != '-') {
            Common.common.Error("Error: invalid range on middle \n")
        }
        do {
            end[enptr++] = rangeStr[i++]
        } while (rangeStr[i] in '0'..'9')
        end[enptr] = '\u0000'
        if (rangeStr[i] != ']') {
            if (rangeStr[i++] != '+') {
                Common.common.Error("Error: invalid range on close\n")
            }
            do {
                skip[skptr++] = rangeStr[i++]
            } while (rangeStr[i] in '0'..'9')
            skip[skptr] = '\u0000'
            skipnum[field] = TempDump.atoi(skip)
        } else {
            skipnum[field] = 1
        }
        sscanf(start, "%2d%2d%2d%2d", hrs, mins, secs, frs)
        startnum[field] = hrs[0] * 30 * 60 * 60 + mins[0] * 60 * 30 + secs[0] * 30 + frs[0]
        sscanf(end, "%2d%2d%2d%2d", hrs, mins, secs, frs)
        endnum[field] = hrs[0] * 30 * 60 * 60 + mins[0] * 60 * 30 + secs[0] * 30 + frs[0]
        numfiles[field] = abs(startnum[field] - endnum[field]) / skipnum[field] + 1
        realnum += numfiles[field]
        if (start[0] == '0' && start[1] != '\u0000') {
            padding[field] = true
            numpadding[field] = TempDump.strLen(start)
        } else {
            padding[field] = false
        }
        return realnum
    }

    fun sscanf(start: CharArray, bla: String, vararg args: IntArray) {
        Scanner(TempDump.ctos(start)).use { scanner ->
            for (a in args) {
                a[0] = scanner.nextInt()
            }
        }
    }

    internal class roqParam {
        var numInputFiles = 0
        val outputFilename: idStr = idStr()
        private var addPath = false
        private val currentFile: idStr = idStr()
        private val currentPath: idStr = idStr()
        private var encodeVideo = false
        private val endPal: idStr = idStr()
        private var endPalette = false
        private var endnum: IntArray = IntArray(0)
        private var endnum2: IntArray = IntArray(0)
        private var field = 0
        private val file: idStrList = idStrList()
        private val file2: idStrList = idStrList()
        private var firstframesize = 0
        private var fixedPalette = false
        private var fullSearch = false
        private var hasSound = false
        private var isScaleable = false
        private var jpegDefault = 0
        private var justDelta = false
        private var justDeltaFlag = false
        private var keyColor = false
        private var keyR: Byte = 0
        private var keyG: Byte = 0
        private var keyB: Byte = 0
        private var make3DO = false
        private var makeVectors = false
        private var noAlphaAtAll = false
        private var normalframesize = 0
        private var numfiles: IntArray = IntArray(0)
        private var numpadding: IntArray = IntArray(0)
        private var numpadding2: IntArray = IntArray(0)
        private val onFrame: IntArray = intArrayOf(0)
        private var padding: BooleanArray = BooleanArray(0)
        private var padding2: BooleanArray = BooleanArray(0)

        //
        private var range: IntArray = IntArray(0)
        private var realnum = 0

        //
        private var scaleDown = false
        private var screenShots = false
        private var skipnum: IntArray = IntArray(0)
        private var skipnum2: IntArray = IntArray(0)
        private val soundfile: idStr = idStr()
        private val startPal: idStr = idStr()
        private var startPalette = false
        private var startnum: IntArray = IntArray(0)
        private var startnum2: IntArray = IntArray(0)
        private val tempFilename: idStr = idStr()
        private var twentyFourToThirty = false
        private var useTimecodeForRange = false

        //
        //
        fun RoqFilename(): String {
            return outputFilename.toString()
        }

        fun RoqTempFilename(): String {
            var i: Int
            var j: Int
            val len: Int
            j = 0
            len = outputFilename.Length()
            i = 0
            while (i < len) {
                if (outputFilename[i] == '/') {
                    j = i
                }
                i++
            }
            tempFilename.set(String.format("/%s.temp", outputFilename.toString().substring(j + 1)))
            return tempFilename.toString()
        }

        fun GetNextImageFilename(): String {
            val tempBuffer = idStr()
            var i: Int
            val len: Int
            onFrame[0]++
            GetNthInputFileName(tempBuffer, onFrame)
            if (justDeltaFlag == true) {
                onFrame[0]--
                justDeltaFlag = false
            }
            if (addPath == true) {
                currentFile.set(currentPath.toString() + "/" + tempBuffer)
            } else {
                currentFile.set(tempBuffer)
            }
            len = currentFile.Length()
            i = 0
            while (i < len) {
                if (currentFile[i] == '^') {
                    currentFile[i] = ' '
                }
                i++
            }
            return currentFile.toString()
        }

        fun SoundFilename(): String {
            return soundfile.toString()
        }

        fun InitFromFile(fileName: String) {
            val src: idParser
            val token = idToken()
            var i: Int
            var readarg: Int
            src = idParser(
                fileName,
                Lexer.LEXFL_NOSTRINGCONCAT or Lexer.LEXFL_NOSTRINGESCAPECHARS or Lexer.LEXFL_ALLOWPATHNAMES
            )
            if (!src.IsLoaded()) {
//		delete src;
                Common.common.Printf("Error: can't open param file %s\n", fileName)
                return
            }
            Common.common.Printf("initFromFile: %s\n", fileName)
            fullSearch = false
            scaleDown = false
            encodeVideo = false
            addPath = false
            screenShots = false
            startPalette = false
            endPalette = false
            fixedPalette = false
            keyColor = false
            justDelta = false
            useTimecodeForRange = false
            onFrame[0] = 0
            numInputFiles = 0
            currentPath.set(idStr('\u0000'))
            make3DO = false
            makeVectors = false
            justDeltaFlag = false
            noAlphaAtAll = false
            twentyFourToThirty = false
            hasSound = false
            isScaleable = false
            firstframesize = 56 * 1024
            normalframesize = 20000
            jpegDefault = 85
            realnum = 0
            while (true) {
                if (!src.ReadToken(token)) {
                    break
                }
                readarg = 0
                // input dir
                if (token.Icmp("input_dir") == 0) {
                    src.ReadToken(token)
                    addPath = true
                    currentPath.set(token)
                    //			common.Printf("  + input directory is %s\n", currentPath );
                    readarg++
                    continue
                }
                // input dir
                if (token.Icmp("scale_down") == 0) {
                    scaleDown = true
                    //			common.Printf("  + scaling down input\n" );
                    readarg++
                    continue
                }
                // full search
                if (token.Icmp("fullsearch") == 0) {
                    normalframesize += normalframesize / 2
                    fullSearch = true
                    readarg++
                    continue
                }
                // scaleable
                if (token.Icmp("scaleable") == 0) {
                    isScaleable = true
                    readarg++
                    continue
                }
                // input dir
                if (token.Icmp("no_alpha") == 0) {
                    noAlphaAtAll = true
                    //			common.Printf("  + scaling down input\n" );
                    readarg++
                    continue
                }
                if (token.Icmp("24_fps_in_30_fps_out") == 0) {
                    twentyFourToThirty = true
                    readarg++
                    continue
                }
                // video in
                if (token.Icmp("video_in") == 0) {
                    encodeVideo = true
                    //			common.Printf("  + Using the video port as input\n");
                    continue
                }
                //timecode range
                if (token.Icmp("timecode") == 0) {
                    useTimecodeForRange = true
                    firstframesize = 12 * 1024
                    normalframesize = 4500
                    //			common.Printf("  + Using timecode as range\n");
                    continue
                }
                // soundfile for making a .RnR
                if (token.Icmp("sound") == 0) {
                    src.ReadToken(token)
                    soundfile.set(token)
                    hasSound = true
                    //			common.Printf("  + Using timecode as range\n");
                    continue
                }
                // soundfile for making a .RnR
                if (token.Icmp("has_sound") == 0) {
                    hasSound = true
                    continue
                }
                // outfile
                if (token.Icmp("filename") == 0) {
                    src.ReadToken(token)
                    outputFilename.set(token)
                    i = outputFilename.Length()
                    //			common.Printf("  + output file is %s\n", outputFilename );
                    readarg++
                    continue
                }
                // starting palette
                if (token.Icmp("start_palette") == 0) {
                    src.ReadToken(token)
                    startPal.set(String.format("/LocalLibrary/vdxPalettes/%s", token))
                    //			common.Error("  + starting palette is %s\n", startPal );
                    startPalette = true
                    readarg++
                    continue
                }
                // ending palette
                if (token.Icmp("end_palette") == 0) {
                    src.ReadToken(token)
                    endPal.set(String.format("/LocalLibrary/vdxPalettes/%s", token))
                    //			common.Printf("  + ending palette is %s\n", endPal );
                    endPalette = true
                    readarg++
                    continue
                }
                // fixed palette
                if (token.Icmp("fixed_palette") == 0) {
                    src.ReadToken(token)
                    startPal.set(String.format("/LocalLibrary/vdxPalettes/%s", token))
                    //			common.Printf("  + fixed palette is %s\n", startPal );
                    fixedPalette = true
                    readarg++
                    continue
                }
                // these are screen shots
                if (token.Icmp("screenshot") == 0) {
//			common.Printf("  + shooting screen shots\n" );
                    screenShots = true
                    readarg++
                    continue
                }
                //	key_color	r g b
                if (token.Icmp("key_color") == 0) {
                    keyR = src.ParseInt().toByte()
                    keyG = src.ParseInt().toByte()
                    keyB = src.ParseInt().toByte()
                    keyColor = true
                    //			common.Printf("  + key color is %03d %03d %03d\n", keyR, keyG, keyB );
                    readarg++
                    continue
                }
                // only want deltas
                if (token.Icmp("just_delta") == 0) {
//			common.Printf("  + outputting deltas in the night\n" );
//			justDelta = true;
//			justDeltaFlag = true;
                    readarg++
                    continue
                }
                // doing 3DO
                if (token.Icmp("3DO") == 0) {
                    make3DO = true
                    readarg++
                    continue
                }
                // makes codebook vector tables
                if (token.Icmp("codebook") == 0) {
                    makeVectors = true
                    readarg++
                    continue
                }
                // set first frame size
                if (token.Icmp("firstframesize") == 0) {
                    firstframesize = src.ParseInt()
                    readarg++
                    continue
                }
                // set normal frame size
                if (token.Icmp("normalframesize") == 0) {
                    normalframesize = src.ParseInt()
                    readarg++
                    continue
                }
                // set normal frame size
                if (token.Icmp("stillframequality") == 0) {
                    jpegDefault = src.ParseInt()
                    readarg++
                    continue
                }
                if (token.Icmp("input") == 0) {
                    val num_files = 255
                    range = IntArray(num_files) // Mem_ClearedAlloc(num_files);
                    padding = BooleanArray(num_files) // Mem_ClearedAlloc(num_files);
                    padding2 = BooleanArray(num_files) // Mem_ClearedAlloc(num_files);
                    skipnum = IntArray(num_files) // Mem_ClearedAlloc(num_files);
                    skipnum2 = IntArray(num_files) // Mem_ClearedAlloc(num_files);
                    startnum = IntArray(num_files) // Mem_ClearedAlloc(num_files);
                    startnum2 = IntArray(num_files) // Mem_ClearedAlloc(num_files);
                    endnum = IntArray(num_files) // Mem_ClearedAlloc(num_files);
                    endnum2 = IntArray(num_files) // Mem_ClearedAlloc(num_files);
                    numpadding = IntArray(num_files) // Mem_ClearedAlloc(num_files);
                    numpadding2 = IntArray(num_files) // Mem_ClearedAlloc(num_files);
                    numfiles = IntArray(num_files) // Mem_ClearedAlloc(num_files);
                    val empty = idStr()
                    file.ensureSize(num_files, empty)
                    //                    file.AssureSize(num_files, empty);//TODO:should this really be called twice?
                    field = 0
                    realnum = 0
                    do {
                        src.ReadToken(token)
                        if (token.Icmp("end_input") != 0) {
                            var arg1: idStr
                            var arg2: idStr
                            var arg3: idStr
                            file[field] = token
                            while (src.ReadTokenOnLine(token) && token.Icmp("[") != 0) {
                                file[field].Append(token)
                            }
                            arg1 = token
                            while (src.ReadTokenOnLine(token) && token.Icmp("[") != 0) {
                                arg1.Append(token)
                            }
                            arg2 = token
                            while (src.ReadTokenOnLine(token) && token.Icmp("[") != 0) {
//						arg2 += token;
                                arg2.Append(token)
                            }
                            arg3 = token
                            while (src.ReadTokenOnLine(token) && token.Icmp("[") != 0) {
//						arg3 += token;
                                arg3.Append(token)
                            }
                            if (arg1[0] != '[') {
//						common.Printf("  + reading %s\n", file[field] );
                                range[field] = 0
                                numfiles[field] = 1
                                realnum++
                            } else {
                                if (arg1[0] == '[') {
                                    range[field] = 1
                                    realnum += if (useTimecodeForRange) {
                                        parseTimecodeRange(
                                            arg1.toString(),
                                            field,
                                            skipnum,
                                            startnum,
                                            endnum,
                                            numfiles,
                                            padding,
                                            numpadding
                                        )
                                        //								common.Printf("  + reading %s from %d to %d\n", file[field], startnum[field], endnum[field]);
                                    } else {
                                        parseRange(
                                            arg1.toString(),
                                            field,
                                            skipnum,
                                            startnum,
                                            endnum,
                                            numfiles,
                                            padding,
                                            numpadding
                                        )
                                        //								common.Printf("  + reading %s from %d to %d\n", file[field], startnum[field], endnum[field]);
                                    }
                                } else if (arg1[0] != '[' && arg2[0] == '[' && arg3[0] == '[') {  //a double ranger...
                                    var files1: Int
                                    var files2: Int
                                    file2[field] = arg1
                                    range[field] = 2
                                    files1 = parseRange(
                                        arg2.toString(),
                                        field,
                                        skipnum,
                                        startnum,
                                        endnum,
                                        numfiles,
                                        padding,
                                        numpadding
                                    )
                                    //							common.Printf("  + reading %s from %d to %d\n", file[field], startnum[field], endnum[field]);
                                    files2 = parseRange(
                                        arg3.toString(),
                                        field,
                                        skipnum2,
                                        startnum2,
                                        endnum2,
                                        numfiles,
                                        padding2,
                                        numpadding2
                                    )
                                    //							common.Printf("  + reading %s from %d to %d\n", file2[field], startnum2[field], endnum2[field]);
                                    if (files1 != files2) {
                                        Common.common.Error(
                                            "You had %d files for %s and %d for %s!",
                                            files1,
                                            arg1,
                                            files2,
                                            arg2
                                        )
                                    } else {
                                        realnum += files1 //not both, they are parallel
                                    }
                                } else {
                                    Common.common.Error("Error: invalid range on open (%s %s %s)\n", arg1, arg2, arg3)
                                }
                            }
                            field++
                        }
                    } while (token.Icmp("end_input") != 0)
                }
            }
            if (TwentyFourToThirty()) {
                realnum = realnum + (realnum shr 2)
            }
            numInputFiles = realnum
            Common.common.Printf("  + reading a total of %d frames in %s\n", numInputFiles, currentPath)
            //	delete src;
        }

        fun GetNthInputFileName(fileName: idStr, n: IntArray) {
            var i: Int
            var myfield: Int
            var index: Int
            val hrs: Int
            val mins: Int
            val secs: Int
            val frs: Int
            //	char tempfile[33], left[256], right[256], *strp;
            var tempfile: String
            var left: String
            var right: String
            var strp: Int
            if (n[0] > realnum) {
                n[0] = realnum
            }
            // overcome starting at zero by ++ing and then --ing.
            if (TwentyFourToThirty()) {
                n[0]++
                n[0] = n[0] / 5 * 4 + n[0] % 5
                n[0]--
            }
            i = 0
            myfield = 0
            while (i <= n[0]) {
                i += numfiles[myfield++]
            }
            myfield--
            i -= numfiles[myfield]
            if (range[myfield] == 1) {
                left = file[myfield].toString()
                strp = left.indexOf("*")
                strp++
                right = String.format("%s", left.substring(strp))
                index = if (startnum[myfield] <= endnum[myfield]) {
                    startnum[myfield] + (n[0] - i) * skipnum[myfield]
                } else {
                    startnum[myfield] - (n[0] - i) * skipnum[myfield]
                }
                if (padding[myfield] == true) {
                    if (useTimecodeForRange) {
                        hrs = index / (30 * 60 * 60)
                        mins = index / (30 * 60) % 60
                        secs = index / 30 % 60
                        frs = index % 30
                        fileName.set(
                            String.format(
                                "%s%.02d%.02d/%.02d%.02d%.02d%.02d%s",
                                left,
                                hrs,
                                mins,
                                hrs,
                                mins,
                                secs,
                                frs,
                                right
                            )
                        )
                    } else {
                        tempfile = String.format("%032d", index)
                        fileName.set(
                            String.format(
                                "%s%s%s",
                                left,
                                tempfile.substring(32 - numpadding[myfield]),
                                right
                            )
                        )
                    }
                } else {
                    if (useTimecodeForRange) {
                        hrs = index / (30 * 60 * 60)
                        mins = index / (30 * 60) % 60
                        secs = index / 30 % 60
                        frs = index % 30
                        fileName.set(
                            String.format(
                                "%s%.02d%.02d/%.02d%.02d%.02d%.02d%s",
                                left,
                                hrs,
                                mins,
                                hrs,
                                mins,
                                secs,
                                frs,
                                right
                            )
                        )
                    } else {
                        fileName.set(String.format("%s%d%s", left, index, right))
                    }
                }
            } else if (range[myfield] == 2) {
                left = file[myfield].toString()
                strp = left.indexOf("*")
                strp++
                right = String.format("%s", left.substring(strp))
                index = if (startnum[myfield] <= endnum[myfield]) {
                    startnum[myfield] + (n[0] - i) * skipnum[myfield]
                } else {
                    startnum[myfield] - (n[0] - i) * skipnum[myfield]
                }
                if (padding[myfield] == true) {
                    tempfile = String.format("%032d", index)
                    fileName.set(
                        String.format(
                            "%s%s%s",
                            left,
                            tempfile.substring(32 - numpadding[myfield]),
                            right
                        )
                    )
                } else {
                    fileName.set(String.format("%s%d%s", left, index, right))
                }
                left = file2[myfield].toString()
                strp = left.indexOf("*")
                strp++
                right = String.format("%s", left.substring(strp))
                index = if (startnum2[myfield] <= endnum2[myfield]) {
                    startnum2[myfield] + (n[0] - i) * skipnum2[myfield]
                } else {
                    startnum2[myfield] - (n[0] - i) * skipnum2[myfield]
                }
                if (padding2[myfield] == true) {
                    tempfile = String.format("%032d", index)
                    fileName.plusAssign(Str.va("\n%s%s%s", left, tempfile.substring(32 - numpadding2[myfield]), right))
                } else {
                    fileName.plusAssign(Str.va("\n%s%d%s", left, index, right))
                }
            } else {
                fileName.set(file[myfield].toString())
            }
        }

        fun MoreFrames(): Boolean {
            return onFrame[0] < numInputFiles
        }

        fun OutputVectors(): Boolean {
            return makeVectors
        }

        fun Timecode(): Boolean {
            return useTimecodeForRange
        }

        fun DeltaFrames(): Boolean {
            return justDelta
        }

        fun NoAlpha(): Boolean {
            return noAlphaAtAll
        }

        fun SearchType(): Boolean {
            return fullSearch
        }

        fun TwentyFourToThirty(): Boolean {
            return twentyFourToThirty
        }

        fun HasSound(): Boolean {
            return hasSound
        }

        fun NumberOfFrames(): Int {
            return numInputFiles
        }

        fun NormalFrameSize(): Int {
            return normalframesize
        }

        fun FirstFrameSize(): Int {
            return firstframesize
        }

        fun JpegQuality(): Int {
            return jpegDefault
        }

        fun IsScaleable(): Boolean {
            return isScaleable
        }
    }
}
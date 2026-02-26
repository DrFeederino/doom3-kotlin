package neo.sys

import neo.TempDump
import neo.TempDump.TODO_Exception
import neo.framework.BuildDefines
import neo.idlib.containers.CInt
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger

object win_cpu {
    const val HT_CANNOT_DETECT = 4
    const val HT_DISABLED = 2
    const val HT_ENABLED = 1
    const val HT_NOT_CAPABLE = 0
    const val HT_SUPPORTED_NOT_ENABLED = 3

    //                                                  // processors per physical processor when execute cpuid with 
    //                                                  // eax set to 1
    const val INITIAL_APIC_ID_BITS = -0x1000000 // EBX[31:24] Bits 24-31 (8 bits) return the 8-bit unique

    //                                                  // initial APIC ID for the processor this code is running on.
    //                                                  // Default value = 0xff if HT is not supported
    const val NUM_LOGICAL_BITS = 0x00FF0000 // EBX[23:16] Bit 16-23 in ebx contains the number of logical
    const val _REG_EAX = 0
    const val _REG_EBX = 1
    const val _REG_ECX = 2
    const val _REG_EDX = 3

    /*
     ==============================================================

     Clock ticks

     ==============================================================
     */
    val precisionControlField: Array<String> = arrayOf(
        "Single Precision (24-bits)",
        "Reserved",
        "Double Precision (53-bits)",
        "Double Extended Precision (64-bits)"
    )
    val roundingControlField: Array<String> = arrayOf(
        "Round to nearest",
        "Round down",
        "Round up",
        "Round toward zero"
    )
    val statusWordFlags: Array<bitFlag_s> = arrayOf(
        bitFlag_s("Invalid operation", 0),
        bitFlag_s("Denormalized operand", 1),
        bitFlag_s("Divide-by-zero", 2),
        bitFlag_s("Numeric overflow", 3),
        bitFlag_s("Numeric underflow", 4),
        bitFlag_s("Inexact result (precision)", 5),
        bitFlag_s("Stack fault", 6),
        bitFlag_s("Error summary status", 7),
        bitFlag_s("FPU busy", 15),
        bitFlag_s("", 0)
    )

    /*
     ==============================================================

     CPU

     ==============================================================
     */
    var controlWordFlags: Array<bitFlag_s> = arrayOf(
        bitFlag_s("Invalid operation", 0),
        bitFlag_s("Denormalized operand", 1),
        bitFlag_s("Divide-by-zero", 2),
        bitFlag_s("Numeric overflow", 3),
        bitFlag_s("Numeric underflow", 4),
        bitFlag_s("Inexact result (precision)", 5),
        bitFlag_s("Infinity control", 12),
        bitFlag_s("", 0)
    )
    var fpuString: CharArray = CharArray(2048)

    /*
     ================
     Sys_ClockTicksPerSecond
     ================
     */
    private var ticks = 0.0f //TODO:make final.

    /*
     ================
     Sys_GetClockTicks
     ================
     */
    fun Sys_GetClockTicks(): Long {
        return System.nanoTime()
        //#if 0
//
//	LARGE_INTEGER li;
//
//	QueryPerformanceCounter( &li );
//	return = (double ) li.LowPart + (double) 0xFFFFFFFF * li.HighPart;
//
//#else
//
//	unsigned long lo, hi;
//
//	__asm {
//		push ebx
//		xor eax, eax
//		cpuid
//		rdtsc
//		mov lo, eax
//		mov hi, edx
//		pop ebx
//	}
//	return (double ) lo + (double) 0xFFFFFFFF * hi;
//
//#endif
    }

    fun Sys_ClockTicksPerSecond(): Float {

//#if 0
//
//	if ( !ticks ) {
//		LARGE_INTEGER li;
//		QueryPerformanceFrequency( &li );
//		ticks = li.QuadPart;
//	}
//
//#else
        if (BuildDefines._WIN32) {
            if (ticks == 0.0f) {
                try {
                    val procSpeed = TempDump.atoi(wmic("cpu get MaxClockSpeed"))
                    ticks = (procSpeed * 1000000L).toFloat()
                } catch (ex: IOException) {
                    Logger.getLogger(win_cpu::class.java.name).log(Level.SEVERE, null, ex)
                }
            }
        }

//#endif
        return ticks
    }

    /*
     ================
     HasCPUID
     ================
     */
    @Throws(IOException::class)
    fun HasCPUID(): Boolean {
        if (BuildDefines._WIN32) {
            val cpuid: String = wmic("cpu get ProcessorId")
            return cpuid.isNotEmpty()
        }
        if (BuildDefines._MACOSX) {
            val command = "system_profiler SPHardwareDataType"
            val sysctl: Process = Runtime.getRuntime().exec(command)
            val scanner = Scanner(sysctl.inputStream)
            scanner.useLocale(Locale.US)
            val sb = StringBuffer()
            while (scanner.hasNextLine()) {
                sb.append(scanner.nextLine())
            }
            val cpu = sb.toString()
            return cpu.isNotEmpty() && cpu.contains("Apple")
        }
        throw TODO_Exception()
        //	__asm
//	{
//		pushfd						// save eflags
//		pop		eax
//		test	eax, 0x00200000		// check ID bit
//		jz		set21				// bit 21 is not set, so jump to set_21
//		and		eax, 0xffdfffff		// clear bit 21
//		push	eax					// save new value in register
//		popfd						// store new value in flags
//		pushfd
//		pop		eax
//		test	eax, 0x00200000		// check ID bit
//		jz		good
//		jmp		err					// cpuid not supported
//set21:
//		or		eax, 0x00200000		// set ID bit
//		push	eax					// store new value
//		popfd						// store new value in EFLAGS
//		pushfd
//		pop		eax
//		test	eax, 0x00200000		// if bit 21 is on
//		jnz		good
//		jmp		err
//	}
//
//err:
//	return false;
//good:
//	return true;
    }

    /*
     ================
     CPUID
     ================
     */
    fun CPUID(func: Int, regs: IntArray /*[4]*/) {
        throw TODO_Exception()
        //	unsigned regEAX, regEBX, regECX, regEDX;
//
//	__asm pusha
//	__asm mov eax, func
//	__asm __emit 00fh
//	__asm __emit 0a2h
//	__asm mov regEAX, eax
//	__asm mov regEBX, ebx
//	__asm mov regECX, ecx
//	__asm mov regEDX, edx
//	__asm popa
//
//	regs[_REG_EAX] = regEAX;
//	regs[_REG_EBX] = regEBX;
//	regs[_REG_ECX] = regECX;
//	regs[_REG_EDX] = regEDX;
    }

    /*
     ================
     IsAMD
     ================
     */
    fun IsAMD(): Boolean {
        return System.getenv("PROCESSOR_IDENTIFIER")?.startsWith("AMD") ?: false
        //        throw new TODO_Exception();
//	char pstring[16];
//	char processorString[13];
//
//	// get name of processor
//	CPUID( 0, ( unsigned int * ) pstring );
//	processorString[0] = pstring[4];
//	processorString[1] = pstring[5];
//	processorString[2] = pstring[6];
//	processorString[3] = pstring[7];
//	processorString[4] = pstring[12];
//	processorString[5] = pstring[13];
//	processorString[6] = pstring[14];
//	processorString[7] = pstring[15];
//	processorString[8] = pstring[8];
//	processorString[9] = pstring[9];
//	processorString[10] = pstring[10];
//	processorString[11] = pstring[11];
//	processorString[12] = 0;
//
//	if ( strcmp( processorString, "AuthenticAMD" ) == 0 ) {
//		return true;
//	}
//	return false;
    }

    /*
     ================
     HasCMOV
     ================
     */
    fun HasCMOV(): Boolean {
        if (true) {
            return true
        }
        throw TODO_Exception()
        //	unsigned regs[4];
//
//	// get CPU feature bits
//	CPUID( 1, regs );
//
//	// bit 15 of EDX denotes CMOV existence
//	if ( regs[_REG_EDX] & ( 1 << 15 ) ) {
//		return true;
//	}
//	return false;
    }

    /*
     ================
     Has3DNow
     ================
     */
    fun Has3DNow(): Boolean {
        if (true) {
            return IsAMD()
        }
        throw TODO_Exception()
        //	unsigned regs[4];
//
//	// check AMD-specific functions
//	CPUID( 0x80000000, regs );
//	if ( regs[_REG_EAX] < 0x80000000 ) {
//		return false;
//	}
//
//	// bit 31 of EDX denotes 3DNow! support
//	CPUID( 0x80000001, regs );
//	if ( regs[_REG_EDX] & ( 1 << 31 ) ) {
//		return true;
//	}
//
//	return false;
    }

    /*
     ================
     HasMMX
     ================
     */
    fun HasMMX(): Boolean {
        if (true) {
            return true
        }
        throw TODO_Exception()
        //	unsigned regs[4];
//
//	// get CPU feature bits
//	CPUID( 1, regs );
//
//	// bit 23 of EDX denotes MMX existence
//	if ( regs[_REG_EDX] & ( 1 << 23 ) ) {
//		return true;
//	}
//	return false;
    }

    /*
     ================
     HasSSE
     ================
     */
    fun HasSSE(): Boolean {
        if (true) {
            return true
        }
        throw TODO_Exception()
        //	unsigned regs[4];
//
//	// get CPU feature bits
//	CPUID( 1, regs );
//
//	// bit 25 of EDX denotes SSE existence
//	if ( regs[_REG_EDX] & ( 1 << 25 ) ) {
//		return true;
//	}
//	return false;
    }

    /*
     ================
     HasSSE2
     ================
     */
    fun HasSSE2(): Boolean {
        if (true) {
            return true
        }
        throw TODO_Exception()
        //	unsigned regs[4];
//
//	// get CPU feature bits
//	CPUID( 1, regs );
//
//	// bit 26 of EDX denotes SSE2 existence
//	if ( regs[_REG_EDX] & ( 1 << 26 ) ) {
//		return true;
//	}
//	return false;
    }

    /*
     ================
     HasSSE3
     ================
     */
    fun HasSSE3(): Boolean {
        if (true) {
            return true
        }
        throw TODO_Exception()
        //	unsigned regs[4];
//
//	// get CPU feature bits
//	CPUID( 1, regs );
//
//	// bit 0 of ECX denotes SSE3 existence
//	if ( regs[_REG_ECX] & ( 1 << 0 ) ) {
//		return true;
//	}
//	return false;
    }

    /*
     ================
     LogicalProcPerPhysicalProc
     ================
     */
    fun LogicalProcPerPhysicalProc(): Char {
        throw TODO_Exception()
        //	unsigned int regebx = 0;
//	__asm {
//		mov eax, 1
//		cpuid
//		mov regebx, ebx
//	}
//	return (unsigned char) ((regebx & NUM_LOGICAL_BITS) >> 16);
    }

    /*
     ================
     GetAPIC_ID
     ================
     */
    fun GetAPIC_ID(): Char {
        throw TODO_Exception()
        //	unsigned int regebx = 0;
//	__asm {
//		mov eax, 1
//		cpuid
//		mov regebx, ebx
//	}
//	return (unsigned char) ((regebx & INITIAL_APIC_ID_BITS) >> 24);
    }

    /*
     ================
     CPUCount

     logicalNum is the number of logical CPU per physical CPU
     physicalNum is the total number of physical processor
     returns one of the HT_* flags
     ================
     */
    fun CPUCount(logicalNum: CInt, physicalNum: CInt): Int {
        throw TODO_Exception()
        //	int statusFlag;
//	SYSTEM_INFO info;
//
//	physicalNum = 1;
//	logicalNum = 1;
//	statusFlag = HT_NOT_CAPABLE;
//
//	info.dwNumberOfProcessors = 0;
//	GetSystemInfo (&info);
//
//	// Number of physical processors in a non-Intel system
//	// or in a 32-bit Intel system with Hyper-Threading technology disabled
//	physicalNum = info.dwNumberOfProcessors;
//
//	unsigned char HT_Enabled = 0;
//
//	logicalNum = LogicalProcPerPhysicalProc();
//
//	if ( logicalNum >= 1 ) {	// > 1 doesn't mean HT is enabled in the BIOS
//		HANDLE hCurrentProcessHandle;
//		DWORD  dwProcessAffinity;
//		DWORD  dwSystemAffinity;
//		DWORD  dwAffinityMask;
//
//		// Calculate the appropriate  shifts and mask based on the
//		// number of logical processors.
//
//		unsigned char i = 1, PHY_ID_MASK  = 0xFF, PHY_ID_SHIFT = 0;
//
//		while( i < logicalNum ) {
//			i *= 2;
// 			PHY_ID_MASK  <<= 1;
//			PHY_ID_SHIFT++;
//		}
//
//		hCurrentProcessHandle = GetCurrentProcess();
//		GetProcessAffinityMask( hCurrentProcessHandle, &dwProcessAffinity, &dwSystemAffinity );
//
//		// Check if available process affinity mask is equal to the
//		// available system affinity mask
//		if ( dwProcessAffinity != dwSystemAffinity ) {
//			statusFlag = HT_CANNOT_DETECT;
//			physicalNum = -1;
//			return statusFlag;
//		}
//
//		dwAffinityMask = 1;
//		while ( dwAffinityMask != 0 && dwAffinityMask <= dwProcessAffinity ) {
//			// Check if this CPU is available
//			if ( dwAffinityMask & dwProcessAffinity ) {
//				if ( SetProcessAffinityMask( hCurrentProcessHandle, dwAffinityMask ) ) {
//					unsigned char APIC_ID, LOG_ID, PHY_ID;
//
//					Sleep( 0 ); // Give OS time to switch CPU
//
//					APIC_ID = GetAPIC_ID();
//					LOG_ID  = APIC_ID & ~PHY_ID_MASK;
//					PHY_ID  = APIC_ID >> PHY_ID_SHIFT;
//
//					if ( LOG_ID != 0 ) {
//						HT_Enabled = 1;
//					}
//				}
//			}
//			dwAffinityMask = dwAffinityMask << 1;
//		}
//
//		// Reset the processor affinity
//		SetProcessAffinityMask( hCurrentProcessHandle, dwProcessAffinity );
//
//		if ( logicalNum == 1 ) {  // Normal P4 : HT is disabled in hardware
//			statusFlag = HT_DISABLED;
//		} else {
//			if ( HT_Enabled ) {
//				// Total physical processors in a Hyper-Threading enabled system.
//				physicalNum /= logicalNum;
//				statusFlag = HT_ENABLED;
//			} else {
//				statusFlag = HT_SUPPORTED_NOT_ENABLED;
//			}
//		}
//	}
//	return statusFlag;
    }

    var fpuState: ByteArray = ByteArray(128)
    var statePtr = fpuState

    /*
     ================
     HasHTT
     ================
     */
    fun HasHTT(): Boolean {
        if (true) {
            return true
        }
        throw TODO_Exception()
        //	unsigned regs[4];
//	int logicalNum, physicalNum, HTStatusFlag;
//
//	// get CPU feature bits
//	CPUID( 1, regs );
//
//	// bit 28 of EDX denotes HTT existence
//	if ( !( regs[_REG_EDX] & ( 1 << 28 ) ) ) {
//		return false;
//	}
//
//	HTStatusFlag = CPUCount( logicalNum, physicalNum );
//	if ( HTStatusFlag != HT_ENABLED ) {
//		return false;
//	}
//	return true;
    }

    /*
     ================
     HasHTT
     ================
     */
    fun HasDAZ(): Boolean {
        if (true) {
            return true
        }
        throw TODO_Exception()
        //	__declspec(align(16)) unsigned char FXSaveArea[512];
//	unsigned char *FXArea = FXSaveArea;
//	DWORD dwMask = 0;
//	unsigned regs[4];
//
//	// get CPU feature bits
//	CPUID( 1, regs );
//
//	// bit 24 of EDX denotes support for FXSAVE
//	if ( !( regs[_REG_EDX] & ( 1 << 24 ) ) ) {
//		return false;
//	}
//
//	memset( FXArea, 0, sizeof( FXSaveArea ) );
//
//	__asm {
//		mov		eax, FXArea
//		FXSAVE	[eax]
//	}
//
//	dwMask = *(DWORD *)&FXArea[28];						// Read the MXCSR Mask
//	return ( ( dwMask & ( 1 << 6 ) ) == ( 1 << 6 ) );	// Return if the DAZ bit is set
    }

    /*
     ================
     Sys_GetCPUId
     ================
     */
    fun  /*cpuid_t*/Sys_GetCPUId(): Int {
        var flags: Int
        try {
            // verify we're at least a Pentium or 486 with CPUID support
            if (!HasCPUID()) {
                return sys_public.CPUID_UNSUPPORTED
            }
        } catch (ex: IOException) {
            Logger.getLogger(win_cpu::class.java.name).log(Level.SEVERE, null, ex)
        }

        // check for an AMD
        flags = if (IsAMD()) {
            sys_public.CPUID_AMD
        } else {
            sys_public.CPUID_INTEL
        }

        // check for Multi Media Extensions
        if (HasMMX()) {
            flags = flags or sys_public.CPUID_MMX
        }

        // check for 3DNow!
        if (Has3DNow()) {
            flags = flags or sys_public.CPUID_3DNOW
        }

        // check for Streaming SIMD Extensions
        if (HasSSE()) {
            flags = flags or (sys_public.CPUID_SSE or sys_public.CPUID_FTZ)
        }

        // check for Streaming SIMD Extensions 2
        if (HasSSE2()) {
            flags = flags or sys_public.CPUID_SSE2
        }

        // check for Streaming SIMD Extensions 3 aka Prescott's New Instructions
        if (HasSSE3()) {
            flags = flags or sys_public.CPUID_SSE3
        }

        // check for Hyper-Threading Technology
        if (HasHTT()) {
            flags = flags or sys_public.CPUID_HTT
        }

        // check for Conditional Move (CMOV) and fast floating point comparison (FCOMI) instructions
        if (HasCMOV()) {
            flags = flags or sys_public.CPUID_CMOV
        }

        // check for Denormals-Are-Zero mode
        if (HasDAZ()) {
            flags = flags or sys_public.CPUID_DAZ
        }
        return flags
    }

    /*
     ===============
     Sys_FPU_PrintStateFlags
     ===============
     */
    fun Sys_FPU_PrintStateFlags(
        ptr: String,
        ctrl: Int,
        stat: Int,
        tags: Int,
        inof: Int,
        inse: Int,
        opof: Int,
        opse: Int
    ): Int {
        throw TODO_Exception()
        //	int i, length = 0;
//
//	length += sprintf( ptr+length,	"CTRL = %08x\n"
//									"STAT = %08x\n"
//									"TAGS = %08x\n"
//									"INOF = %08x\n"
//									"INSE = %08x\n"
//									"OPOF = %08x\n"
//									"OPSE = %08x\n"
//									"\n",
//									ctrl, stat, tags, inof, inse, opof, opse );
//
//	length += sprintf( ptr+length, "Control Word:\n" );
//	for ( i = 0; controlWordFlags[i].name[0]; i++ ) {
//		length += sprintf( ptr+length, "  %-30s = %s\n", controlWordFlags[i].name, ( ctrl & ( 1 << controlWordFlags[i].bit ) ) ? "true" : "false" );
//	}
//	length += sprintf( ptr+length, "  %-30s = %s\n", "Precision control", precisionControlField[(ctrl>>8)&3] );
//	length += sprintf( ptr+length, "  %-30s = %s\n", "Rounding control", roundingControlField[(ctrl>>10)&3] );
//
//	length += sprintf( ptr+length, "Status Word:\n" );
//	for ( i = 0; statusWordFlags[i].name[0]; i++ ) {
//		ptr += sprintf( ptr+length, "  %-30s = %s\n", statusWordFlags[i].name, ( stat & ( 1 << statusWordFlags[i].bit ) ) ? "true" : "false" );
//	}
//	length += sprintf( ptr+length, "  %-30s = %d%d%d%d\n", "Condition code", (stat>>8)&1, (stat>>9)&1, (stat>>10)&1, (stat>>14)&1 );
//	length += sprintf( ptr+length, "  %-30s = %d\n", "Top of stack pointer", (stat>>11)&7 );
//
//	return length;
    }

    /*
     ===============
     Sys_FPU_StackIsEmpty
     ===============
     */
    fun Sys_FPU_StackIsEmpty(): Boolean {
        throw TODO_Exception()
        //	__asm {
//		mov			eax, statePtr
//		fnstenv		[eax]
//		mov			eax, [eax+8]
//		xor			eax, 0xFFFFFFFF
//		and			eax, 0x0000FFFF
//		jz			empty
//	}
//	return false;
//empty:
//	return true;
    }

    /*
     ===============
     Sys_FPU_ClearStack
     ===============
     */
    fun Sys_FPU_ClearStack() {
        throw TODO_Exception()
        //	__asm {
//		mov			eax, statePtr
//		fnstenv		[eax]
//		mov			eax, [eax+8]
//		xor			eax, 0xFFFFFFFF
//		mov			edx, (3<<14)
//	emptyStack:
//		mov			ecx, eax
//		and			ecx, edx
//		jz			done
//		fstp		st
//		shr			edx, 2
//		jmp			emptyStack
//	done:
//	}
    }

    /*
     ===============
     Sys_FPU_GetState

     gets the FPU state without changing the state
     ===============
     */
    fun Sys_FPU_GetState(): String {
        throw TODO_Exception()
        //	double fpuStack[8] = { 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f };
//	double *fpuStackPtr = fpuStack;
//	int i, numValues;
//	char *ptr;
//
//	__asm {
//		mov			esi, statePtr
//		mov			edi, fpuStackPtr
//		fnstenv		[esi]
//		mov			esi, [esi+8]
//		xor			esi, 0xFFFFFFFF
//		mov			edx, (3<<14)
//		xor			eax, eax
//		mov			ecx, esi
//		and			ecx, edx
//		jz			done
//		fst			qword ptr [edi+0]
//		inc			eax
//		shr			edx, 2
//		mov			ecx, esi
//		and			ecx, edx
//		jz			done
//		fxch		st(1)
//		fst			qword ptr [edi+8]
//		inc			eax
//		fxch		st(1)
//		shr			edx, 2
//		mov			ecx, esi
//		and			ecx, edx
//		jz			done
//		fxch		st(2)
//		fst			qword ptr [edi+16]
//		inc			eax
//		fxch		st(2)
//		shr			edx, 2
//		mov			ecx, esi
//		and			ecx, edx
//		jz			done
//		fxch		st(3)
//		fst			qword ptr [edi+24]
//		inc			eax
//		fxch		st(3)
//		shr			edx, 2
//		mov			ecx, esi
//		and			ecx, edx
//		jz			done
//		fxch		st(4)
//		fst			qword ptr [edi+32]
//		inc			eax
//		fxch		st(4)
//		shr			edx, 2
//		mov			ecx, esi
//		and			ecx, edx
//		jz			done
//		fxch		st(5)
//		fst			qword ptr [edi+40]
//		inc			eax
//		fxch		st(5)
//		shr			edx, 2
//		mov			ecx, esi
//		and			ecx, edx
//		jz			done
//		fxch		st(6)
//		fst			qword ptr [edi+48]
//		inc			eax
//		fxch		st(6)
//		shr			edx, 2
//		mov			ecx, esi
//		and			ecx, edx
//		jz			done
//		fxch		st(7)
//		fst			qword ptr [edi+56]
//		inc			eax
//		fxch		st(7)
//	done:
//		mov			numValues, eax
//	}
//
//	int ctrl = *(int *)&fpuState[0];
//	int stat = *(int *)&fpuState[4];
//	int tags = *(int *)&fpuState[8];
//	int inof = *(int *)&fpuState[12];
//	int inse = *(int *)&fpuState[16];
//	int opof = *(int *)&fpuState[20];
//	int opse = *(int *)&fpuState[24];
//
//	ptr = fpuString;
//	ptr += sprintf( ptr,"FPU State:\n"
//						"num values on stack = %d\n", numValues );
//	for ( i = 0; i < 8; i++ ) {
//		ptr += sprintf( ptr, "ST%d = %1.10e\n", i, fpuStack[i] );
//	}
//
//	Sys_FPU_PrintStateFlags( ptr, ctrl, stat, tags, inof, inse, opof, opse );
//
//	return fpuString;
    }

    /*
     ===============
     Sys_FPU_EnableExceptions
     ===============
     */
    fun Sys_FPU_EnableExceptions(exceptions: Int) {
        throw TODO_Exception()
        //	__asm {
//		mov			eax, statePtr
//		mov			ecx, exceptions
//		and			cx, 63
//		not			cx
//		fnstcw		word ptr [eax]
//		mov			bx, word ptr [eax]
//		or			bx, 63
//		and			bx, cx
//		mov			word ptr [eax], bx
//		fldcw		word ptr [eax]
//	}
    }

    /*
     ===============
     Sys_FPU_SetPrecision
     ===============
     */
    fun Sys_FPU_SetPrecision(precision: Int) {
        throw TODO_Exception()
        //	short precisionBitTable[4] = { 0, 1, 3, 0 };
//	short precisionBits = precisionBitTable[precision & 3] << 8;
//	short precisionMask = ~( ( 1 << 9 ) | ( 1 << 8 ) );
//
//	__asm {
//		mov			eax, statePtr
//		mov			cx, precisionBits
//		fnstcw		word ptr [eax]
//		mov			bx, word ptr [eax]
//		and			bx, precisionMask
//		or			bx, cx
//		mov			word ptr [eax], bx
//		fldcw		word ptr [eax]
//	}
    }

    /*
     ================
     Sys_FPU_SetRounding
     ================
     */
    fun Sys_FPU_SetRounding(rounding: Int) {
        throw TODO_Exception()
        //	short roundingBitTable[4] = { 0, 1, 2, 3 };
//	short roundingBits = roundingBitTable[rounding & 3] << 10;
//	short roundingMask = ~( ( 1 << 11 ) | ( 1 << 10 ) );
//
//	__asm {
//		mov			eax, statePtr
//		mov			cx, roundingBits
//		fnstcw		word ptr [eax]
//		mov			bx, word ptr [eax]
//		and			bx, roundingMask
//		or			bx, cx
//		mov			word ptr [eax], bx
//		fldcw		word ptr [eax]
//	}
    }

    /*
     ================
     Sys_FPU_SetDAZ
     ================
     */
    fun Sys_FPU_SetDAZ(enable: Boolean) {
        throw TODO_Exception()
        //	DWORD dwData;
//
//	_asm {
//		movzx	ecx, byte ptr enable
//		and		ecx, 1
//		shl		ecx, 6
//		STMXCSR	dword ptr dwData
//		mov		eax, dwData
//		and		eax, ~(1<<6)	// clear DAX bit
//		or		eax, ecx		// set the DAZ bit
//		mov		dwData, eax
//		LDMXCSR	dword ptr dwData
//	}
    }

    /*
     ================
     Sys_FPU_SetFTZ
     ================
     */
    fun Sys_FPU_SetFTZ(enable: Boolean) {
        throw TODO_Exception()
        //	DWORD dwData;
//
//	_asm {
//		movzx	ecx, byte ptr enable
//		and		ecx, 1
//		shl		ecx, 15
//		STMXCSR	dword ptr dwData
//		mov		eax, dwData
//		and		eax, ~(1<<15)	// clear FTZ bit
//		or		eax, ecx		// set the FTZ bit
//		mov		dwData, eax
//		LDMXCSR	dword ptr dwData
//	}
    }

    @Throws(IOException::class)
    fun wmic(query: String): String {
        return ""
        //        Process wmic = Runtime.getRuntime().exec("wmic " + query);
////                wmic.waitFor();
//        try (BufferedReader reader = new BufferedReader(new InputStreamReader(wmic.getInputStream()))) {
//            if (reader.readLine().startsWith("Node")) {
//                return "";// invalid query
//            }
//
//            reader.readLine();// empty line
//            result = reader.readLine();
//        }
//        wmic.destroy();
//
//        return result;
    }

    @Throws(IOException::class)
    fun cmd(query: String): String {
        val result: String
        val wmic = Runtime.getRuntime().exec(query)
        BufferedReader(InputStreamReader(wmic.inputStream)).use { reader -> result = reader.readLine() }
        wmic.destroy()
        return result
    }

    /*
     ===============================================================================

     FPU

     ===============================================================================
     */
    class bitFlag_s(var name: String, var bit: Int)
}
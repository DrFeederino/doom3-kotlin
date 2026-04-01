package neo.Game.Script

import neo.Game.Game.SCRIPT_DEFAULTDEFS
import neo.Game.GameSys.Event.idEventDef.Companion.FindEvent
import neo.Game.Game_local
import neo.Game.Game_local.idGameLocal.Companion.Error
import neo.Game.Script.Script_Program.eval_s
import neo.Game.Script.Script_Program.function_t
import neo.Game.Script.Script_Program.idCompileError
import neo.Game.Script.Script_Program.idTypeDef
import neo.Game.Script.Script_Program.idVarDef
import neo.Game.Script.Script_Program.idVarDef.initialized_t
import neo.Game.Script.Script_Program.statement_s
import neo.TempDump.btoi
import neo.TempDump.itob
import neo.idlib.Text.Lexer.LEXFL_ALLOWMULTICHARLITERALS
import neo.idlib.Text.Lexer.LEXFL_NOERRORS
import neo.idlib.Text.Lexer.P_PRECOMP
import neo.idlib.Text.Lexer.idLexer
import neo.idlib.Text.Parser.idParser
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Str.idStr.Companion.Cmp
import neo.idlib.Text.Str.idStr.Companion.Icmp
import neo.idlib.Text.Token.TT_LITERAL
import neo.idlib.Text.Token.TT_NAME
import neo.idlib.Text.Token.TT_NUMBER
import neo.idlib.Text.Token.TT_PUNCTUATION
import neo.idlib.Text.Token.TT_STRING
import neo.idlib.Text.Token.idToken
import neo.idlib.Timer.idTimer
import neo.idlib.math.idVec3

const val D_EVENT_ENTITY = 'e'
const val D_EVENT_ENTITY_NULL = 'E' // event can handle NULL entity pointers
const val D_EVENT_FLOAT = 'f'
const val D_EVENT_INTEGER = 'd'
const val D_EVENT_MAXARGS =
    8 // if changed, enable the CREATE_EVENT_CODE define in Event.cpp to generate switch statement for idClass::ProcessEventArgPtr.
const val D_EVENT_STRING = 's'
const val D_EVENT_TRACE = 't'
const val D_EVENT_VECTOR = 'v'

// running the game will then generate c:\doom\base\events.txt, the contents of which should be copied into the switch statement.
const val D_EVENT_VOID = 0.toChar()
const val FUNCTION_PRIORITY = 2
const val INT_PRIORITY = 2
const val NOT_PRIORITY = 5
const val NUM_OPCODES = 123
const val OP_ADDRESS = 45
const val OP_ADD_F = 12
const val OP_ADD_FS = 15
const val OP_ADD_S = 14
const val OP_ADD_SF = 16
const val OP_ADD_SV = 18
const val OP_ADD_V = 13
const val OP_ADD_VS = 17
const val OP_AND = 111
const val OP_AND_BOOLBOOL = 114
const val OP_AND_BOOLF = 112
const val OP_AND_FBOOL = 113
const val OP_BITAND = 119
const val OP_BITOR = 120
const val OP_BREAK = 121 // placeholder op.  not used in final code
const val OP_CALL = 96
const val OP_COMP_F = 5
const val OP_CONTINUE = 122 // placeholder op.  not used in final code
const val OP_DIV_F = 10
const val OP_EQ_E = 24
const val OP_EQ_EO = 25
const val OP_EQ_F = 21
const val OP_EQ_OE = 26
const val OP_EQ_OO = 27
const val OP_EQ_S = 23
const val OP_EQ_V = 22
const val OP_EVENTCALL = 46
const val OP_GE = 36
const val OP_GOTO = 110
const val OP_GT = 38
const val OP_IF = 94
const val OP_IFNOT = 95
const val OP_INDIRECT_BOOL = 43
const val OP_INDIRECT_ENT = 42
const val OP_INDIRECT_F = 39
const val OP_INDIRECT_OBJ = 44
const val OP_INDIRECT_S = 41
const val OP_INDIRECT_V = 40
const val OP_INT_F = 93
const val OP_LE = 35
const val OP_LT = 37
const val OP_MOD_F = 11
const val OP_MUL_F = 6
const val OP_MUL_FV = 8
const val OP_MUL_V = 7
const val OP_MUL_VF = 9
const val OP_NEG_F = 91
const val OP_NEG_V = 92
const val OP_NE_E = 31
const val OP_NE_EO = 32
const val OP_NE_F = 28
const val OP_NE_OE = 33
const val OP_NE_OO = 34
const val OP_NE_S = 30
const val OP_NE_V = 29
const val OP_NOT_BOOL = 86
const val OP_NOT_ENT = 90
const val OP_NOT_F = 87
const val OP_NOT_S = 89
const val OP_NOT_V = 88
const val OP_OBJECTCALL = 47
const val OP_OBJTHREAD = 98
const val OP_OR = 115
const val OP_OR_BOOLBOOL = 118
const val OP_OR_BOOLF = 116
const val OP_OR_FBOOL = 117
const val OP_PUSH_BTOF = 106
const val OP_PUSH_BTOS = 109
const val OP_PUSH_ENT = 102

//
const val OP_PUSH_F = 99
const val OP_PUSH_FTOB = 107
const val OP_PUSH_FTOS = 105
const val OP_PUSH_OBJ = 103
const val OP_PUSH_OBJENT = 104
const val OP_PUSH_S = 101
const val OP_PUSH_V = 100
const val OP_PUSH_VTOS = 108

// These opcodes are no longer necessary:
// OP_PUSH_OBJ:
// OP_PUSH_OBJENT:
//enum {
const val OP_RETURN = 0
const val OP_STOREP_BOOL = 67
const val OP_STOREP_BOOLTOF = 74
const val OP_STOREP_BTOS = 71
const val OP_STOREP_ENT = 65

//
const val OP_STOREP_F = 62
const val OP_STOREP_FLD = 66
const val OP_STOREP_FTOBOOL = 73

//
const val OP_STOREP_FTOS = 70
const val OP_STOREP_OBJ = 68
const val OP_STOREP_OBJENT = 69
const val OP_STOREP_S = 64
const val OP_STOREP_V = 63
const val OP_STOREP_VTOS = 72
const val OP_STORE_BOOL = 53
const val OP_STORE_BOOLTOF = 61
const val OP_STORE_BTOS = 58
const val OP_STORE_ENT = 52
const val OP_STORE_ENTOBJ = 56

//
const val OP_STORE_F = 49
const val OP_STORE_FTOBOOL = 60

//
const val OP_STORE_FTOS = 57
const val OP_STORE_OBJ = 55
const val OP_STORE_OBJENT = 54
const val OP_STORE_S = 51
const val OP_STORE_V = 50
const val OP_STORE_VTOS = 59
const val OP_SUB_F = 19
const val OP_SUB_V = 20
const val OP_SYSCALL = 48
const val OP_THREAD = 97
const val OP_UADD_F = 80
const val OP_UADD_V = 81
const val OP_UAND_F = 84
const val OP_UDECP_F = 4
const val OP_UDEC_F = 3
const val OP_UDIV_F = 77
const val OP_UDIV_V = 78
const val OP_UINCP_F = 2

//
const val OP_UINC_F = 1
const val OP_UMOD_F = 79

//
const val OP_UMUL_F = 75
const val OP_UMUL_V = 76
const val OP_UOR_F = 85
const val OP_USUB_F = 82
const val OP_USUB_V = 83
const val RESULT_STRING = "<RESULT>"
const val TILDE_PRIORITY = 5
const val TOP_PRIORITY = 7

internal class opcode_s(
    var name: String?,
    var opname: String,
    var priority: Int,
    var rightAssociative: Boolean,
    var type_a: idVarDef?,
    var type_b: idVarDef?,
    var type_c: idVarDef?
) {
    override fun toString(): String {
        return "opcode_s{" +
                "name='" + name + '\'' +
                ", opname='" + opname + '\'' +
                ", priority=" + priority +
                ", rightAssociative=" + rightAssociative +
                ", type_a=" + type_a +
                ", type_b=" + type_b +
                ", type_c=" + type_c +
                '}'
    }
}

internal class idCompiler {
    private val parser = idParser()
    private var basetype // for accessing fields
            : idVarDef?
    private var braceDepth: Int
    private var callthread: Boolean
    private var console: Boolean
    private var currentFileNumber: Int
    private var currentLineNumber: Int
    private var eof: Boolean
    private val errorCount: Int
    private val immediate: eval_s = eval_s()
    private var immediateType: idTypeDef?
    private var loopDepth: Int
    private var parserPtr: idParser
    private var scope // the function being parsed, or NULL
            : idVarDef?
    private val token = idToken()

    init {
        var id: Int
        assert(opcodes.size == NUM_OPCODES + 1)
        eof = true
        parserPtr = parser
        callthread = false
        loopDepth = 0
        eof = false
        braceDepth = 0
        immediateType = null
        basetype = null
        currentLineNumber = 0
        currentFileNumber = 0
        errorCount = 0
        console = false
        scope = Script_Program.def_namespace

        punctuationValid = BooleanArray(punctuationValid.size)
        var ptr = 0
        while (punctuation[ptr] != null) {
            id = parserPtr.GetPunctuationId(punctuation[ptr]!!)
            if (id >= 0 && id < 256) {
                punctuationValid[id] = true
            }
            ptr++
        }
    }

    private fun Divide(numerator: Float, denominator: Float): Float {
        if (denominator == 0.0f) {
            Error("Divide by zero")
            return 0.0f
        }
        return numerator / denominator
    }

    /*
         ============
         idCompiler::Error

         Aborts the current file load
         ============
         */
    private fun Error(fmt: String, vararg args: Any) { //const id_attribute((format(printf,2,3)));

//            va_list argptr;
//            char[] string = new char[1024];
//
//            va_start(argptr, message);
//            vsprintf(string, message, argptr);
//            va_end(argptr);
//
        throw idCompileError(String.format(fmt, *args))
    }

    /*
         ============
         idCompiler::Warning

         Prints a warning about the current line
         ============
         */
    private fun Warning(fmt: String, vararg args: Any) { // const id_attribute((format(printf,2,3)));

//            va_list argptr;
//            char[] string = new char[1024];
//
//            va_start(argptr, message);
//            vsprintf(string, message, argptr);
//            va_end(argptr);
//
        parserPtr.Warning("%s", String.format(fmt, *args))
    }

    /*
         ============
         idCompiler::OptimizeOpcode

         try to optimize when the operator works on constants only
         ============
         */
    private fun OptimizeOpcode(op: opcode_s?, var_a: idVarDef?, var_b: idVarDef?): idVarDef? {
        val c = eval_s()
        val type: idTypeDef?

        if (var_a != null && var_a.initialized != initialized_t.initializedConstant) {
            return null
        }
        if (var_b != null && var_b.initialized != initialized_t.initializedConstant) {
            return null
        }
        val vec_c = idVec3() //*reinterpret_cast<idVec3 *>( &c.vector[ 0 ] );

        when (indexOf(op, opcodes)) {
            OP_ADD_F -> {
                c._float = var_a!!.value!!.floatPtr + var_b!!.value!!.floatPtr
                type = Script_Program.type_float
            }

            OP_ADD_V -> {
                vec_c.set(var_a!!.value!!.getVectorPtrs() + var_b!!.value!!.getVectorPtrs())
                type = Script_Program.type_vector
            }

            OP_SUB_F -> {
                c._float = var_a!!.value!!.floatPtr - var_b!!.value!!.floatPtr
                type = Script_Program.type_float
            }

            OP_SUB_V -> {
                vec_c.set(var_a!!.value!!.getVectorPtrs() - var_b!!.value!!.getVectorPtrs())
                type = Script_Program.type_vector
            }

            OP_MUL_F -> {
                c._float = var_a!!.value!!.floatPtr * var_b!!.value!!.floatPtr
                type = Script_Program.type_float
            }

            OP_MUL_V -> {
                c._float = var_a!!.value!!.vectorPtr * var_b!!.value!!.vectorPtr
                type = Script_Program.type_float
            }

            OP_MUL_FV -> {
                vec_c.set(var_b!!.value!!.getVectorPtrs() * var_a!!.value!!.floatPtr)
                type = Script_Program.type_vector
            }

            OP_MUL_VF -> {
                vec_c.set(var_a!!.value!!.getVectorPtrs() * var_b!!.value!!.floatPtr)
                type = Script_Program.type_vector
            }

            OP_DIV_F -> {
                c._float = Divide(var_a!!.value!!.floatPtr, var_b!!.value!!.floatPtr)
                type = Script_Program.type_float
            }

            OP_MOD_F -> {
                c._float = (var_a!!.value!!.floatPtr.toInt() % var_b!!.value!!.floatPtr.toInt()).toFloat()
                type = Script_Program.type_float
            }

            OP_BITAND -> {
                c._float = (var_a!!.value!!.floatPtr.toInt() and var_b!!.value!!.floatPtr.toInt()).toFloat()
                type = Script_Program.type_float
            }

            OP_BITOR -> {
                c._float = (var_a!!.value!!.floatPtr.toInt() or var_b!!.value!!.floatPtr.toInt()).toFloat()
                type = Script_Program.type_float
            }

            OP_GE -> {
                c._float = btoi(var_a!!.value!!.floatPtr >= var_b!!.value!!.floatPtr).toFloat()
                type = Script_Program.type_float
            }

            OP_LE -> {
                c._float = btoi(var_a!!.value!!.floatPtr <= var_b!!.value!!.floatPtr).toFloat()
                type = Script_Program.type_float
            }

            OP_GT -> {
                c._float = btoi(var_a!!.value!!.floatPtr > var_b!!.value!!.floatPtr).toFloat()
                type = Script_Program.type_float
            }

            OP_LT -> {
                c._float = btoi(var_a!!.value!!.floatPtr < var_b!!.value!!.floatPtr).toFloat()
                type = Script_Program.type_float
            }

            OP_AND -> {
                c._float = btoi(var_a!!.value!!.floatPtr != 0.0f && var_b!!.value!!.floatPtr != 0.0f).toFloat()
                type = Script_Program.type_float
            }

            OP_OR -> {
                c._float = btoi(var_a!!.value!!.floatPtr != 0.0f || var_b!!.value!!.floatPtr != 0.0f).toFloat()
                type = Script_Program.type_float
            }

            OP_NOT_BOOL -> {
                c._int = btoi(!itob(var_a!!.value!!.intPtr))
                type = Script_Program.type_boolean
            }

            OP_NOT_F -> {
                c._float = btoi(!itob(var_a!!.value!!.floatPtr.toInt())).toFloat()
                type = Script_Program.type_float
            }

            OP_NOT_V -> {
                c._float =
                    btoi(0.0f == var_a!!.value!!.vectorPtr.x && 0.0f == var_a.value!!.vectorPtr.y && 0.0f == var_a.value!!.vectorPtr.z).toFloat()
                type = Script_Program.type_float
            }

            OP_NEG_F -> {
                c._float = -var_a!!.value!!.floatPtr
                type = Script_Program.type_float
            }

            OP_NEG_V -> {
                vec_c.set(var_a!!.value!!.vectorPtr.unaryMinus())
                type = Script_Program.type_vector
            }

            OP_INT_F -> {
                c._float = var_a!!.value!!.floatPtr.toInt().toFloat()
                type = Script_Program.type_float
            }

            OP_EQ_F -> {
                c._float = btoi(var_a!!.value!!.floatPtr == var_b!!.value!!.floatPtr).toFloat()
                type = Script_Program.type_float
            }

            OP_EQ_V -> {
                c._float = btoi(var_a!!.value!!.vectorPtr.Compare(var_b!!.value!!.getVectorPtrs())).toFloat()
                type = Script_Program.type_float
            }

            OP_EQ_E -> {
                c._float = btoi(var_a!!.value!!.intPtr == var_b!!.value!!.intPtr).toFloat()
                type = Script_Program.type_float
            }

            OP_NE_F -> {
                c._float = btoi(var_a!!.value!!.floatPtr != var_b!!.value!!.floatPtr).toFloat()
                type = Script_Program.type_float
            }

            OP_NE_V -> {
                c._float = btoi(!var_a!!.value!!.vectorPtr.Compare(var_b!!.value!!.getVectorPtrs())).toFloat()
                type = Script_Program.type_float
            }

            OP_NE_E -> {
                c._float = btoi(var_a!!.value!!.intPtr != var_b!!.value!!.intPtr).toFloat()
                type = Script_Program.type_float
            }

            OP_UADD_F -> {
                c._float = var_b!!.value!!.floatPtr + var_a!!.value!!.floatPtr
                type = Script_Program.type_float
            }

            OP_USUB_F -> {
                c._float = var_b!!.value!!.floatPtr - var_a!!.value!!.floatPtr
                type = Script_Program.type_float
            }

            OP_UMUL_F -> {
                c._float = var_b!!.value!!.floatPtr * var_a!!.value!!.floatPtr
                type = Script_Program.type_float
            }

            OP_UDIV_F -> {
                c._float = Divide(var_b!!.value!!.floatPtr, var_a!!.value!!.floatPtr)
                type = Script_Program.type_float
            }

            OP_UMOD_F -> {
                c._float = (var_b!!.value!!.floatPtr.toInt() % var_a!!.value!!.floatPtr.toInt()).toFloat()
                type = Script_Program.type_float
            }

            OP_UOR_F -> {
                c._float = (var_b!!.value!!.floatPtr.toInt() or var_a!!.value!!.floatPtr.toInt()).toFloat()
                type = Script_Program.type_float
            }

            OP_UAND_F -> {
                c._float = (var_b!!.value!!.floatPtr.toInt() and var_a!!.value!!.floatPtr.toInt()).toFloat()
                type = Script_Program.type_float
            }

            OP_UINC_F -> {
                c._float = var_a!!.value!!.floatPtr + 1
                type = Script_Program.type_float
            }

            OP_UDEC_F -> {
                c._float = var_a!!.value!!.floatPtr - 1
                type = Script_Program.type_float
            }

            OP_COMP_F -> {
                c._float = var_a!!.value!!.floatPtr.toInt().inv().toFloat()
                type = Script_Program.type_float
            }

            else -> type = null
        }

        if (null == type) {
            return null
        }

        if (var_a != null) {
            var_a.numUsers--
            if (var_a.numUsers <= 0) {
                Game_local.gameLocal.program.FreeDef(var_a, null)
            }
        }

        if (var_b != null) {
            var_b.numUsers--
            if (var_b.numUsers <= 0) {
                Game_local.gameLocal.program.FreeDef(var_b, null)
            }
        }
        return GetImmediate(type, c, "")
    }

    /*
         ============
         idCompiler::EmitOpcode

         Emits a primitive statement, returning the var it places it's value in
         ============
         */
    private fun EmitOpcode(op: opcode_s?, var_a: idVarDef?, var_b: idVarDef?): idVarDef? {
        var var_c: idVarDef?
        var_c = OptimizeOpcode(op, var_a, var_b)
        if (var_c != null) {
            return var_c
        }
        if (var_a != null && var_a.Name() == RESULT_STRING) {
            var_a.numUsers++
        }
        if (var_b != null && var_b.Name() == RESULT_STRING) {
            var_b.numUsers++
        }
        val statement = Game_local.gameLocal.program.AllocStatement()
        statement!!.linenumber = currentLineNumber
        statement.file = currentFileNumber
        if (op!!.type_c === Script_Program.def_void || op.rightAssociative) {
            // ifs, gotos, and assignments don't need vars allocated
            var_c = null
        } else {
            // allocate result space
            // try to reuse result defs as much as possible
            var_c = Game_local.gameLocal.program.FindFreeResultDef(
                op.type_c!!.TypeDef(),
                RESULT_STRING,
                scope,
                var_a,
                var_b
            )
            // set user count back to 1, a result def needs to be used twice before it can be reused
            var_c.numUsers = 1
        }
        statement.op = indexOf(op, opcodes)
        statement.a = var_a
        statement.b = var_b
        statement.c = var_c
        return if (op.rightAssociative) {
            var_a
        } else var_c
    }

    /*
         ============
         idCompiler::EmitOpcode

         Emits a primitive statement, returning the var it places it's value in
         ============
         */
    private fun EmitOpcode(op: Int, var_a: idVarDef?, var_b: idVarDef?): idVarDef? {
        return EmitOpcode(opcodes[op], var_a, var_b)
    }

    private fun indexOf(`val`: opcode_s?, array: Array<opcode_s?>): Int {
        for (i in array.indices) {
            if (array[i] == `val`) {
                return i
            }
        }
        return -1
    }

    /*
         ============
         idCompiler::EmitPush

         Emits an opcode to push the variable onto the stack.
         ============
         */
    private fun EmitPush(expression: idVarDef?, funcArg: idTypeDef?): Boolean {
        var op: opcode_s
        var out: opcode_s?
        var op_ptr: Int
        out = null
        op = opcodes[OP_PUSH_F.also { op_ptr = it }]!!
        while (op.name != null && op.name == "<PUSH>") {
            if (funcArg!!.Type() == op.type_a!!.Type() && expression!!.Type() == op.type_b!!.Type()) {
                out = op
                break
            }
            op = opcodes[++op_ptr]!!
        }
        if (null == out) {
            if (expression!!.TypeDef() != funcArg && !expression.TypeDef()!!.Inherits(funcArg)) {
                return false
            }
            out = opcodes[OP_PUSH_ENT]
        }
        EmitOpcode(out, expression, null)
        return true
    }

    private fun NextToken() {
        var i: Int

        // reset our type
        immediateType = null

        // Save the token's line number and filename since when we emit opcodes the current
        // token is always the next one to be read
        currentLineNumber = token.line
        currentFileNumber = Game_local.gameLocal.program.GetFilenum(parserPtr.GetFileName().toString())
        bla2++
        if (!parserPtr.ReadToken(token)) {
            eof = true
            return
        }
        if (currentFileNumber != Game_local.gameLocal.program.GetFilenum(parserPtr.GetFileName().toString())) {
            if (braceDepth > 0 && !token.equals("}")) {
                // missing a closing brace.  try to give as much info as possible.
                if (scope!!.Type() == Script_Program.ev_function) {
                    Error("Unexpected end of file inside function '%s'.  Missing closing braces.", scope!!.Name())
                } else if (scope!!.Type() == Script_Program.ev_object) {
                    Error("Unexpected end of file inside object '%s'.  Missing closing braces.", scope!!.Name())
                } else if (scope!!.Type() == Script_Program.ev_namespace) {
                    Error("Unexpected end of file inside namespace '%s'.  Missing closing braces.", scope!!.Name())
                } else {
                    Error("Unexpected end of file inside braced section")
                }
            }
        }
        when (token.type) {
            TT_STRING -> {
                // handle quoted strings as a unit
                immediateType = Script_Program.type_string
                return
            }

            TT_LITERAL -> {

                // handle quoted vectors as a unit
                immediateType = Script_Program.type_vector
                val lex =
                    idLexer(token.toString(), token.Length(), parserPtr.GetFileName().toString(), LEXFL_NOERRORS)
                val token2 = idToken()

                i = 0
                while (i < 3) {
                    if (!lex.ReadToken(token2)) {
                        Error("Couldn't read vector. '%s' is not in the form of 'x y z'", token)
                    }
                    if (token2.type == TT_PUNCTUATION && token2.equals("-")) {
                        if (lex.CheckTokenType(TT_NUMBER, 0, token2) == 0) {
                            Error("expected a number following '-' but found '%s' in vector '%s'", token2, token)
                        }
                        immediate.vector[i] = -token2.GetFloatValue()
                    } else if (token2.type == TT_NUMBER) {
                        immediate.vector[i] = token2.GetFloatValue()
                    } else {
                        Error(
                            "vector '%s' is not in the form of 'x y z'.  expected float value, found '%s'",
                            token,
                            token2
                        )
                    }
                    i++
                }
                return
            }

            TT_NUMBER -> {
                immediateType = Script_Program.type_float
                immediate._float = token.GetFloatValue()
                return
            }

            TT_PUNCTUATION -> {
                // entity names
                if (token.equals("$")) {
                    immediateType = Script_Program.type_entity
                    parserPtr.ReadToken(token)
                    return
                }
                if (token.equals("{")) {
                    braceDepth++
                    return
                }
                if (token.equals("}")) {
                    braceDepth--
                    return
                }
                if (punctuationValid[token.subtype]) {
                    return
                }
                Error("Unknown punctuation '%s'", token)
            }

            TT_NAME -> return
            else -> Error("Unknown token '%s'", token)
        }
    }

    /*
         ============
         idCompiler::CheckType

         Parses a variable type, including functions types
         ============
         */
    /*
         =============
         idCompiler::ExpectToken

         Issues an Error if the current token isn't equal to string
         Gets the next token
         =============
         */
    private fun ExpectToken(string: String) {
        if (!token.equals(string)) {
            Error("expected '%s', found '%s'", string, token)
        }
        NextToken()
    }

    /*
         =============
         idCompiler::CheckToken

         Returns true and gets the next token if the current token equals string
         Returns false and does nothing otherwise
         =============
         */
    private fun CheckToken(string: String?): Boolean {
        if (token.toString() != string) { //TODO:try to use the idStr::Cmp in the overridden token.equals() method.
            return false
        }
        NextToken()
        return true
    }

    /*
         ============
         idCompiler::ParseName

         Checks to see if the current token is a valid name
         ============
         */
    private fun ParseName(name: idStr) {
        if (token.type != TT_NAME) {
            Error("'%s' is not a name", token)
        }
        name.set(token)
        NextToken()
    }

    /*
         ============
         idCompiler::SkipOutOfFunction

         For error recovery, pops out of nested braces
         ============
         */
    private fun SkipOutOfFunction() {
        while (braceDepth != 0) {
            parserPtr.SkipBracedSection(false)
            braceDepth--
        }
        NextToken()
    }

    /*
         ============
         idCompiler::SkipToSemicolon

         For error recovery
         ============
         */
    private fun SkipToSemicolon() {
        do {
            if (CheckToken(";")) {
                return
            }
            NextToken()
        } while (!eof)
    }

    private fun CheckType(): idTypeDef? {
        var type: idTypeDef?
        if (token.equals("float")) {
            type = Script_Program.type_float
        } else if (token.equals("vector")) {
            type = Script_Program.type_vector
        } else if (token.equals("entity")) {
            type = Script_Program.type_entity
        } else if (token.equals("string")) {
            type = Script_Program.type_string
        } else if (token.equals("void")) {
            type = Script_Program.type_void
        } else if (token.equals("object")) {
            type = Script_Program.type_object
        } else if (token.equals("boolean")) {
            type = Script_Program.type_boolean
        } else if (token.equals("namespace")) {
            type = Script_Program.type_namespace
        } else if (token.equals("scriptEvent")) {
            type = Script_Program.type_scriptevent
        } else {
            type = Game_local.gameLocal.program.FindType(token.toString())
            if (type != null && !type.Inherits(Script_Program.type_object)) {
                type = null
            }
        }
        return type
    }

    /*
         ============
         idCompiler::ParseType

         Parses a variable type, including functions types
         ============
         */
    private fun ParseType(): idTypeDef? {
        val type: idTypeDef? = CheckType()
        if (null == type) {
            Error("\"%s\" is not a type", token.toString())
        }
        if (type == Script_Program.type_scriptevent && scope !== Script_Program.def_namespace) {
            Error("scriptEvents can only defined in the global namespace")
        }
        if (type == Script_Program.type_namespace && scope!!.Type() != Script_Program.ev_namespace) {
            Error("A namespace may only be defined globally, or within another namespace")
        }
        NextToken()
        return type
    }

    /*
         ============
         idCompiler::FindImmediate

         tries to find an existing immediate with the same value
         ============
         */
    private fun FindImmediate(type: idTypeDef?, eval: eval_s?, string: String): idVarDef? {
        var def: idVarDef?
        val   /*ctype_t*/etype: Int = type!!.Type()

        // check for a constant with the same value
        def = Game_local.gameLocal.program.GetDefList("<IMMEDIATE>")
        while (def != null) {
            if (def.TypeDef() != type) {
                def = def.Next()
                continue
            }
            when (etype) {
                Script_Program.ev_field -> if (def.value!!.intPtr == eval!!._int) {
                    return def
                }

                Script_Program.ev_argsize -> if (def.value!!.argSize == eval!!._int) {
                    return def
                }

                Script_Program.ev_jumpoffset -> if (def.value!!.jumpOffset == eval!!._int) {
                    return def
                }

                Script_Program.ev_entity -> if (def.value!!.intPtr == eval!!.entity) {
                    return def
                }

                Script_Program.ev_string -> if (Cmp(def.value!!.stringPtr!!, string) == 0) {
                    return def
                }

                Script_Program.ev_float -> if (def.value!!.floatPtr == eval!!._float) {
                    return def
                }

                Script_Program.ev_virtualfunction -> if (def.value!!.virtualFunction == eval!!._int) {
                    return def
                }

                Script_Program.ev_vector -> {
                    val vectorPtr = idVec3(def.value!!.getVectorPtrs())
                    if (vectorPtr.x == eval!!.vector[0] && vectorPtr.y == eval.vector[1] && vectorPtr.z == eval.vector[2]) {
                        return def
                    }
                }

                else -> Error("weird immediate type")
            }
            def = def.Next()
        }
        return null
    }

    /*
         ============
         idCompiler::GetImmediate

         returns an existing immediate with the same value, or allocates a new one
         ============
         */
    private fun GetImmediate(type: idTypeDef?, eval: eval_s?, string: String): idVarDef {
        var def: idVarDef?
        def = FindImmediate(type, eval, string)
        if (def != null) {
            def.numUsers++
        } else {
            // allocate a new def
            def = Game_local.gameLocal.program.AllocDef(type, "<IMMEDIATE>", Script_Program.def_namespace, true)
            if (type!!.Type() == Script_Program.ev_string) {
                def.SetString(string, true)
            } else {
                def.SetValue(eval, true)
            }
        }
        return def
    }

    /*
         ============
         idCompiler::VirtualFunctionConstant

         Creates a def for an index into a virtual function table
         ============
         */
    private fun VirtualFunctionConstant(func: idVarDef?): idVarDef {
        val eval = eval_s()
        eval._int = func!!.scope!!.TypeDef()!!.GetFunctionNumber(func.value!!.functionPtr)
        if (eval._int < 0) {
            Error("Function '%s' not found in scope '%s'", func.Name(), func.scope!!.Name())
        }
        return GetImmediate(Script_Program.type_virtualfunction, eval, "")
    }

    /*
         ============
         idCompiler::SizeConstant

         Creates a def for a size constant
         ============
         */
    private fun SizeConstant(size: Int): idVarDef {
        val eval = eval_s()

        eval._int = size
        return GetImmediate(Script_Program.type_argsize, eval, "")
    }

    /*
         ============
         idCompiler::JumpConstant

         Creates a def for a jump constant
         ============
         */
    private fun JumpConstant(value: Int): idVarDef {
        val eval = eval_s()
        eval._int = value
        return GetImmediate(Script_Program.type_jumpoffset, eval, "")
    }

    /*
         ============
         idCompiler::JumpDef

         Creates a def for a relative jump from one code location to another
         ============
         */
    private fun JumpDef(jumpfrom: Int, jumpto: Int): idVarDef {
        return JumpConstant(jumpto - jumpfrom)
    }

    /*
         ============
         idCompiler::JumpTo

         Creates a def for a relative jump from current code location
         ============
         */
    private fun JumpTo(jumpto: Int): idVarDef {
        return JumpDef(Game_local.gameLocal.program.NumStatements(), jumpto)
    }

    /*
         ============
         idCompiler::JumpFrom

         Creates a def for a relative jump from code location to current code location
         ============
         */
    private fun JumpFrom(jumpfrom: Int): idVarDef {
        return JumpDef(jumpfrom, Game_local.gameLocal.program.NumStatements())
    }

    /*
         ============
         idCompiler::ParseImmediate

         Looks for a preexisting constant
         ============
         */
    private fun ParseImmediate(): idVarDef {
        blaaaa++
        val def: idVarDef = GetImmediate(immediateType, immediate, token.toString())
        NextToken()
        return def
    }

    private fun EmitFunctionParms(
        op: Int,
        func: idVarDef?,
        startarg: Int,
        startsize: Int,
        `object`: idVarDef?
    ): idVarDef? {
        var e: idVarDef?
        var funcArg: idTypeDef?
        val returnDef: idVarDef?
        val resultOp: Int
        val type = func!!.TypeDef()
        if (func.Type() != Script_Program.ev_function) {
            Error("'%s' is not a function", func.Name())
        }

        // copy the parameters to the global parameter variables
        var arg: Int = startarg
        var size: Int = startsize
        if (!CheckToken(")")) {
            do {
                if (arg >= type!!.NumParameters()) {
                    Error("too many parameters")
                }
                e = GetExpression(TOP_PRIORITY)
                funcArg = type.GetParmType(arg)
                if (!EmitPush(e, funcArg)) {
                    Error("type mismatch on parm %d of call to '%s'", arg + 1, func.Name())
                }
                size += if (funcArg!!.Type() == Script_Program.ev_object) {
                    Script_Program.type_object.Size()
                } else {
                    funcArg.Size()
                }
                arg++
            } while (CheckToken(","))
            ExpectToken(")")
        }
        if (arg < type!!.NumParameters()) {
            Error("too few parameters for function '%s'", func.Name())
        }
        if (op == OP_CALL) {
            EmitOpcode(op, func, null)
        } else if (op == OP_OBJECTCALL || op == OP_OBJTHREAD) {
            EmitOpcode(op, `object`, VirtualFunctionConstant(func))

            // need arg size seperate since script object may be NULL
            val statement =
                Game_local.gameLocal.program.GetStatement(Game_local.gameLocal.program.NumStatements() - 1)
            statement.c = SizeConstant(func.value!!.functionPtr!!.parmTotal)
            // DG: set flag for savegame compat when prototype was parsed but implementation wasn't yet
            if (op == OP_OBJECTCALL && func.value!!.functionPtr!!.parmTotal > 0
                && func.value!!.functionPtr!!.parmSize.Num() == 0
            ) {
                statement.flags = statement_s.FLAG_OBJECTCALL_IMPL_NOT_PARSED_YET
            }
        } else {
            EmitOpcode(op, func, SizeConstant(size))
        }

        // we need to copy off the result into a temporary result location, so figure out the opcode
        val returnType = type.ReturnType()
        if (returnType!!.Type() == Script_Program.ev_string) {
            resultOp = OP_STORE_S
            returnDef = Game_local.gameLocal.program.returnStringDef
        } else {
            Game_local.gameLocal.program.returnDef!!.SetTypeDef(returnType)
            returnDef = Game_local.gameLocal.program.returnDef
            resultOp = when (returnType.Type()) {
                Script_Program.ev_void -> OP_STORE_F
                Script_Program.ev_boolean -> OP_STORE_BOOL
                Script_Program.ev_float -> OP_STORE_F
                Script_Program.ev_vector -> OP_STORE_V
                Script_Program.ev_entity -> OP_STORE_ENT
                Script_Program.ev_object -> OP_STORE_OBJ
                else -> {
                    Error("Invalid return type for function '%s'", func.Name())
                    // shut up compiler
                    OP_STORE_OBJ
                }
            }
        }
        if (returnType.Type() == Script_Program.ev_void) {
            // don't need result space since there's no result, so just return the normal result def.
            return returnDef
        }

        // allocate result space
        // try to reuse result defs as much as possible
        val statement = Game_local.gameLocal.program.GetStatement(Game_local.gameLocal.program.NumStatements() - 1)
        val resultDef = Game_local.gameLocal.program.FindFreeResultDef(
            returnType,
            RESULT_STRING,
            scope,
            statement.a,
            statement.b
        )
        // set user count back to 0, a result def needs to be used twice before it can be reused
        resultDef.numUsers = 0
        EmitOpcode(resultOp, returnDef, resultDef)
        return resultDef
    }

    private fun ParseFunctionCall(funcDef: idVarDef?): idVarDef? {
        assert(funcDef != null)
        if (funcDef!!.Type() != Script_Program.ev_function) {
            Error("'%s' is not a function", funcDef.Name())
        }
        if (funcDef.initialized == initialized_t.uninitialized) {
            Error("Function '%s' has not been defined yet", funcDef.GlobalName())
        }
        assert(funcDef.value!!.functionPtr != null)
        return if (callthread) {
            if (funcDef.initialized != initialized_t.uninitialized && funcDef.value!!.functionPtr!!.eventdef != null) {
                Error("Built-in functions cannot be called as threads")
            }
            callthread = false
            EmitFunctionParms(OP_THREAD, funcDef, 0, 0, null)
        } else {
            if (funcDef.initialized != initialized_t.uninitialized && funcDef.value!!.functionPtr!!.eventdef != null) {
                if (scope!!.Type() != Script_Program.ev_namespace && scope!!.scope!!.Type() == Script_Program.ev_object) {
                    // get the local object pointer
                    val thisdef = Game_local.gameLocal.program.GetDef(scope!!.scope!!.TypeDef(), "self", scope)
                    if (thisdef == null) {
                        Error("No 'self' within scope")
                    }
                    return ParseEventCall(thisdef, funcDef)
                } else {
                    Error("Built-in functions cannot be called without an object")
                }
            }
            EmitFunctionParms(OP_CALL, funcDef, 0, 0, null)
        }
    }

    private fun ParseObjectCall(`object`: idVarDef?, func: idVarDef?): idVarDef? {
        EmitPush(`object`, `object`!!.TypeDef())
        return if (callthread) {
            callthread = false
            EmitFunctionParms(OP_OBJTHREAD, func, 1, Script_Program.type_object.Size(), `object`)
        } else {
            EmitFunctionParms(OP_OBJECTCALL, func, 1, 0, `object`)
        }
    }

    private fun ParseEventCall(`object`: idVarDef?, funcDef: idVarDef?): idVarDef? {
        if (callthread) {
            Error("Cannot call built-in functions as a thread")
        }
        if (funcDef!!.Type() != Script_Program.ev_function) {
            Error("'%s' is not a function", funcDef.Name())
        }
        if (funcDef.value!!.functionPtr!!.eventdef == null) {
            Error("\"%s\" cannot be called with object notation", funcDef.Name())
        }
        if (`object`!!.Type() == Script_Program.ev_object) {
            EmitPush(`object`, Script_Program.type_entity)
        } else {
            EmitPush(`object`, `object`.TypeDef())
        }
        return EmitFunctionParms(OP_EVENTCALL, funcDef, 0, Script_Program.type_object.Size(), null)
    }

    private fun ParseSysObjectCall(funcDef: idVarDef?): idVarDef? {
        if (callthread) {
            Error("Cannot call built-in functions as a thread")
        }
        if (funcDef!!.Type() != Script_Program.ev_function) {
            Error("'%s' is not a function", funcDef.Name())
        }
        if (funcDef.value!!.functionPtr!!.eventdef == null) {
            Error("\"%s\" cannot be called with object notation", funcDef.Name())
        }

//            //TODO:fix this.
//            if (!idThread.Type.RespondsTo(funcDef.value!!.functionPtr.eventdef)) {
//                Error("\"%s\" is not callable as a 'sys' function", funcDef.Name());
//            }
        return EmitFunctionParms(OP_SYSCALL, funcDef, 0, 0, null)
    }

    private fun LookupDef(name: String, baseobj: idVarDef?): idVarDef? {
        var def: idVarDef?
        val field: idVarDef?
        val   /*ctype_t*/type_b: Int
        val   /*ctype_t*/type_c: Int
        var op: opcode_s
        var op_i: Int
        bla++

        // check if we're accessing a field
        if (baseobj != null && baseobj.Type() == Script_Program.ev_object) {
            var tdef: idVarDef?
            def = null
            tdef = baseobj
            while (tdef !== Script_Program.def_object) {
                def = Game_local.gameLocal.program.GetDef(null, name, tdef)
                if (def != null) {
                    break
                }
                tdef = tdef!!.TypeDef()!!.SuperClass()!!.def
            }
        } else {
            // first look through the defs in our scope
            def = Game_local.gameLocal.program.GetDef(null, name, scope)
            if (def == null) {
                // if we're in a member function, check types local to the object
                if (scope!!.Type() != Script_Program.ev_namespace && scope!!.scope!!.Type() == Script_Program.ev_object) {
                    // get the local object pointer
                    val thisdef = Game_local.gameLocal.program.GetDef(scope!!.scope!!.TypeDef(), "self", scope)
                    field = LookupDef(name, scope!!.scope!!.TypeDef()!!.def)
                    if (field == null) {
                        Error("Unknown value \"%s\"", name)
                    }

                    // type check
                    type_b = field!!.Type()
                    if (field.Type() == Script_Program.ev_function) {
                        type_c = field.TypeDef()!!.ReturnType()!!.Type()
                    } else {
                        type_c = field.TypeDef()!!.FieldType()!!.Type() // field access gets type from field
                        if (CheckToken("++")) {
                            if (type_c != Script_Program.ev_float) {
                                Error("Invalid type for ++")
                            }
                            def = EmitOpcode(OP_UINCP_F, thisdef, field)
                            return def
                        } else if (CheckToken("--")) {
                            if (type_c != Script_Program.ev_float) {
                                Error("Invalid type for --")
                            }
                            def = EmitOpcode(OP_UDECP_F, thisdef, field)
                            return def
                        }
                    }
                    op = opcodes[OP_INDIRECT_F.also { op_i = it }]!!
                    while (op.type_a!!.Type() != Script_Program.ev_object || type_b != op.type_b!!.Type() || type_c != op.type_c!!.Type()) {
                        if (op.priority == FUNCTION_PRIORITY && op.type_a!!.Type() == Script_Program.ev_object && op.type_c!!.Type() == Script_Program.ev_void && type_c != op.type_c!!.Type()) {
                            // catches object calls that return a value
                            break
                        }
                        op = opcodes[++op_i]!!
                        if (null == op.name || op.name != ".") {
                            Error("no valid opcode to access type '%s'", field.TypeDef()!!.SuperClass()!!.Name())
                        }
                    }

//				if ( ( op - opcodes ) == OP_OBJECTCALL ) {
                    if (op_i == OP_OBJECTCALL) {
                        ExpectToken("(")
                        def = ParseObjectCall(thisdef, field)
                    } else {
                        // emit the conversion opcode
                        def = EmitOpcode(op, thisdef, field)

                        // field access gets type from field
                        def!!.SetTypeDef(field.TypeDef()!!.FieldType())
                    }
                }
            }
        }
        return def
    }

    private fun ParseValue(): idVarDef? {
        DBG_ParseValue++
        var def: idVarDef?
        var namespaceDef: idVarDef?
        val name = idStr()
        if (immediateType == Script_Program.type_entity) {
            // if an immediate entity ($-prefaced name) then create or lookup a def for it.
            // when entities are spawned, they'll lookup the def and point it to them.
            def = Game_local.gameLocal.program.GetDef(
                Script_Program.type_entity,
                "$$token",
                Script_Program.def_namespace
            )
            if (def == null) {
                def = Game_local.gameLocal.program.AllocDef(
                    Script_Program.type_entity,
                    "$$token",
                    Script_Program.def_namespace,
                    true
                )
            }
            NextToken()
            return def
        } else if (immediateType != null) {
            // if the token is an immediate, allocate a constant for it
            return ParseImmediate()
        }
        ParseName(name)
        def = LookupDef(name.toString(), basetype)
        if (def == null) {
            if (basetype != null) {
                Error("%s is not a member of %s", name, basetype!!.TypeDef()!!.Name())
            } else {
                Error("Unknown value \"%s\"", name)
            }
            // if namespace, then look up the variable in that namespace
        } else if (def!!.Type() == Script_Program.ev_namespace) {
            while (def!!.Type() == Script_Program.ev_namespace) {
                ExpectToken("::")
                ParseName(name)
                namespaceDef = def
                def = Game_local.gameLocal.program.GetDef(null, name.toString(), namespaceDef)
                if (def == null) {
                    Error("Unknown value \"%s::%s\"", namespaceDef.GlobalName(), name)
                }
            }
            //def = LookupDef( name, basetype );
        }
        return def
    }

    private fun GetTerm(): idVarDef? {
        val e: idVarDef?
        val op: Int
        if (immediateType == null && CheckToken("~")) {
            e = GetExpression(TILDE_PRIORITY)
            op = when (e!!.Type()) {
                Script_Program.ev_float -> OP_COMP_F
                else -> {
                    Error("type mismatch for ~")

                    // shut up compiler
                    OP_COMP_F
                }
            }
            return EmitOpcode(op, e, null)
        }
        if (immediateType == null && CheckToken("!")) {
            e = GetExpression(NOT_PRIORITY)
            op = when (e!!.Type()) {
                Script_Program.ev_boolean -> OP_NOT_BOOL
                Script_Program.ev_float -> OP_NOT_F
                Script_Program.ev_string -> OP_NOT_S
                Script_Program.ev_vector -> OP_NOT_V
                Script_Program.ev_entity -> OP_NOT_ENT
                Script_Program.ev_function -> {
                    Error("Invalid type for !")

                    // shut up compiler
                    OP_NOT_F
                }

                Script_Program.ev_object -> OP_NOT_ENT
                else -> {
                    Error("type mismatch for !")

                    // shut up compiler
                    OP_NOT_F
                }
            }
            return EmitOpcode(op, e, null)
        }

        // check for negation operator
        if (immediateType == null && CheckToken("-")) {
            // constants are directly negated without an instruction
            return if (immediateType == Script_Program.type_float) {
                immediate._float = -immediate._float
                ParseImmediate()
            } else if (immediateType == Script_Program.type_vector) {
                immediate.vector[0] = -immediate.vector[0]
                immediate.vector[1] = -immediate.vector[1]
                immediate.vector[2] = -immediate.vector[2]
                ParseImmediate()
            } else {
                e = GetExpression(NOT_PRIORITY)
                op = when (e!!.Type()) {
                    Script_Program.ev_float -> OP_NEG_F
                    Script_Program.ev_vector -> OP_NEG_V
                    else -> {
                        Error("type mismatch for -")

                        // shut up compiler
                        OP_NEG_F
                    }
                }
                EmitOpcode(opcodes[op], e, null)
            }
        }
        if (CheckToken("int")) {
            ExpectToken("(")
            e = GetExpression(INT_PRIORITY)
            if (e!!.Type() != Script_Program.ev_float) {
                Error("type mismatch for int()")
            }
            ExpectToken(")")
            return EmitOpcode(OP_INT_F, e, null)
        }
        if (CheckToken("thread")) {
            callthread = true
            e = GetExpression(FUNCTION_PRIORITY)
            if (callthread) {
                Error("Invalid thread call")
            }

            // threads return the thread number
            Game_local.gameLocal.program.returnDef!!.SetTypeDef(Script_Program.type_float)
            return Game_local.gameLocal.program.returnDef
        }
        if (immediateType == null && CheckToken("(")) {
            e = GetExpression(TOP_PRIORITY)
            ExpectToken(")")
            return e
        }
        return ParseValue()
    }

    private fun TypeMatches(  /*ctype_t*/type1: Int,   /*ctype_t*/type2: Int): Boolean {

        //if ( ( type1 == ev_entity ) && ( type2 == ev_object ) ) {
        //	return true;
        //}
        //if ( ( type2 == ev_entity ) && ( type1 == ev_object ) ) {
        //	return true;
        //}
        return type1 == type2
    }

    private fun GetExpression(priority: Int): idVarDef? {
        DBG_GetExpression++
        var op: opcode_s?
        var oldop: opcode_s
        var e: idVarDef?
        var e2: idVarDef?
        var oldtype: idVarDef?
        var   /*ctype_t*/type_a: Int
        var   /*ctype_t*/type_b: Int
        var   /*ctype_t*/type_c: Int
        var op_i: Int
        if (priority == 0) {
            return GetTerm()
        }
        e = GetExpression(priority - 1)
        if (token.equals(";")) {
            // save us from searching through the opcodes unneccesarily
            return e
        }
        while (true) {
            if (priority == FUNCTION_PRIORITY && CheckToken("(")) {
                return ParseFunctionCall(e)
            }

            // has to be a punctuation
            if (immediateType != null) {
                break
            }
            op = opcodes[0.also { op_i = it }]!!
            while (op_i < opcodes.size && op != null && op.name != null) {
                if (op.priority == priority && CheckToken(op.name)) {
                    break
                }
                op = opcodes[++op_i]
            }
            if (null == op || null == op.name) {
                // next token isn't at this priority level
                break
            }

            // unary operators act only on the left operand
            if (op.type_b === Script_Program.def_void) {
                e = EmitOpcode(op, e, null)
                return e
            }

            // preserve our base type
            oldtype = basetype

            // field access needs scope from object
            if (op.name!![0] == '.' && e!!.TypeDef()!!.Inherits(Script_Program.type_object)) {
                // save off what type this field is part of
                basetype = e.TypeDef()!!.def
            }
            if (op.rightAssociative) {
                // if last statement is an indirect, change it to an address of
                if (Game_local.gameLocal.program.NumStatements() > 0) {
                    val statement =
                        Game_local.gameLocal.program.GetStatement(Game_local.gameLocal.program.NumStatements() - 1)
                    if (statement.op >= OP_INDIRECT_F && statement.op < OP_ADDRESS) {
                        statement.op = OP_ADDRESS
                        Script_Program.type_pointer.SetPointerType(e!!.TypeDef())
                        e.SetTypeDef(Script_Program.type_pointer)
                    }
                }
                e2 = GetExpression(priority)
            } else {
                e2 = GetExpression(priority - 1)
            }

            // restore type
            basetype = oldtype

            // type check
            type_a = e!!.Type()
            type_b = e2!!.Type()

            // field access gets type from field
            type_c = if (op.name!![0] == '.') {
                if (e2.Type() == Script_Program.ev_function && e2.TypeDef()!!.ReturnType() != null) {
                    e2.TypeDef()!!.ReturnType()!!.Type()
                } else if (e2.TypeDef()!!.FieldType() != null) {
                    e2.TypeDef()!!.FieldType()!!.Type()
                } else {
                    // not a field
                    Script_Program.ev_error
                }
            } else {
                Script_Program.ev_void
            }
            oldop = op
            while (!TypeMatches(type_a, op!!.type_a!!.Type()) || !TypeMatches(
                    type_b,
                    op.type_b!!.Type()
                ) || type_c != Script_Program.ev_void && !TypeMatches(type_c, op.type_c!!.Type())
            ) {
                if (op.priority == FUNCTION_PRIORITY &&
                    TypeMatches(type_a, op.type_a!!.Type()) &&
                    TypeMatches(type_b, op.type_b!!.Type())
                ) {
                    break
                }
                op = opcodes[++op_i]!!
                if (null == op.name || op.name != oldop.name) {
                    Error("type mismatch for '%s'", oldop.name)
                }
            }
            when (op_i) {
                OP_SYSCALL -> {
                    ExpectToken("(")
                    e = ParseSysObjectCall(e2)
                }

                OP_OBJECTCALL -> {
                    ExpectToken("(")
                    e =
                        if (e2.initialized != initialized_t.uninitialized && e2.value!!.functionPtr!!.eventdef != null) {
                            ParseEventCall(e, e2)
                        } else {
                            ParseObjectCall(e, e2)
                        }
                }

                OP_EVENTCALL -> {
                    ExpectToken("(")
                    e =
                        if (e2.initialized != initialized_t.uninitialized && e2.value!!.functionPtr!!.eventdef != null) {
                            ParseEventCall(e, e2)
                        } else {
                            ParseObjectCall(e, e2)
                        }
                }

                else -> {
                    if (callthread) {
                        Error("Expecting function call after 'thread'")
                    }
                    if (type_a == Script_Program.ev_pointer && type_b != e.TypeDef()!!.PointerType()!!.Type()) {
                        // FIXME: need to make a general case for this
//				if ( ( op - opcodes == OP_STOREP_F ) && ( e.TypeDef().PointerType().Type() == ev_boolean ) ) {
                        if (op_i == OP_STOREP_F && e.TypeDef()!!.PointerType()!!
                                .Type() == Script_Program.ev_boolean
                        ) {
                            // copy from float to boolean pointer
                            op = opcodes[OP_STOREP_FTOBOOL.also { op_i = it }]!!
                        } else if (op_i == OP_STOREP_BOOL && e.TypeDef()!!.PointerType()!!
                                .Type() == Script_Program.ev_float
                        ) {
                            // copy from boolean to float pointer
                            op = opcodes[OP_STOREP_BOOLTOF.also { op_i = it }]!!
                        } else if (op_i == OP_STOREP_F && e.TypeDef()!!.PointerType()!!
                                .Type() == Script_Program.ev_string
                        ) {
                            // copy from float to string pointer
                            op = opcodes[OP_STOREP_FTOS.also { op_i = it }]!!
                        } else if (op_i == OP_STOREP_BOOL && e.TypeDef()!!.PointerType()!!
                                .Type() == Script_Program.ev_string
                        ) {
                            // copy from boolean to string pointer
                            op = opcodes[OP_STOREP_BTOS.also { op_i = it }]!!
                        } else if (op_i == OP_STOREP_V && e.TypeDef()!!.PointerType()!!
                                .Type() == Script_Program.ev_string
                        ) {
                            // copy from vector to string pointer
                            op = opcodes[OP_STOREP_VTOS.also { op_i = it }]!!
                        } else if (op_i == OP_STOREP_ENT && e.TypeDef()!!.PointerType()!!
                                .Type() == Script_Program.ev_object
                        ) {
                            // store an entity into an object pointer
                            op = opcodes[OP_STOREP_OBJENT.also { op_i = it }]!!
                        } else {
                            Error("type mismatch for '%s'", op.name)
                        }
                    }
                    e = if (op.rightAssociative) {
                        EmitOpcode(op, e2, e)
                    } else {
                        EmitOpcode(op, e, e2)
                    }
                    if (op_i == OP_STOREP_OBJENT) {
                        // statement.b points to type_pointer, which is just a temporary that gets its type reassigned, so we store the real type in statement.c
                        // so that we can do a type check during run time since we don't know what type the script object is at compile time because it
                        // comes from an entity
                        val statement =
                            Game_local.gameLocal.program.GetStatement(Game_local.gameLocal.program.NumStatements() - 1)
                        statement.c = Script_Program.type_pointer.PointerType()!!.def
                    }

                    // field access gets type from field
                    if (type_c != Script_Program.ev_void) {
                        e!!.SetTypeDef(e2.TypeDef()!!.FieldType())
                    }
                }
            }
        }
        return e
    }

    private fun GetTypeForEventArg(argType: Char): idTypeDef? {
        return when (argType) {
            D_EVENT_INTEGER ->  // this will get converted to int by the interpreter
                Script_Program.type_float

            D_EVENT_FLOAT -> Script_Program.type_float
            D_EVENT_VECTOR -> Script_Program.type_vector
            D_EVENT_STRING -> Script_Program.type_string
            D_EVENT_ENTITY, D_EVENT_ENTITY_NULL -> Script_Program.type_entity
            D_EVENT_VOID -> Script_Program.type_void
            D_EVENT_TRACE ->  // This data type isn't available from script
                null

            else ->  // probably a typo
                null
        }
    }

    private fun PatchLoop(start: Int, continuePos: Int) {
        var pos: statement_s?
        pos = Game_local.gameLocal.program.GetStatement(start)
        var i: Int = start
        while (i < Game_local.gameLocal.program.NumStatements()) {
            pos = Game_local.gameLocal.program.GetStatement(i)
            if (pos.op == OP_BREAK) {
                pos.op = OP_GOTO
                pos.a = JumpFrom(i)
            } else if (pos.op == OP_CONTINUE) {
                pos.op = OP_GOTO
                pos.a = JumpDef(i, continuePos)
            }
            i++
        }
    }

    private fun ParseReturnStatement() {
        var op: opcode_s
        var op_i: Int
        if (CheckToken(";")) {
            if (scope!!.TypeDef()!!.ReturnType()!!.Type() != Script_Program.ev_void) {
                Error("expecting return value")
            }
            EmitOpcode(OP_RETURN, null, null)
            return
        }
        val e = GetExpression(TOP_PRIORITY)
        ExpectToken(";")
        val   /*ctype_t*/type_a: Int = e!!.Type()
        val   /*ctype_t*/type_b: Int = scope!!.TypeDef()!!.ReturnType()!!.Type()
        if (TypeMatches(type_a, type_b)) {
            EmitOpcode(OP_RETURN, e, null)
            return
        }
        op = opcodes[0.also { op_i = it }]!!
        while (op.name != null) {
            if (op.name == "=") {
                break
            }
            op = opcodes[++op_i]!!
        }
        assert(op.name != null)
        while (!TypeMatches(type_a, op.type_a!!.Type()) || !TypeMatches(type_b, op.type_b!!.Type())) {
            op = opcodes[++op_i]!!
            if (null == op.name || op.name != "=") {
                Error("type mismatch for return value")
            }
        }
        val returnType = scope!!.TypeDef()!!.ReturnType()
        if (returnType!!.Type() == Script_Program.ev_string) {
            EmitOpcode(op, e, Game_local.gameLocal.program.returnStringDef)
        } else {
            Game_local.gameLocal.program.returnDef!!.SetTypeDef(returnType)
            EmitOpcode(op, e, Game_local.gameLocal.program.returnDef)
        }
        EmitOpcode(OP_RETURN, null, null)
    }

    private fun ParseWhileStatement() {
        val patch1: Int
        loopDepth++
        ExpectToken("(")
        val patch2: Int = Game_local.gameLocal.program.NumStatements()
        val e = GetExpression(TOP_PRIORITY)
        ExpectToken(")")
        if (e!!.initialized == initialized_t.initializedConstant && e.value!!.intPtr != 0) {
            //FIXME: we can completely skip generation of this code in the opposite case
            ParseStatement()
            EmitOpcode(OP_GOTO, JumpTo(patch2), null)
        } else {
            patch1 = Game_local.gameLocal.program.NumStatements()
            EmitOpcode(OP_IFNOT, e, null)
            ParseStatement()
            EmitOpcode(OP_GOTO, JumpTo(patch2), null)
            Game_local.gameLocal.program.GetStatement(patch1).b = JumpFrom(patch1)
        }

        // fixup breaks and continues
        PatchLoop(patch2, patch2)
        loopDepth--
    }

    /*
         ================
         idCompiler::ParseForStatement

         Form of for statement with a counter:

         a = 0;
         start:					<< patch4
         if ( !( a < 10 ) ) {
         goto end;		<< patch1
         } else {
         goto process;	<< patch3
         }

         increment:				<< patch2
         a = a + 1;
         goto start;			<< goto patch4

         process:
         statements;
         goto increment;		<< goto patch2

         end:

         Form of for statement without a counter:

         a = 0;
         start:					<< patch2
         if ( !( a < 10 ) ) {
         goto end;		<< patch1
         }

         process:
         statements;
         goto start;			<< goto patch2

         end:
         ================
         */
    private fun ParseForStatement() {
        var patch2: Int
        val patch3: Int
        val patch4: Int
        loopDepth++
        val start: Int = Game_local.gameLocal.program.NumStatements()
        ExpectToken("(")

        // init
        if (!CheckToken(";")) {
            do {
                GetExpression(TOP_PRIORITY)
            } while (CheckToken(","))
            ExpectToken(";")
        }

        // condition
        patch2 = Game_local.gameLocal.program.NumStatements()
        val e: idVarDef? = GetExpression(TOP_PRIORITY)
        ExpectToken(";")

        //FIXME: add check for constant expression
        val patch1: Int = Game_local.gameLocal.program.NumStatements()
        EmitOpcode(OP_IFNOT, e, null)

        // counter
        if (!CheckToken(")")) {
            patch3 = Game_local.gameLocal.program.NumStatements()
            EmitOpcode(OP_IF, e, null)
            patch4 = patch2
            patch2 = Game_local.gameLocal.program.NumStatements()
            do {
                GetExpression(TOP_PRIORITY)
            } while (CheckToken(","))
            ExpectToken(")")

            // goto patch4
            EmitOpcode(OP_GOTO, JumpTo(patch4), null)

            // fixup patch3
            Game_local.gameLocal.program.GetStatement(patch3).b = JumpFrom(patch3)
        }
        ParseStatement()

        // goto patch2
        EmitOpcode(OP_GOTO, JumpTo(patch2), null)

        // fixup patch1
        Game_local.gameLocal.program.GetStatement(patch1).b = JumpFrom(patch1)

        // fixup breaks and continues
        PatchLoop(start, patch2)
        loopDepth--
    }

    private fun ParseDoWhileStatement() {
        loopDepth++
        val patch1: Int = Game_local.gameLocal.program.NumStatements()
        ParseStatement()
        ExpectToken("while")
        ExpectToken("(")
        val e: idVarDef? = GetExpression(TOP_PRIORITY)
        ExpectToken(")")
        ExpectToken(";")
        EmitOpcode(OP_IF, e, JumpTo(patch1))

        // fixup breaks and continues
        PatchLoop(patch1, patch1)
        loopDepth--
    }

    private fun ParseIfStatement() {
        val patch2: Int
        ExpectToken("(")
        val e: idVarDef? = GetExpression(TOP_PRIORITY)
        ExpectToken(")")

        //FIXME: add check for constant expression
        val patch1: Int = Game_local.gameLocal.program.NumStatements()
        EmitOpcode(OP_IFNOT, e, null)
        ParseStatement()
        if (CheckToken("else")) {
            patch2 = Game_local.gameLocal.program.NumStatements()
            EmitOpcode(OP_GOTO, null, null)
            Game_local.gameLocal.program.GetStatement(patch1).b = JumpFrom(patch1)
            ParseStatement()
            Game_local.gameLocal.program.GetStatement(patch2).a = JumpFrom(patch2)
        } else {
            Game_local.gameLocal.program.GetStatement(patch1).b = JumpFrom(patch1)
        }
    }

    private fun ParseStatement() {
        if (CheckToken(";")) {
            // skip semicolons, which are harmless and ok syntax
            return
        }
        if (CheckToken("{")) {
            do {
                ParseStatement()
            } while (!CheckToken("}"))
            return
        }
        if (CheckToken("return")) {
            ParseReturnStatement()
            return
        }
        if (CheckToken("while")) {
            ParseWhileStatement()
            return
        }
        if (CheckToken("for")) {
            ParseForStatement()
            return
        }
        if (CheckToken("do")) {
            ParseDoWhileStatement()
            return
        }
        if (CheckToken("break")) {
            ExpectToken(";")
            if (0 == loopDepth) {
                Error("cannot break outside of a loop")
            }
            EmitOpcode(OP_BREAK, null, null)
            return
        }
        if (CheckToken("continue")) {
            ExpectToken(";")
            if (0 == loopDepth) {
                Error("cannot contine outside of a loop")
            }
            EmitOpcode(OP_CONTINUE, null, null)
            return
        }
        if (CheckType() != null) {
            ParseDefs()
            return
        }
        if (CheckToken("if")) {
            ParseIfStatement()
            return
        }
        GetExpression(TOP_PRIORITY)
        ExpectToken(";")
    }

    private fun ParseObjectDef(objname: String) {
        val objtype: idTypeDef?
        var type: idTypeDef?
        val parentType: idTypeDef?
        var fieldtype: idTypeDef?
        val name = idStr()
        var fieldname: String
        val newtype = idTypeDef(Script_Program.ev_field, null, "", 0, null)
        val oldscope: idVarDef? = scope
        if (scope!!.Type() != Script_Program.ev_namespace) {
            Error("Objects cannot be defined within functions or other objects")
        }

        // make sure it doesn't exist before we create it
        if (Game_local.gameLocal.program.FindType(objname) != null) {
            Error("'%s' : redefinition; different basic types", objname)
        }

        // base type
        if (!CheckToken(":")) {
            parentType = Script_Program.type_object
        } else {
            parentType = ParseType()
            if (!parentType!!.Inherits(Script_Program.type_object)) {
                Error("Objects may only inherit from objects.")
            }
        }
        objtype = Game_local.gameLocal.program.AllocType(
            Script_Program.ev_object,
            null,
            objname,
            if (parentType == Script_Program.type_object) 0 else parentType.Size(),
            parentType
        )
        objtype.def = Game_local.gameLocal.program.AllocDef(objtype, objname, scope, true)
        scope = objtype.def

        // inherit all the functions
        parentType.NumFunctions()
        var i = 0
        while (i < parentType.NumFunctions()) {
            val func = parentType.GetFunction(i)
            objtype.AddFunction(func)
            i++
        }
        ExpectToken("{")
        do {
            if (CheckToken(";")) {
                // skip semicolons, which are harmless and ok syntax
                continue
            }
            fieldtype = ParseType()
            newtype.SetFieldType(fieldtype)
            fieldname = String.format("%s field", fieldtype!!.Name())
            newtype.SetName(fieldname)
            ParseName(name)

            // check for a function prototype or declaraction
            if (CheckToken("(")) {
                ParseFunctionDef(newtype.FieldType(), name.toString())
            } else {
                type = Game_local.gameLocal.program.GetType(newtype, true)
                assert(type!!.def == null)
                Game_local.gameLocal.program.AllocDef(type, name.toString(), scope, true)
                objtype.AddField(type, name.toString())
                ExpectToken(";")
            }
        } while (!CheckToken("}"))
        scope = oldscope
        ExpectToken(";")
    }

    /*
         ============
         idCompiler::ParseFunction

         parse a function type
         ============
         */
    private fun ParseFunction(returnType: idTypeDef?, name: String): idTypeDef? {
        val newtype =
            idTypeDef(Script_Program.ev_function, null, name, Script_Program.type_function.Size(), returnType)
        var type: idTypeDef?
        if (scope!!.Type() != Script_Program.ev_namespace) {
            // create self pointer
            newtype.AddFunctionParm(scope!!.TypeDef(), "self")
        }
        if (!CheckToken(")")) {
            val parmName = idStr()
            do {
                type = ParseType()
                ParseName(parmName)
                newtype.AddFunctionParm(type, parmName.toString())
            } while (CheckToken(","))
            ExpectToken(")")
        }
        return Game_local.gameLocal.program.GetType(newtype, true)
    }

    private fun ParseFunctionDef(returnType: idTypeDef?, name: String) {
        var def: idVarDef?
        var parm: idVarDef?
        var i: Int
        var parmType: idTypeDef?
        val func: function_t?
        var pos: statement_s?
        if (scope!!.Type() != Script_Program.ev_namespace && !scope!!.TypeDef()!!
                .Inherits(Script_Program.type_object)
        ) {
            Error("Functions may not be defined within other functions")
        }
        val type = ParseFunction(returnType, name)
        def = Game_local.gameLocal.program.GetDef(type, name, scope)
        if (def == null) {
            def = Game_local.gameLocal.program.AllocDef(type, name, scope, true)
            type!!.def = def
            func = Game_local.gameLocal.program.AllocFunction(def)
            if (scope!!.TypeDef()!!.Inherits(Script_Program.type_object)) {
                scope!!.TypeDef()!!.AddFunction(func)
            }
        } else {
            func = def!!.value!!.functionPtr
            assert(func != null)
            if (func!!.firstStatement != 0) {
                Error("%s redeclared", def.GlobalName())
            }
        }

        // check if this is a prototype or declaration
        if (!CheckToken("{")) {
            // it's just a prototype, so get the ; and move on
            ExpectToken(";")

            // DG: calculate parmTotal for prototype so calling this function before
            // the implementation is parsed works without assertions in the interpreter.
            // Don't set parmSize[] yet and don't define the parms yet,
            // otherwise they're defined in a different order than before,
            // so their .num is different which breaks compat with savegames.
            val numParms: Int = type!!.NumParameters()
            func!!.parmTotal = 0
            i = 0
            while (i < numParms) {
                parmType = type.GetParmType(i)
                val size = if (parmType!!.Inherits(Script_Program.type_object)) {
                    Script_Program.type_object.Size()
                } else {
                    parmType.Size()
                }
                func.parmTotal += size
                i++
            }
            return
        }

        // calculate stack space used by parms
        val numParms: Int = type!!.NumParameters()
        var totalSize = 0
        func!!.parmSize.SetNum(numParms)
        i = 0
        while (i < numParms) {
            parmType = type.GetParmType(i)
            if (parmType!!.Inherits(Script_Program.type_object)) {
                func.parmSize[i] = Script_Program.type_object.Size()
            } else {
                func.parmSize[i] = parmType.Size()
            }
            totalSize += func.parmSize[i]
            i++
        }
        // DG: if parmTotal has been calculated before (prototype), it shouldn't have changed
        assert(func.parmTotal == 0 || totalSize == func.parmTotal) {
            "function parameter sizes differ between prototype vs implementation!"
        }
        func.parmTotal = totalSize

        // define the parms
        i = 0
        while (i < numParms) {
            if (Game_local.gameLocal.program.GetDef(type.GetParmType(i), type.GetParmName(i), def) != null) {
                Error("'%s' defined more than once in function parameters", type.GetParmName(i))
            }
            parm = Game_local.gameLocal.program.AllocDef(type.GetParmType(i), type.GetParmName(i), def, false)
            i++
        }
        val oldscope = scope
        scope = def
        func.firstStatement = Game_local.gameLocal.program.NumStatements()

        // check if we should call the super class constructor
        if (oldscope!!.TypeDef()!!.Inherits(Script_Program.type_object) && Icmp(name, "init") == 0) {
            var superClass: idTypeDef?
            var constructorFunc: function_t? = null

            // find the superclass constructor
            superClass = oldscope.TypeDef()!!.SuperClass()
            while (superClass != Script_Program.type_object) {
                constructorFunc =
                    Game_local.gameLocal.program.FindFunction(String.format("%s::init", superClass!!.Name()))
                if (constructorFunc != null) {
                    break
                }
                superClass = superClass.SuperClass()
            }

            // emit the call to the constructor
            if (constructorFunc != null) {
                val selfDef = Game_local.gameLocal.program.GetDef(type.GetParmType(0), type.GetParmName(0), def)!!
                EmitPush(selfDef, selfDef.TypeDef())
                EmitOpcode(opcodes[OP_CALL], constructorFunc.def, null)
            }
        }

        // parse regular statements
        while (!CheckToken("}")) {
            ParseStatement()
        }

        // check if we should call the super class destructor
        if (oldscope.TypeDef()!!.Inherits(Script_Program.type_object) && Icmp(name, "destroy") == 0) {
            var superClass: idTypeDef?
            var destructorFunc: function_t? = null

            // find the superclass destructor
            superClass = oldscope.TypeDef()!!.SuperClass()
            while (superClass != Script_Program.type_object) {
                destructorFunc =
                    Game_local.gameLocal.program.FindFunction(String.format("%s::destroy", superClass!!.Name()))
                if (destructorFunc != null) {
                    break
                }
                superClass = superClass.SuperClass()
            }
            if (destructorFunc != null) {
                if (func.firstStatement < Game_local.gameLocal.program.NumStatements()) {
                    // change all returns to point to the call to the destructor
                    pos = Game_local.gameLocal.program.GetStatement(func.firstStatement)
                    i = func.firstStatement
                    while (i < Game_local.gameLocal.program.NumStatements()) {
                        pos = Game_local.gameLocal.program.GetStatement(i)
                        if (pos.op == OP_RETURN) {
                            pos.op = OP_GOTO
                            pos.a = JumpDef(i, Game_local.gameLocal.program.NumStatements())
                        }
                        i++
                    }
                }

                // emit the call to the destructor
                val selfDef = Game_local.gameLocal.program.GetDef(type.GetParmType(0), type.GetParmName(0), def)!!
                EmitPush(selfDef, selfDef.TypeDef())
                EmitOpcode(opcodes[OP_CALL], destructorFunc.def, null)
            }
        }

// Disabled code since it caused a function to fall through to the next function when last statement is in the form "if ( x ) { return; }"
// #if 0
        // // don't bother adding a return opcode if the "return" statement was used.
        // if ( ( func.firstStatement == gameLocal.program.NumStatements() ) || ( gameLocal.program.GetStatement( gameLocal.program.NumStatements() - 1 ).op != OP_RETURN ) ) {
        // // emit an end of statements opcode
        // EmitOpcode( OP_RETURN, 0, 0 );
        // }
// #else
        // always emit the return opcode
        EmitOpcode(OP_RETURN, null, null)
        // #endif

        // record the number of statements in the function
        func.numStatements = Game_local.gameLocal.program.NumStatements() - func.firstStatement
        scope = oldscope
    }

    private fun ParseVariableDef(type: idTypeDef?, name: String) {
        val def2: idVarDef?
        var negate: Boolean
        var def = Game_local.gameLocal.program.GetDef(type, name, scope)
        if (def != null) {
            Error("%s redeclared", name)
        }
        def = Game_local.gameLocal.program.AllocDef(type, name, scope, false)

        // check for an initialization
        if (CheckToken("=")) {
            // if a local variable in a function then write out interpreter code to initialize variable
            if (scope!!.Type() == Script_Program.ev_function) {
                def2 = GetExpression(TOP_PRIORITY)
                if (type == Script_Program.type_float && def2!!.TypeDef() == Script_Program.type_float) {
                    EmitOpcode(OP_STORE_F, def2, def)
                } else if (type == Script_Program.type_vector && def2!!.TypeDef() == Script_Program.type_vector) {
                    EmitOpcode(OP_STORE_V, def2, def)
                } else if (type == Script_Program.type_string && def2!!.TypeDef() == Script_Program.type_string) {
                    EmitOpcode(OP_STORE_S, def2, def)
                } else if (type == Script_Program.type_entity && (def2!!.TypeDef() == Script_Program.type_entity || def2.TypeDef()!!
                        .Inherits(Script_Program.type_object))
                ) {
                    EmitOpcode(OP_STORE_ENT, def2, def)
                } else if (type!!.Inherits(Script_Program.type_object) && def2!!.TypeDef() == Script_Program.type_entity) {
                    EmitOpcode(OP_STORE_OBJENT, def2, def)
                } else if (type.Inherits(Script_Program.type_object) && def2!!.TypeDef()!!.Inherits(type)) {
                    EmitOpcode(OP_STORE_OBJ, def2, def)
                } else if (type == Script_Program.type_boolean && def2!!.TypeDef() == Script_Program.type_boolean) {
                    EmitOpcode(OP_STORE_BOOL, def2, def)
                } else if (type == Script_Program.type_string && def2!!.TypeDef() == Script_Program.type_float) {
                    EmitOpcode(OP_STORE_FTOS, def2, def)
                } else if (type == Script_Program.type_string && def2!!.TypeDef() == Script_Program.type_boolean) {
                    EmitOpcode(OP_STORE_BTOS, def2, def)
                } else if (type == Script_Program.type_string && def2!!.TypeDef() == Script_Program.type_vector) {
                    EmitOpcode(OP_STORE_VTOS, def2, def)
                } else if (type == Script_Program.type_boolean && def2!!.TypeDef() == Script_Program.type_float) {
                    EmitOpcode(OP_STORE_FTOBOOL, def2, def)
                } else if (type == Script_Program.type_float && def2!!.TypeDef() == Script_Program.type_boolean) {
                    EmitOpcode(OP_STORE_BOOLTOF, def2, def)
                } else {
                    Error("bad initialization for '%s'", name)
                }
            } else {
                // global variables can only be initialized with immediate values
                negate = false
                if (token.type == TT_PUNCTUATION && token.equals("-")) {
                    negate = true
                    NextToken()
                    if (immediateType != Script_Program.type_float) {
                        Error("wrong immediate type for '-' on variable '%s'", name)
                    }
                }
                if (immediateType != type) {
                    Error("wrong immediate type for '%s'", name)
                }

                // global variables are initialized at start up
                if (type == Script_Program.type_string) {
                    def.SetString(token.toString(), false)
                } else {
                    if (negate) {
                        immediate._float = -immediate._float
                    }
                    def.SetValue(immediate, false)
                }
                NextToken()
            }
        } else if (type == Script_Program.type_string) {
            // local strings on the stack are initialized in the interpreter
            if (scope!!.Type() != Script_Program.ev_function) {
                def.SetString("", false)
            }
        } else if (type!!.Inherits(Script_Program.type_object)) {
            if (scope!!.Type() != Script_Program.ev_function) {
                def.SetObject(null)
            }
        }
    }

    private fun ParseEventDef(returnType: idTypeDef?, name: String) {
        var expectedType: idTypeDef?
        var argType: idTypeDef?
        var type: idTypeDef?
        var i: Int
        val num: Int
        val format: String
        val parmName = idStr()
        val ev = FindEvent(name)
        if (null == ev) {
            Error("Unknown event '%s'", name)
        }

        // set the return type
        expectedType = GetTypeForEventArg(ev!!.GetReturnType())
        if (expectedType == null) {
            Error("Invalid return type '%c' in definition of '%s' event.", ev.GetReturnType(), name)
        }
        if (returnType != expectedType) {
            Error("Return type doesn't match internal return type '%s'", expectedType!!.Name())
        }
        val newtype =
            idTypeDef(Script_Program.ev_function, null, name, Script_Program.type_function.Size(), returnType)
        ExpectToken("(")
        format = ev.GetArgFormat()!!
        num = format.length
        i = 0
        while (i < num) {
            expectedType = GetTypeForEventArg(format[i])
            if (null == expectedType || expectedType == Script_Program.type_void) {
                Error("Invalid parameter '%c' in definition of '%s' event.", format[i], name)
            }
            argType = ParseType()
            ParseName(parmName)
            if (argType != expectedType) {
                Error(
                    "The type of parm %d ('%s') does not match the internal type '%s' in definition of '%s' event.",
                    i + 1, parmName, expectedType!!.Name(), name
                )
            }
            newtype.AddFunctionParm(argType, "")
            if (i < num - 1) {
                if (CheckToken(")")) {
                    Error("Too few parameters for event definition.  Internal definition has %d parameters.", num)
                }
                ExpectToken(",")
            }
            i++
        }
        if (!CheckToken(")")) {
            Error("Too many parameters for event definition.  Internal definition has %d parameters.", num)
        }
        ExpectToken(";")
        type = Game_local.gameLocal.program.FindType(name)
        if (type != null) {
            if (!newtype.MatchesType(type) || type.def!!.value!!.functionPtr!!.eventdef !== ev) {
                Error("Type mismatch on redefinition of '%s'", name)
            }
        } else {
            type = Game_local.gameLocal.program.AllocType(newtype)
            type.def = Game_local.gameLocal.program.AllocDef(type, name, Script_Program.def_namespace, true)
            val func = Game_local.gameLocal.program.AllocFunction(type.def)
            func!!.eventdef = ev
            func.parmSize.SetNum(num)
            i = 0
            while (i < num) {
                argType = newtype.GetParmType(i)
                func.parmTotal += argType!!.Size()
                func.parmSize[i] = argType.Size()
                i++
            }

            // mark the parms as local
            func.locals = func.parmTotal
        }
    }

    /*
         ================
         idCompiler::ParseDefs

         Called at the outer layer and when a local statement is hit
         ================
         */
    private fun ParseDefs() {
        val name = idStr()
        var type: idTypeDef?
        var def: idVarDef?
        val oldscope: idVarDef?
        if (CheckToken(";")) {
            // skip semicolons, which are harmless and ok syntax
            return
        }
        type = ParseType()
        if (type == Script_Program.type_scriptevent) {
            type = ParseType()
            ParseName(name)
            ParseEventDef(type, name.toString())
            return
        }
        ParseName(name)
        if (type == Script_Program.type_namespace) {
            def = Game_local.gameLocal.program.GetDef(type, name.toString(), scope)
            if (def == null) {
                def = Game_local.gameLocal.program.AllocDef(type, name.toString(), scope, true)
            }
            ParseNamespace(def)
        } else if (CheckToken("::")) {
            def = Game_local.gameLocal.program.GetDef(null, name.toString(), scope)
            if (def == null) {
                Error("Unknown object name '%s'", name)
            }
            ParseName(name)
            oldscope = scope
            scope = def
            ExpectToken("(")
            ParseFunctionDef(type, name.toString())
            scope = oldscope
        } else if (type == Script_Program.type_object) {
            ParseObjectDef(name.toString())
        } else if (CheckToken("(")) {        // check for a function prototype or declaraction
            ParseFunctionDef(type, name.toString())
        } else {
            ParseVariableDef(type, name.toString())
            while (CheckToken(",")) {
                ParseName(name)
                ParseVariableDef(type, name.toString())
            }
            ExpectToken(";")
        }
    }

    /*
         ================
         idCompiler::ParseNamespace

         Parses anything within a namespace definition
         ================
         */
    private fun ParseNamespace(newScope: idVarDef?) {
        val oldscope: idVarDef? = scope
        if (newScope !== Script_Program.def_namespace) {
            ExpectToken("{")
        }
        while (!eof) {
            scope = newScope
            callthread = false
            if (newScope !== Script_Program.def_namespace && CheckToken("}")) {
                break
            }
            ParseDefs()
        }
        scope = oldscope
    }

    /*
         ============
         idCompiler::CompileFile

         compiles the 0 terminated text, adding definitions to the program structure
         ============
         */
    fun CompileFile(text: String, filename: String?, toConsole: Boolean) {
        val compile_time = idTimer()
        compile_time.Start()
        scope = Script_Program.def_namespace
        basetype = null
        callthread = false
        loopDepth = 0
        eof = false
        braceDepth = 0
        immediateType = null
        currentLineNumber = 0
        console = toConsole

//	memset( &immediate, 0, sizeof( immediate ) );
        parser.SetFlags(LEXFL_ALLOWMULTICHARLITERALS)
        parser.LoadMemory(text, text.length, filename!!)
        parserPtr = parser

        // unread tokens to include script defines
        token.set(SCRIPT_DEFAULTDEFS)
        token.type = TT_STRING
        token.subtype = token.Length()
        token.linesCrossed = 0
        token.line = 0
        parser.UnreadToken(token)
        token.set("include")
        token.type = TT_NAME
        token.subtype = token.Length()
        token.linesCrossed = 0
        token.line = 0
        parser.UnreadToken(token)
        token.set("#")
        token.type = TT_PUNCTUATION
        token.subtype = P_PRECOMP
        token.linesCrossed = 0
        token.line = 0
        parser.UnreadToken(token)

        // init the current token line to be the first line so that currentLineNumber is set correctly in NextToken
        token.line = 1
        val error = false
        try {
            // read first token
            NextToken()
            while (!eof && !error) {
                // parse from global namespace
                ParseNamespace(Script_Program.def_namespace)
            }
        } catch (err: idCompileError) {
            val error2: String = if (console) {
                // don't print line number of an error if were calling script from the console using the "script" command
                String.format("Error: %s\n", err.error)
            } else {
                String.format(
                    "Error: file %s, line %d: %s\n",
                    Game_local.gameLocal.program.GetFilename(currentFileNumber),
                    currentLineNumber,
                    err.error
                )
            }
            parser.FreeSource()
            throw idCompileError(error2)
        }
        parser.FreeSource()
        compile_time.Stop()
        if (!toConsole) {
            Game_local.gameLocal.Printf("Compiled '%s': %d ms\n", filename, compile_time.Milliseconds())
        }
    }

    companion object {
        //
        val opcodes = arrayOf<opcode_s?>(
            opcode_s(
                "<RETURN>",
                "RETURN",
                -1,
                false,
                Script_Program.def_void,
                Script_Program.def_void,
                Script_Program.def_void
            ),  //
            opcode_s(
                "++",
                "UINC_F",
                1,
                true,
                Script_Program.def_float,
                Script_Program.def_void,
                Script_Program.def_void
            ),
            opcode_s(
                "++",
                "UINCP_F",
                1,
                true,
                Script_Program.def_object,
                Script_Program.def_field,
                Script_Program.def_float
            ),
            opcode_s(
                "--",
                "UDEC_F",
                1,
                true,
                Script_Program.def_float,
                Script_Program.def_void,
                Script_Program.def_void
            ),
            opcode_s(
                "--",
                "UDECP_F",
                1,
                true,
                Script_Program.def_object,
                Script_Program.def_field,
                Script_Program.def_float
            ),  //
            opcode_s(
                "~",
                "COMP_F",
                -1,
                false,
                Script_Program.def_float,
                Script_Program.def_void,
                Script_Program.def_float
            ),  //
            opcode_s(
                "*",
                "MUL_F",
                3,
                false,
                Script_Program.def_float,
                Script_Program.def_float,
                Script_Program.def_float
            ),
            opcode_s(
                "*",
                "MUL_V",
                3,
                false,
                Script_Program.def_vector,
                Script_Program.def_vector,
                Script_Program.def_float
            ),
            opcode_s(
                "*",
                "MUL_FV",
                3,
                false,
                Script_Program.def_float,
                Script_Program.def_vector,
                Script_Program.def_vector
            ),
            opcode_s(
                "*",
                "MUL_VF",
                3,
                false,
                Script_Program.def_vector,
                Script_Program.def_float,
                Script_Program.def_vector
            ),  //
            opcode_s(
                "/",
                "DIV",
                3,
                false,
                Script_Program.def_float,
                Script_Program.def_float,
                Script_Program.def_float
            ),
            opcode_s(
                "%",
                "MOD_F",
                3,
                false,
                Script_Program.def_float,
                Script_Program.def_float,
                Script_Program.def_float
            ),  //
            opcode_s(
                "+",
                "ADD_F",
                4,
                false,
                Script_Program.def_float,
                Script_Program.def_float,
                Script_Program.def_float
            ),
            opcode_s(
                "+",
                "ADD_V",
                4,
                false,
                Script_Program.def_vector,
                Script_Program.def_vector,
                Script_Program.def_vector
            ),
            opcode_s(
                "+",
                "ADD_S",
                4,
                false,
                Script_Program.def_string,
                Script_Program.def_string,
                Script_Program.def_string
            ),
            opcode_s(
                "+",
                "ADD_FS",
                4,
                false,
                Script_Program.def_float,
                Script_Program.def_string,
                Script_Program.def_string
            ),
            opcode_s(
                "+",
                "ADD_SF",
                4,
                false,
                Script_Program.def_string,
                Script_Program.def_float,
                Script_Program.def_string
            ),
            opcode_s(
                "+",
                "ADD_VS",
                4,
                false,
                Script_Program.def_vector,
                Script_Program.def_string,
                Script_Program.def_string
            ),
            opcode_s(
                "+",
                "ADD_SV",
                4,
                false,
                Script_Program.def_string,
                Script_Program.def_vector,
                Script_Program.def_string
            ),  //
            opcode_s(
                "-",
                "SUB_F",
                4,
                false,
                Script_Program.def_float,
                Script_Program.def_float,
                Script_Program.def_float
            ),
            opcode_s(
                "-",
                "SUB_V",
                4,
                false,
                Script_Program.def_vector,
                Script_Program.def_vector,
                Script_Program.def_vector
            ),  //
            opcode_s(
                "==",
                "EQ_F",
                5,
                false,
                Script_Program.def_float,
                Script_Program.def_float,
                Script_Program.def_float
            ),
            opcode_s(
                "==",
                "EQ_V",
                5,
                false,
                Script_Program.def_vector,
                Script_Program.def_vector,
                Script_Program.def_float
            ),
            opcode_s(
                "==",
                "EQ_S",
                5,
                false,
                Script_Program.def_string,
                Script_Program.def_string,
                Script_Program.def_float
            ),
            opcode_s(
                "==",
                "EQ_E",
                5,
                false,
                Script_Program.def_entity,
                Script_Program.def_entity,
                Script_Program.def_float
            ),
            opcode_s(
                "==",
                "EQ_EO",
                5,
                false,
                Script_Program.def_entity,
                Script_Program.def_object,
                Script_Program.def_float
            ),
            opcode_s(
                "==",
                "EQ_OE",
                5,
                false,
                Script_Program.def_object,
                Script_Program.def_entity,
                Script_Program.def_float
            ),
            opcode_s(
                "==",
                "EQ_OO",
                5,
                false,
                Script_Program.def_object,
                Script_Program.def_object,
                Script_Program.def_float
            ),  //
            opcode_s(
                "!=",
                "NE_F",
                5,
                false,
                Script_Program.def_float,
                Script_Program.def_float,
                Script_Program.def_float
            ),
            opcode_s(
                "!=",
                "NE_V",
                5,
                false,
                Script_Program.def_vector,
                Script_Program.def_vector,
                Script_Program.def_float
            ),
            opcode_s(
                "!=",
                "NE_S",
                5,
                false,
                Script_Program.def_string,
                Script_Program.def_string,
                Script_Program.def_float
            ),
            opcode_s(
                "!=",
                "NE_E",
                5,
                false,
                Script_Program.def_entity,
                Script_Program.def_entity,
                Script_Program.def_float
            ),
            opcode_s(
                "!=",
                "NE_EO",
                5,
                false,
                Script_Program.def_entity,
                Script_Program.def_object,
                Script_Program.def_float
            ),
            opcode_s(
                "!=",
                "NE_OE",
                5,
                false,
                Script_Program.def_object,
                Script_Program.def_entity,
                Script_Program.def_float
            ),
            opcode_s(
                "!=",
                "NE_OO",
                5,
                false,
                Script_Program.def_object,
                Script_Program.def_object,
                Script_Program.def_float
            ),  //
            opcode_s(
                "<=",
                "LE",
                5,
                false,
                Script_Program.def_float,
                Script_Program.def_float,
                Script_Program.def_float
            ),
            opcode_s(
                ">=",
                "GE",
                5,
                false,
                Script_Program.def_float,
                Script_Program.def_float,
                Script_Program.def_float
            ),
            opcode_s(
                "<",
                "LT",
                5,
                false,
                Script_Program.def_float,
                Script_Program.def_float,
                Script_Program.def_float
            ),
            opcode_s(
                ">",
                "GT",
                5,
                false,
                Script_Program.def_float,
                Script_Program.def_float,
                Script_Program.def_float
            ),  //
            opcode_s(
                ".",
                "INDIRECT_F",
                1,
                false,
                Script_Program.def_object,
                Script_Program.def_field,
                Script_Program.def_float
            ),
            opcode_s(
                ".",
                "INDIRECT_V",
                1,
                false,
                Script_Program.def_object,
                Script_Program.def_field,
                Script_Program.def_vector
            ),
            opcode_s(
                ".",
                "INDIRECT_S",
                1,
                false,
                Script_Program.def_object,
                Script_Program.def_field,
                Script_Program.def_string
            ),
            opcode_s(
                ".",
                "INDIRECT_E",
                1,
                false,
                Script_Program.def_object,
                Script_Program.def_field,
                Script_Program.def_entity
            ),
            opcode_s(
                ".",
                "INDIRECT_BOOL",
                1,
                false,
                Script_Program.def_object,
                Script_Program.def_field,
                Script_Program.def_boolean
            ),
            opcode_s(
                ".",
                "INDIRECT_OBJ",
                1,
                false,
                Script_Program.def_object,
                Script_Program.def_field,
                Script_Program.def_object
            ),  //
            opcode_s(
                ".",
                "ADDRESS",
                1,
                false,
                Script_Program.def_entity,
                Script_Program.def_field,
                Script_Program.def_pointer
            ),  //
            opcode_s(
                ".",
                "EVENTCALL",
                2,
                false,
                Script_Program.def_entity,
                Script_Program.def_function,
                Script_Program.def_void
            ),
            opcode_s(
                ".",
                "OBJECTCALL",
                2,
                false,
                Script_Program.def_object,
                Script_Program.def_function,
                Script_Program.def_void
            ),
            opcode_s(
                ".",
                "SYSCALL",
                2,
                false,
                Script_Program.def_void,
                Script_Program.def_function,
                Script_Program.def_void
            ),  //
            opcode_s(
                "=",
                "STORE_F",
                6,
                true,
                Script_Program.def_float,
                Script_Program.def_float,
                Script_Program.def_float
            ),
            opcode_s(
                "=",
                "STORE_V",
                6,
                true,
                Script_Program.def_vector,
                Script_Program.def_vector,
                Script_Program.def_vector
            ),
            opcode_s(
                "=",
                "STORE_S",
                6,
                true,
                Script_Program.def_string,
                Script_Program.def_string,
                Script_Program.def_string
            ),
            opcode_s(
                "=",
                "STORE_ENT",
                6,
                true,
                Script_Program.def_entity,
                Script_Program.def_entity,
                Script_Program.def_entity
            ),
            opcode_s(
                "=",
                "STORE_BOOL",
                6,
                true,
                Script_Program.def_boolean,
                Script_Program.def_boolean,
                Script_Program.def_boolean
            ),
            opcode_s(
                "=",
                "STORE_OBJENT",
                6,
                true,
                Script_Program.def_object,
                Script_Program.def_entity,
                Script_Program.def_object
            ),
            opcode_s(
                "=",
                "STORE_OBJ",
                6,
                true,
                Script_Program.def_object,
                Script_Program.def_object,
                Script_Program.def_object
            ),
            opcode_s(
                "=",
                "STORE_OBJENT",
                6,
                true,
                Script_Program.def_entity,
                Script_Program.def_object,
                Script_Program.def_object
            ),  //
            opcode_s(
                "=",
                "STORE_FTOS",
                6,
                true,
                Script_Program.def_string,
                Script_Program.def_float,
                Script_Program.def_string
            ),
            opcode_s(
                "=",
                "STORE_BTOS",
                6,
                true,
                Script_Program.def_string,
                Script_Program.def_boolean,
                Script_Program.def_string
            ),
            opcode_s(
                "=",
                "STORE_VTOS",
                6,
                true,
                Script_Program.def_string,
                Script_Program.def_vector,
                Script_Program.def_string
            ),
            opcode_s(
                "=",
                "STORE_FTOBOOL",
                6,
                true,
                Script_Program.def_boolean,
                Script_Program.def_float,
                Script_Program.def_boolean
            ),
            opcode_s(
                "=",
                "STORE_BOOLTOF",
                6,
                true,
                Script_Program.def_float,
                Script_Program.def_boolean,
                Script_Program.def_float
            ),  //
            opcode_s(
                "=",
                "STOREP_F",
                6,
                true,
                Script_Program.def_pointer,
                Script_Program.def_float,
                Script_Program.def_float
            ),
            opcode_s(
                "=",
                "STOREP_V",
                6,
                true,
                Script_Program.def_pointer,
                Script_Program.def_vector,
                Script_Program.def_vector
            ),
            opcode_s(
                "=",
                "STOREP_S",
                6,
                true,
                Script_Program.def_pointer,
                Script_Program.def_string,
                Script_Program.def_string
            ),
            opcode_s(
                "=",
                "STOREP_ENT",
                6,
                true,
                Script_Program.def_pointer,
                Script_Program.def_entity,
                Script_Program.def_entity
            ),
            opcode_s(
                "=",
                "STOREP_FLD",
                6,
                true,
                Script_Program.def_pointer,
                Script_Program.def_field,
                Script_Program.def_field
            ),
            opcode_s(
                "=",
                "STOREP_BOOL",
                6,
                true,
                Script_Program.def_pointer,
                Script_Program.def_boolean,
                Script_Program.def_boolean
            ),
            opcode_s(
                "=",
                "STOREP_OBJ",
                6,
                true,
                Script_Program.def_pointer,
                Script_Program.def_object,
                Script_Program.def_object
            ),
            opcode_s(
                "=",
                "STOREP_OBJENT",
                6,
                true,
                Script_Program.def_pointer,
                Script_Program.def_object,
                Script_Program.def_object
            ),  //
            opcode_s(
                "<=>",
                "STOREP_FTOS",
                6,
                true,
                Script_Program.def_pointer,
                Script_Program.def_float,
                Script_Program.def_string
            ),
            opcode_s(
                "<=>",
                "STOREP_BTOS",
                6,
                true,
                Script_Program.def_pointer,
                Script_Program.def_boolean,
                Script_Program.def_string
            ),
            opcode_s(
                "<=>",
                "STOREP_VTOS",
                6,
                true,
                Script_Program.def_pointer,
                Script_Program.def_vector,
                Script_Program.def_string
            ),
            opcode_s(
                "<=>",
                "STOREP_FTOBOOL",
                6,
                true,
                Script_Program.def_pointer,
                Script_Program.def_float,
                Script_Program.def_boolean
            ),
            opcode_s(
                "<=>",
                "STOREP_BOOLTOF",
                6,
                true,
                Script_Program.def_pointer,
                Script_Program.def_boolean,
                Script_Program.def_float
            ),  //
            opcode_s(
                "*=",
                "UMUL_F",
                6,
                true,
                Script_Program.def_float,
                Script_Program.def_float,
                Script_Program.def_void
            ),
            opcode_s(
                "*=",
                "UMUL_V",
                6,
                true,
                Script_Program.def_vector,
                Script_Program.def_float,
                Script_Program.def_void
            ),
            opcode_s(
                "/=",
                "UDIV_F",
                6,
                true,
                Script_Program.def_float,
                Script_Program.def_float,
                Script_Program.def_void
            ),
            opcode_s(
                "/=",
                "UDIV_V",
                6,
                true,
                Script_Program.def_vector,
                Script_Program.def_float,
                Script_Program.def_void
            ),
            opcode_s(
                "%=",
                "UMOD_F",
                6,
                true,
                Script_Program.def_float,
                Script_Program.def_float,
                Script_Program.def_void
            ),
            opcode_s(
                "+=",
                "UADD_F",
                6,
                true,
                Script_Program.def_float,
                Script_Program.def_float,
                Script_Program.def_void
            ),
            opcode_s(
                "+=",
                "UADD_V",
                6,
                true,
                Script_Program.def_vector,
                Script_Program.def_vector,
                Script_Program.def_void
            ),
            opcode_s(
                "-=",
                "USUB_F",
                6,
                true,
                Script_Program.def_float,
                Script_Program.def_float,
                Script_Program.def_void
            ),
            opcode_s(
                "-=",
                "USUB_V",
                6,
                true,
                Script_Program.def_vector,
                Script_Program.def_vector,
                Script_Program.def_void
            ),
            opcode_s(
                "&=",
                "UAND_F",
                6,
                true,
                Script_Program.def_float,
                Script_Program.def_float,
                Script_Program.def_void
            ),
            opcode_s(
                "|=",
                "UOR_F",
                6,
                true,
                Script_Program.def_float,
                Script_Program.def_float,
                Script_Program.def_void
            ),  //
            opcode_s(
                "!",
                "NOT_BOOL",
                -1,
                false,
                Script_Program.def_boolean,
                Script_Program.def_void,
                Script_Program.def_float
            ),
            opcode_s(
                "!",
                "NOT_F",
                -1,
                false,
                Script_Program.def_float,
                Script_Program.def_void,
                Script_Program.def_float
            ),
            opcode_s(
                "!",
                "NOT_V",
                -1,
                false,
                Script_Program.def_vector,
                Script_Program.def_void,
                Script_Program.def_float
            ),
            opcode_s(
                "!",
                "NOT_S",
                -1,
                false,
                Script_Program.def_vector,
                Script_Program.def_void,
                Script_Program.def_float
            ),
            opcode_s(
                "!",
                "NOT_ENT",
                -1,
                false,
                Script_Program.def_entity,
                Script_Program.def_void,
                Script_Program.def_float
            ),  //
            opcode_s(
                "<NEG_F>",
                "NEG_F",
                -1,
                false,
                Script_Program.def_float,
                Script_Program.def_void,
                Script_Program.def_float
            ),
            opcode_s(
                "<NEG_V>",
                "NEG_V",
                -1,
                false,
                Script_Program.def_vector,
                Script_Program.def_void,
                Script_Program.def_vector
            ),  //
            opcode_s(
                "int",
                "INT_F",
                -1,
                false,
                Script_Program.def_float,
                Script_Program.def_void,
                Script_Program.def_float
            ),  //
            opcode_s(
                "<IF>",
                "IF",
                -1,
                false,
                Script_Program.def_float,
                Script_Program.def_jumpoffset,
                Script_Program.def_void
            ),
            opcode_s(
                "<IFNOT>",
                "IFNOT",
                -1,
                false,
                Script_Program.def_float,
                Script_Program.def_jumpoffset,
                Script_Program.def_void
            ),  //
            // calls returns REG_RETURN
            opcode_s(
                "<CALL>",
                "CALL",
                -1,
                false,
                Script_Program.def_function,
                Script_Program.def_argsize,
                Script_Program.def_void
            ),
            opcode_s(
                "<THREAD>",
                "THREAD",
                -1,
                false,
                Script_Program.def_function,
                Script_Program.def_argsize,
                Script_Program.def_void
            ),
            opcode_s(
                "<THREAD>",
                "OBJTHREAD",
                -1,
                false,
                Script_Program.def_function,
                Script_Program.def_argsize,
                Script_Program.def_void
            ),  //
            opcode_s(
                "<PUSH>",
                "PUSH_F",
                -1,
                false,
                Script_Program.def_float,
                Script_Program.def_float,
                Script_Program.def_void
            ),
            opcode_s(
                "<PUSH>",
                "PUSH_V",
                -1,
                false,
                Script_Program.def_vector,
                Script_Program.def_vector,
                Script_Program.def_void
            ),
            opcode_s(
                "<PUSH>",
                "PUSH_S",
                -1,
                false,
                Script_Program.def_string,
                Script_Program.def_string,
                Script_Program.def_void
            ),
            opcode_s(
                "<PUSH>",
                "PUSH_ENT",
                -1,
                false,
                Script_Program.def_entity,
                Script_Program.def_entity,
                Script_Program.def_void
            ),
            opcode_s(
                "<PUSH>",
                "PUSH_OBJ",
                -1,
                false,
                Script_Program.def_object,
                Script_Program.def_object,
                Script_Program.def_void
            ),
            opcode_s(
                "<PUSH>",
                "PUSH_OBJENT",
                -1,
                false,
                Script_Program.def_entity,
                Script_Program.def_object,
                Script_Program.def_void
            ),
            opcode_s(
                "<PUSH>",
                "PUSH_FTOS",
                -1,
                false,
                Script_Program.def_string,
                Script_Program.def_float,
                Script_Program.def_void
            ),
            opcode_s(
                "<PUSH>",
                "PUSH_BTOF",
                -1,
                false,
                Script_Program.def_float,
                Script_Program.def_boolean,
                Script_Program.def_void
            ),
            opcode_s(
                "<PUSH>",
                "PUSH_FTOB",
                -1,
                false,
                Script_Program.def_boolean,
                Script_Program.def_float,
                Script_Program.def_void
            ),
            opcode_s(
                "<PUSH>",
                "PUSH_VTOS",
                -1,
                false,
                Script_Program.def_string,
                Script_Program.def_vector,
                Script_Program.def_void
            ),
            opcode_s(
                "<PUSH>",
                "PUSH_BTOS",
                -1,
                false,
                Script_Program.def_string,
                Script_Program.def_boolean,
                Script_Program.def_void
            ),  //
            opcode_s(
                "<GOTO>",
                "GOTO",
                -1,
                false,
                Script_Program.def_jumpoffset,
                Script_Program.def_void,
                Script_Program.def_void
            ),  //
            opcode_s(
                "&&",
                "AND",
                7,
                false,
                Script_Program.def_float,
                Script_Program.def_float,
                Script_Program.def_float
            ),
            opcode_s(
                "&&",
                "AND_BOOLF",
                7,
                false,
                Script_Program.def_boolean,
                Script_Program.def_float,
                Script_Program.def_float
            ),
            opcode_s(
                "&&",
                "AND_FBOOL",
                7,
                false,
                Script_Program.def_float,
                Script_Program.def_boolean,
                Script_Program.def_float
            ),
            opcode_s(
                "&&",
                "AND_BOOLBOOL",
                7,
                false,
                Script_Program.def_boolean,
                Script_Program.def_boolean,
                Script_Program.def_float
            ),
            opcode_s(
                "||",
                "OR",
                7,
                false,
                Script_Program.def_float,
                Script_Program.def_float,
                Script_Program.def_float
            ),
            opcode_s(
                "||",
                "OR_BOOLF",
                7,
                false,
                Script_Program.def_boolean,
                Script_Program.def_float,
                Script_Program.def_float
            ),
            opcode_s(
                "||",
                "OR_FBOOL",
                7,
                false,
                Script_Program.def_float,
                Script_Program.def_boolean,
                Script_Program.def_float
            ),
            opcode_s(
                "||",
                "OR_BOOLBOOL",
                7,
                false,
                Script_Program.def_boolean,
                Script_Program.def_boolean,
                Script_Program.def_float
            ),  //
            opcode_s(
                "&",
                "BITAND",
                3,
                false,
                Script_Program.def_float,
                Script_Program.def_float,
                Script_Program.def_float
            ),
            opcode_s(
                "|",
                "BITOR",
                3,
                false,
                Script_Program.def_float,
                Script_Program.def_float,
                Script_Program.def_float
            ),  //
            opcode_s(
                "<BREAK>",
                "BREAK",
                -1,
                false,
                Script_Program.def_float,
                Script_Program.def_void,
                Script_Program.def_void
            ),
            opcode_s(
                "<CONTINUE>",
                "CONTINUE",
                -1,
                false,
                Script_Program.def_float,
                Script_Program.def_void,
                Script_Program.def_void
            ),  //
            null
        )
        private val punctuation = arrayOf(
            "+=", "-=", "*=", "/=", "%=", "&=", "|=", "++", "--",
            "&&", "||", "<=", ">=", "==", "!=", "::", ";", ",",
            "~", "!", "*", "/", "%", "(", ")", "-", "+",
            "=", "[", "]", ".", "<", ">", "&", "|", ":", null
        )
        var bla = 0

        /*
         ==============
         idCompiler::NextToken

         Sets token, immediateType, and possibly immediate
         ==============
         */
        var bla2 = 0
        var blaaaa = 0
        private var DBG_GetExpression = 0

        /*
         ============
         idCompiler::ParseValue

         Returns the def for the current token
         ============
         */
        private var DBG_ParseValue = 0
        private var punctuationValid = BooleanArray(256)
    }
}

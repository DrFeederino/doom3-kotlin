package neo.framework

import neo.TempDump
import neo.TempDump.SERiAL
import neo.TempDump.TODO_Exception
import neo.framework.Async.AsyncNetwork.idAsyncNetwork
import neo.framework.CVarSystem.idCVar
import neo.framework.CmdSystem.idCmdSystem.ArgCompletion_Integer
import neo.framework.KeyInput.idKeyInput
import neo.framework.UsercmdGen.idUsercmdGenLocal.*
import neo.idlib.BIT
import neo.idlib.LittleLong
import neo.idlib.LittleShort
import neo.idlib.Text.Str.idStr
import neo.idlib.idException
import neo.idlib.math.ANGLE2SHORT
import neo.idlib.math.PITCH
import neo.idlib.math.YAW
import neo.idlib.math.idMath.ClampChar
import neo.idlib.math.idMath.M_MS2SEC
import neo.idlib.math.idVec3
import neo.sys.sys_public.joystickAxis_t
import neo.sys.sys_public.sysEventType_t
import neo.sys.win_input
import neo.sys.win_main
import org.lwjgl.glfw.*
import java.nio.ByteBuffer
import java.time.Instant
import java.util.*
import kotlin.experimental.or
import kotlin.experimental.xor
import kotlin.math.abs
import kotlin.math.sqrt

object UsercmdGen {
    val BUTTON_5: Int = BIT(5)
    val BUTTON_6: Int = BIT(6)
    val BUTTON_7: Int = BIT(7)
    val BUTTON_ATTACK: Int = BIT(0)
    val BUTTON_MLOOK: Int = BIT(4)
    val BUTTON_RUN: Int = BIT(1)
    val BUTTON_SCORES: Int = BIT(3)
    val BUTTON_ZOOM: Int = BIT(2)
    const val IMPULSE_0 = 0 // weap 0
    const val IMPULSE_1 = 1 // weap 1
    const val IMPULSE_10 = 10 // weap 10
    const val IMPULSE_11 = 11 // weap 11
    const val IMPULSE_12 = 12 // weap 12
    const val IMPULSE_13 = 13 // weap reload
    const val IMPULSE_14 = 14 // weap next
    const val IMPULSE_15 = 15 // weap prev
    const val IMPULSE_16 = 16 // <unused>
    const val IMPULSE_17 = 17 // ready to play ( toggles ui_ready )
    const val IMPULSE_18 = 18 // center view
    const val IMPULSE_19 = 19 // show PDA/INV/MAP
    const val IMPULSE_2 = 2 // weap 2
    const val IMPULSE_20 = 20 // toggle team ( toggles ui_team )
    const val IMPULSE_21 = 21 // <unused>
    const val IMPULSE_22 = 22 // spectate
    const val IMPULSE_23 = 23 // <unused>
    const val IMPULSE_24 = 24 // <unused>
    const val IMPULSE_25 = 25 // <unused>
    const val IMPULSE_26 = 26 // <unused>
    const val IMPULSE_27 = 27 // <unused>
    const val IMPULSE_28 = 28 // vote yes
    const val IMPULSE_29 = 29 // vote no
    const val IMPULSE_3 = 3 // weap 3
    const val IMPULSE_4 = 4 // weap 4
    const val IMPULSE_40 = 40 // use vehicle
    const val IMPULSE_5 = 5 // weap 5
    const val IMPULSE_6 = 6 // weap 6
    const val IMPULSE_7 = 7 // weap 7
    const val IMPULSE_8 = 8 // weap 8
    const val IMPULSE_9 = 9 // weap 9
    const val UCF_IMPULSE_SEQUENCE = 0x0001 // toggled every time an impulse command is sent

    /*
     ===============================================================================

     Samples a set of user commands from player input.

     ===============================================================================
     */
    const val USERCMD_HZ = 60 // 60 frames per second
    const val USERCMD_MSEC = 1000 / USERCMD_HZ
    const val KEY_MOVESPEED = 127
    const val MAX_BUFFERED_USERCMD = 64
    const val MAX_CHAT_BUFFER = 127

    val userCmdStrings: Array<userCmdString_t> = arrayOf(
        userCmdString_t("_moveUp", usercmdButton_t.UB_UP),
        userCmdString_t("_moveDown", usercmdButton_t.UB_DOWN),
        userCmdString_t("_left", usercmdButton_t.UB_LEFT),
        userCmdString_t("_right", usercmdButton_t.UB_RIGHT),
        userCmdString_t("_forward", usercmdButton_t.UB_FORWARD),
        userCmdString_t("_back", usercmdButton_t.UB_BACK),
        userCmdString_t("_lookUp", usercmdButton_t.UB_LOOKUP),
        userCmdString_t("_lookDown", usercmdButton_t.UB_LOOKDOWN),
        userCmdString_t("_strafe", usercmdButton_t.UB_STRAFE),
        userCmdString_t("_moveLeft", usercmdButton_t.UB_MOVELEFT),
        userCmdString_t("_moveRight", usercmdButton_t.UB_MOVERIGHT),  //
        userCmdString_t("_attack", usercmdButton_t.UB_ATTACK),
        userCmdString_t("_speed", usercmdButton_t.UB_SPEED),
        userCmdString_t("_zoom", usercmdButton_t.UB_ZOOM),
        userCmdString_t("_showScores", usercmdButton_t.UB_SHOWSCORES),
        userCmdString_t("_mlook", usercmdButton_t.UB_MLOOK),  //
        userCmdString_t("_button0", usercmdButton_t.UB_BUTTON0),
        userCmdString_t("_button1", usercmdButton_t.UB_BUTTON1),
        userCmdString_t("_button2", usercmdButton_t.UB_BUTTON2),
        userCmdString_t("_button3", usercmdButton_t.UB_BUTTON3),
        userCmdString_t("_button4", usercmdButton_t.UB_BUTTON4),
        userCmdString_t("_button5", usercmdButton_t.UB_BUTTON5),
        userCmdString_t("_button6", usercmdButton_t.UB_BUTTON6),
        userCmdString_t("_button7", usercmdButton_t.UB_BUTTON7),  //
        userCmdString_t("_impulse0", usercmdButton_t.UB_IMPULSE0),
        userCmdString_t("_impulse1", usercmdButton_t.UB_IMPULSE1),
        userCmdString_t("_impulse2", usercmdButton_t.UB_IMPULSE2),
        userCmdString_t("_impulse3", usercmdButton_t.UB_IMPULSE3),
        userCmdString_t("_impulse4", usercmdButton_t.UB_IMPULSE4),
        userCmdString_t("_impulse5", usercmdButton_t.UB_IMPULSE5),
        userCmdString_t("_impulse6", usercmdButton_t.UB_IMPULSE6),
        userCmdString_t("_impulse7", usercmdButton_t.UB_IMPULSE7),
        userCmdString_t("_impulse8", usercmdButton_t.UB_IMPULSE8),
        userCmdString_t("_impulse9", usercmdButton_t.UB_IMPULSE9),
        userCmdString_t("_impulse10", usercmdButton_t.UB_IMPULSE10),
        userCmdString_t("_impulse11", usercmdButton_t.UB_IMPULSE11),
        userCmdString_t("_impulse12", usercmdButton_t.UB_IMPULSE12),
        userCmdString_t("_impulse13", usercmdButton_t.UB_IMPULSE13),
        userCmdString_t("_impulse14", usercmdButton_t.UB_IMPULSE14),
        userCmdString_t("_impulse15", usercmdButton_t.UB_IMPULSE15),
        userCmdString_t("_impulse16", usercmdButton_t.UB_IMPULSE16),
        userCmdString_t("_impulse17", usercmdButton_t.UB_IMPULSE17),
        userCmdString_t("_impulse18", usercmdButton_t.UB_IMPULSE18),
        userCmdString_t("_impulse19", usercmdButton_t.UB_IMPULSE19),
        userCmdString_t("_impulse20", usercmdButton_t.UB_IMPULSE20),
        userCmdString_t("_impulse21", usercmdButton_t.UB_IMPULSE21),
        userCmdString_t("_impulse22", usercmdButton_t.UB_IMPULSE22),
        userCmdString_t("_impulse23", usercmdButton_t.UB_IMPULSE23),
        userCmdString_t("_impulse24", usercmdButton_t.UB_IMPULSE24),
        userCmdString_t("_impulse25", usercmdButton_t.UB_IMPULSE25),
        userCmdString_t("_impulse26", usercmdButton_t.UB_IMPULSE26),
        userCmdString_t("_impulse27", usercmdButton_t.UB_IMPULSE27),
        userCmdString_t("_impulse28", usercmdButton_t.UB_IMPULSE28),
        userCmdString_t("_impulse29", usercmdButton_t.UB_IMPULSE29),
        userCmdString_t("_impulse30", usercmdButton_t.UB_IMPULSE30),
        userCmdString_t("_impulse31", usercmdButton_t.UB_IMPULSE31),
        userCmdString_t("_impulse32", usercmdButton_t.UB_IMPULSE32),
        userCmdString_t("_impulse33", usercmdButton_t.UB_IMPULSE33),
        userCmdString_t("_impulse34", usercmdButton_t.UB_IMPULSE34),
        userCmdString_t("_impulse35", usercmdButton_t.UB_IMPULSE35),
        userCmdString_t("_impulse36", usercmdButton_t.UB_IMPULSE36),
        userCmdString_t("_impulse37", usercmdButton_t.UB_IMPULSE37),
        userCmdString_t("_impulse38", usercmdButton_t.UB_IMPULSE38),
        userCmdString_t("_impulse39", usercmdButton_t.UB_IMPULSE39),
        userCmdString_t("_impulse40", usercmdButton_t.UB_IMPULSE40),
        userCmdString_t("_impulse41", usercmdButton_t.UB_IMPULSE41),
        userCmdString_t("_impulse42", usercmdButton_t.UB_IMPULSE42),
        userCmdString_t("_impulse43", usercmdButton_t.UB_IMPULSE43),
        userCmdString_t("_impulse44", usercmdButton_t.UB_IMPULSE44),
        userCmdString_t("_impulse45", usercmdButton_t.UB_IMPULSE45),
        userCmdString_t("_impulse46", usercmdButton_t.UB_IMPULSE46),
        userCmdString_t("_impulse47", usercmdButton_t.UB_IMPULSE47),
        userCmdString_t("_impulse48", usercmdButton_t.UB_IMPULSE48),
        userCmdString_t("_impulse49", usercmdButton_t.UB_IMPULSE49),
        userCmdString_t("_impulse50", usercmdButton_t.UB_IMPULSE50),
        userCmdString_t("_impulse51", usercmdButton_t.UB_IMPULSE51),
        userCmdString_t("_impulse52", usercmdButton_t.UB_IMPULSE52),
        userCmdString_t("_impulse53", usercmdButton_t.UB_IMPULSE53),
        userCmdString_t("_impulse54", usercmdButton_t.UB_IMPULSE54),
        userCmdString_t("_impulse55", usercmdButton_t.UB_IMPULSE55),
        userCmdString_t("_impulse56", usercmdButton_t.UB_IMPULSE56),
        userCmdString_t("_impulse57", usercmdButton_t.UB_IMPULSE57),
        userCmdString_t("_impulse58", usercmdButton_t.UB_IMPULSE58),
        userCmdString_t("_impulse59", usercmdButton_t.UB_IMPULSE59),
        userCmdString_t("_impulse60", usercmdButton_t.UB_IMPULSE60),
        userCmdString_t("_impulse61", usercmdButton_t.UB_IMPULSE61),
        userCmdString_t("_impulse62", usercmdButton_t.UB_IMPULSE62),
        userCmdString_t("_impulse63", usercmdButton_t.UB_IMPULSE63),  //
        userCmdString_t(null, usercmdButton_t.UB_NONE)
    )

    //
    //
    val NUM_USER_COMMANDS = userCmdStrings.size
    private var localUsercmdGen: idUsercmdGenLocal = idUsercmdGenLocal()
    var usercmdGen: idUsercmdGen = localUsercmdGen
    fun setUsercmdGens(usercmdGen: idUsercmdGen) {
        localUsercmdGen = usercmdGen as idUsercmdGenLocal
        UsercmdGen.usercmdGen = localUsercmdGen
    }

    enum class inhibit_t {
        INHIBIT_SESSION, INHIBIT_ASYNC
    }

    enum class usercmdButton_t {
        UB_NONE,  //
        UB_UP, UB_DOWN, UB_LEFT, UB_RIGHT, UB_FORWARD, UB_BACK, UB_LOOKUP, UB_LOOKDOWN, UB_STRAFE, UB_MOVELEFT, UB_MOVERIGHT,  //
        UB_BUTTON0, UB_BUTTON1, UB_BUTTON2, UB_BUTTON3, UB_BUTTON4, UB_BUTTON5, UB_BUTTON6, UB_BUTTON7,  //
        UB_ATTACK, UB_SPEED, UB_ZOOM, UB_SHOWSCORES, UB_MLOOK,  //
        UB_IMPULSE0, UB_IMPULSE1, UB_IMPULSE2, UB_IMPULSE3, UB_IMPULSE4, UB_IMPULSE5, UB_IMPULSE6, UB_IMPULSE7, UB_IMPULSE8, UB_IMPULSE9, UB_IMPULSE10, UB_IMPULSE11, UB_IMPULSE12, UB_IMPULSE13, UB_IMPULSE14, UB_IMPULSE15, UB_IMPULSE16, UB_IMPULSE17, UB_IMPULSE18, UB_IMPULSE19, UB_IMPULSE20, UB_IMPULSE21, UB_IMPULSE22, UB_IMPULSE23, UB_IMPULSE24, UB_IMPULSE25, UB_IMPULSE26, UB_IMPULSE27, UB_IMPULSE28, UB_IMPULSE29, UB_IMPULSE30, UB_IMPULSE31, UB_IMPULSE32, UB_IMPULSE33, UB_IMPULSE34, UB_IMPULSE35, UB_IMPULSE36, UB_IMPULSE37, UB_IMPULSE38, UB_IMPULSE39, UB_IMPULSE40, UB_IMPULSE41, UB_IMPULSE42, UB_IMPULSE43, UB_IMPULSE44, UB_IMPULSE45, UB_IMPULSE46, UB_IMPULSE47, UB_IMPULSE48, UB_IMPULSE49, UB_IMPULSE50, UB_IMPULSE51, UB_IMPULSE52, UB_IMPULSE53, UB_IMPULSE54, UB_IMPULSE55, UB_IMPULSE56, UB_IMPULSE57, UB_IMPULSE58, UB_IMPULSE59, UB_IMPULSE60, UB_IMPULSE61, UB_IMPULSE62, UB_IMPULSE63,  //
        UB_MAX_BUTTONS
    }

    class usercmd_t : SERiAL {
        var angles: ShortArray = ShortArray(3) // view angles
        var buttons // buttons
                : Byte = 0
        var duplicateCount // duplication count for networking
                = 0
        var flags // additional flags
                : Byte = 0
        var forwardmove // forward/backward movement
                : Byte = 0
        var gameFrame // frame number
                = 0
        var gameTime // game time
                = 0
        var impulse // impulse command
                : Byte = 0
        var mx // mouse delta x
                : Short = 0
        var my // mouse delta y
                : Short = 0
        var rightmove // left/right movement
                : Byte = 0
        var sequence // just for debugging
                = 0
        var upmove // up/down movement
                : Byte = 0

        constructor()

        fun ByteSwap() {            // on big endian systems, byte swap the shorts and ints
            angles[0] = LittleShort(angles[0])
            angles[1] = LittleShort(angles[1])
            angles[2] = LittleShort(angles[2])
            sequence = LittleLong(sequence)
        }

        override fun hashCode(): Int {
            var hash = 5
            hash = 83 * hash + buttons
            hash = 83 * hash + forwardmove
            hash = 83 * hash + rightmove
            hash = 83 * hash + upmove
            hash = 83 * hash + angles.contentHashCode()
            hash = 83 * hash + mx
            hash = 83 * hash + my
            hash = 83 * hash + impulse
            hash = 83 * hash + flags
            return hash
        }

        override fun equals(obj: Any?): Boolean {
            if (obj == null) {
                return false
            }
            if (javaClass != obj.javaClass) {
                return false
            }
            val other = obj as usercmd_t
            if (buttons != other.buttons) {
                return false
            }
            if (forwardmove != other.forwardmove) {
                return false
            }
            if (rightmove != other.rightmove) {
                return false
            }
            if (upmove != other.upmove) {
                return false
            }
            if (!angles.contentEquals(other.angles)) {
                return false
            }
            if (mx != other.mx) {
                return false
            }
            if (my != other.my) {
                return false
            }
            return if (impulse != other.impulse) {
                false
            } else flags == other.flags
        }

        override fun AllocBuffer(): ByteBuffer {
            throw TODO_Exception()
        }

        override fun Read(buffer: ByteBuffer) {
            throw TODO_Exception()
        }

        override fun Write(): ByteBuffer {
            throw TODO_Exception()
        }

        companion object {
            @Transient
            val BYTES = Integer.BYTES * 4 + 6 + 5 * java.lang.Short.BYTES
        }
    }

    abstract class idUsercmdGen {
        lateinit var keyboardCallback: KeyboardCallback
        lateinit var mouseButtonCallback: MouseButtonCallback
        lateinit var mouseCursorCallback: MouseCursorCallback
        lateinit var mouseScrollCallback: MouseScrollCallback

        // virtual 				~idUsercmdGen( void ) {}
        // Sets up all the cvars and console commands.
        abstract fun Init()

        // Prepares for a new map.
        abstract fun InitForNewMap()

        // Shut down.
        abstract fun Shutdown()

        // Clears all key states and face straight.
        abstract fun Clear()

        // Clears view angles.
        abstract fun ClearAngles()

        // When the console is down or the menu is up, only emit default usercmd, so the player isn't moving around.
        // Each subsystem (session and game) may want an inhibit will OR the requests.
        abstract fun InhibitUsercmd(subsystem: inhibit_t, inhibit: Boolean)

        // Returns a buffered command for the given game tic.
        @Throws(idException::class)
        abstract fun TicCmd(ticNumber: Int): usercmd_t

        // Called async at regular intervals.
        abstract fun UsercmdInterrupt()

        // Set a value that can safely be referenced by UsercmdInterrupt() for each key binding.
        abstract fun CommandStringUsercmdData(cmdString: String): Int

        // Returns the number of user commands.
        abstract fun GetNumUserCommands(): Int

        // Returns the name of a user command via index.
        abstract fun GetUserCommandName(index: Int): String

        // Continuously modified, never reset. For full screen guis.
        abstract fun MouseState(x: IntArray, y: IntArray, button: IntArray, down: BooleanArray)

        // Directly sample a button.
        abstract fun ButtonState(key: Int): Int

        // Directly sample a keystate.
        abstract fun KeyState(key: Int): Int

        // Directly sample a usercmd.
        abstract fun GetDirectUsercmd(): usercmd_t
    }

    class userCmdString_t(var string: String?, var button: usercmdButton_t)

    //    
    //   
    internal class buttonState_t {
        var held = false
        var on = 0
        fun Clear() {
            held = false
            on = 0
        }

        fun SetKeyState(keystate: Int, toggle: Boolean) {
            if (!toggle) {
                held = false
                on = keystate
            } else if (0 == keystate) {
                held = false
            } else if (!held) {
                held = true
                on = on xor 1
            }
        }

        init {
            Clear()
        }
    }

    class idUsercmdGenLocal : idUsercmdGen() {
        private val buffered: Array<usercmd_t> = Array(MAX_BUFFERED_USERCMD) { usercmd_t() }
        private val buttonState: IntArray = IntArray(TempDump.etoi(usercmdButton_t.UB_MAX_BUTTONS))
        private val joystickAxis: IntArray =
            IntArray(TempDump.etoi(joystickAxis_t.MAX_JOYSTICK_AXIS)) // set by joystick events
        private val keyState: BooleanArray = BooleanArray(KeyInput.K_LAST_KEY)
        private val lastCommandTime = 0

        //
        private val toggled_crouch: buttonState_t
        private val toggled_run: buttonState_t

        //
        //
        private val toggled_zoom: buttonState_t
        private val viewangles: idVec3 = idVec3()

        //
        private var cmd // the current cmd being built
                : usercmd_t

        //
        private var continuousMouseX = 0.0
        private var continuousMouseY // for gui event generatioin, never zerod
                = 0.0
        private var flags = 0
        private var impulse = 0

        //
        private var inhibitCommands // true when in console or menu locally
                = 0

        //
        private var initialized = false
        private var mouseButton // for gui event generatioin
                = 0
        private var mouseDown = false

        //
        private var mouseDx = 0.0
        private var mouseDy // added to by mouse events
                = 0.0

        override fun Init() {
            initialized = true
        }

        override fun InitForNewMap() {
            flags = 0
            impulse = 0
            toggled_crouch.Clear()
            toggled_run.Clear()
            toggled_zoom.Clear()
            toggled_run.on = if (in_alwaysRun.GetBool()) 1 else 0
            Clear()
            ClearAngles()
        }

        override fun Shutdown() {
            initialized = false
        }

        override fun Clear() {
            // clears all key states
            Arrays.fill(buttonState, 0) //	memset( buttonState, 0, sizeof( buttonState ) );
            Arrays.fill(keyState, false) //	memset( keyState, false, sizeof( keyState ) );
            inhibitCommands = 0 //false;
            mouseDy = 0.0
            mouseDx = mouseDy
            mouseButton = 0
            mouseDown = false
        }

        override fun ClearAngles() {
            viewangles.Zero()
        }

        /*
         ================
         idUsercmdGenLocal::TicCmd

         Returns a buffered usercmd
         ================
         */
        @Throws(idException::class)
        override fun TicCmd(ticNumber: Int): usercmd_t {

            // the packetClient code can legally ask for com_ticNumber+1, because
            // it is in the async code and com_ticNumber hasn't been updated yet,
            // but all other code should never ask for anything > com_ticNumber
            if (ticNumber > Common.com_ticNumber + 1) {
                Common.common.Error("idUsercmdGenLocal::TicCmd ticNumber > com_ticNumber")
            }
            if (ticNumber <= Common.com_ticNumber - MAX_BUFFERED_USERCMD) {
                // this can happen when something in the game code hitches badly, allowing the
                // async code to overflow the buffers
                //common.Printf( "warning: idUsercmdGenLocal::TicCmd ticNumber <= com_ticNumber - MAX_BUFFERED_USERCMD\n" );
            }
            return buffered[ticNumber and (MAX_BUFFERED_USERCMD - 1)]!!
        }

        override fun InhibitUsercmd(subsystem: inhibit_t, inhibit: Boolean) {
            inhibitCommands = if (inhibit) {
                inhibitCommands or (1 shl subsystem.ordinal)
            } else {
                inhibitCommands and (-0x1 xor (1 shl subsystem.ordinal))
            }
        }

        /*
         ================
         idUsercmdGenLocal::UsercmdInterrupt

         Called asyncronously
         ================
         */
        override fun UsercmdInterrupt() {
            // dedicated servers won't create usercmds
            if (!initialized) {
                return
            }

//            Display.processMessages();
            // init the usercmd for com_ticNumber+1
            InitCurrent()

            // process the system mouse events
//            Mouse();

            // process the system keyboard events
//            Keyboard();

            // process the system joystick events
            Joystick()

            // create the usercmd for com_ticNumber+1
            MakeCurrent()

            // save a number for debugging cmdDemos and networking
            cmd.sequence = Common.com_ticNumber + 1
            buffered[Common.com_ticNumber + 1 and MAX_BUFFERED_USERCMD - 1] = cmd
        }

        /*
         ================
         idUsercmdGenLocal::CommandStringUsercmdData

         Returns the button if the command string is used by the async usercmd generator.
         ================
         */
        override fun CommandStringUsercmdData(cmdString: String): Int {
            for (ucs in userCmdStrings) {
                if (idStr.Icmp(cmdString, ucs.string ?: "") == 0) {
                    return ucs.button.ordinal
                }
            }
            return usercmdButton_t.UB_NONE.ordinal
        }

        override fun GetNumUserCommands(): Int {
            return NUM_USER_COMMANDS
        }

        override fun GetUserCommandName(index: Int): String {
            return if (index >= 0 && index < NUM_USER_COMMANDS) {
                userCmdStrings[index].string!!
            } else ""
        }

        override fun MouseState(x: IntArray, y: IntArray, button: IntArray, down: BooleanArray) {
//            x[0] = continuousMouseX;
//            y[0] = continuousMouseY;
            button[0] = mouseButton
            down[0] = mouseDown
        }

        /*
         ===============
         idUsercmdGenLocal::ButtonState

         Returns (the fraction of the frame) that the key was down
         ===============
         */
        override fun ButtonState(key: Int): Int {
            if (key < 0 || key >= usercmdButton_t.UB_MAX_BUTTONS.ordinal) {
                return -1
            }
            return if (buttonState[key] > 0) 1 else 0
        }

        fun ButtonState(key: usercmdButton_t): Int {
            return ButtonState(key.ordinal)
        }

        /*
         ===============
         idUsercmdGenLocal::KeyState

         Returns (the fraction of the frame) that the key was down
         bk20060111
         ===============
         */
        override fun KeyState(key: Int): Int {
            if (key < 0 || key >= KeyInput.K_LAST_KEY) {
                return -1
            }
            return if (keyState[key]) 1 else 0
        }

        override fun GetDirectUsercmd(): usercmd_t {

            // initialize current usercmd
            InitCurrent()

            // process the system mouse events
//            Mouse();

            // process the system keyboard events
//            Keyboard();

//            // process the system joystick events
//            Joystick();
//TODO:enable our input devices.

            // create the usercmd
            MakeCurrent()
            cmd.duplicateCount = 0
            return cmd
        }

        /*
         ================
         idUsercmdGenLocal::MakeCurrent

         creates the current command for this frame
         ================
         */
        private fun MakeCurrent() {
            val oldAngles = idVec3(viewangles)
            var i: Int
            if (!Inhibited()) {
                // update toggled key states
                toggled_crouch.SetKeyState(ButtonState(usercmdButton_t.UB_DOWN), in_toggleCrouch.GetBool())
                toggled_run.SetKeyState(
                    ButtonState(usercmdButton_t.UB_SPEED),
                    in_toggleRun.GetBool() && idAsyncNetwork.IsActive()
                )
                toggled_zoom.SetKeyState(ButtonState(usercmdButton_t.UB_ZOOM), in_toggleZoom.GetBool())

                // keyboard angle adjustment
                AdjustAngles()

                // set button bits
                CmdButtons()

                // get basic movement from keyboard
                KeyMove()

                // get basic movement from mouse
                MouseMove()

                // get basic movement from joystick
                JoystickMove()

                // check to make sure the angles haven't wrapped
                if (viewangles[PITCH] - oldAngles[PITCH] > 90) {
                    viewangles[PITCH] = oldAngles[PITCH] + 90
                } else if (oldAngles[PITCH] - viewangles[PITCH] > 90) {
                    viewangles[PITCH] = oldAngles[PITCH] - 90
                }
            } else {
                mouseDx = 0.0
                mouseDy = 0.0
            }
            i = 0
            while (i < 3) {
                cmd.angles[i] = ANGLE2SHORT(viewangles[i]).toInt().toShort()
                i++
            }
            cmd.mx = continuousMouseX.toInt().toShort()
            cmd.my = continuousMouseY.toInt().toShort()
            flags = cmd.flags.toInt()
            impulse = cmd.impulse.toInt()
        }

        /*
         ================
         idUsercmdGenLocal::InitCurrent

         inits the current command for this frame
         ================
         */
        private fun InitCurrent() {
            cmd = usercmd_t() //memset( &cmd, 0, sizeof( cmd ) );
            cmd.flags = flags.toByte()
            cmd.impulse = impulse.toByte()
            cmd.buttons =
                cmd.buttons or (if (in_alwaysRun.GetBool() && idAsyncNetwork.IsActive()) BUTTON_RUN else 0).toByte()
            cmd.buttons = cmd.buttons or (if (in_freeLook.GetBool()) BUTTON_MLOOK else 0).toByte()
        }

        /*
         ================
         idUsercmdGenLocal::Inhibited

         is user cmd generation inhibited
         ================
         */
        private fun Inhibited(): Boolean {
            return inhibitCommands != 0
        }

        /*
         ================
         idUsercmdGenLocal::AdjustAngles

         Moves the local angle positions
         ================
         */
        private fun AdjustAngles() {
            val speed: Float
            speed = if ((toggled_run.on != 0) xor (in_alwaysRun.GetBool() && idAsyncNetwork.IsActive())) {
                M_MS2SEC * USERCMD_MSEC * in_angleSpeedKey.GetFloat()
            } else {
                M_MS2SEC * USERCMD_MSEC
            }
            if (0 == ButtonState(usercmdButton_t.UB_STRAFE)) {
                viewangles.minusAssign(
                    YAW,
                    speed * in_yawSpeed.GetFloat() * ButtonState(usercmdButton_t.UB_RIGHT)
                )
                viewangles.plusAssign(YAW, speed * in_yawSpeed.GetFloat() * ButtonState(usercmdButton_t.UB_LEFT))
            }
            viewangles.minusAssign(
                PITCH,
                speed * in_pitchSpeed.GetFloat() * ButtonState(usercmdButton_t.UB_LOOKUP)
            )
            viewangles.plusAssign(
                PITCH,
                speed * in_pitchSpeed.GetFloat() * ButtonState(usercmdButton_t.UB_LOOKDOWN)
            )
        }

        /*
         ================
         idUsercmdGenLocal::KeyMove

         Sets the usercmd_t based on key states
         ================
         */
        private fun KeyMove() {
            var forward: Int
            var side: Int
            var up: Int
            forward = 0
            side = 0
            up = 0
            if (ButtonState(usercmdButton_t.UB_STRAFE) != 0) {
                side += KEY_MOVESPEED * ButtonState(usercmdButton_t.UB_RIGHT)
                side -= KEY_MOVESPEED * ButtonState(usercmdButton_t.UB_LEFT)
            }
            side += KEY_MOVESPEED * ButtonState(usercmdButton_t.UB_MOVERIGHT)
            side -= KEY_MOVESPEED * ButtonState(usercmdButton_t.UB_MOVELEFT)
            up -= KEY_MOVESPEED * toggled_crouch.on
            up += KEY_MOVESPEED * ButtonState(usercmdButton_t.UB_UP)
            forward += KEY_MOVESPEED * ButtonState(usercmdButton_t.UB_FORWARD)
            forward -= KEY_MOVESPEED * ButtonState(usercmdButton_t.UB_BACK)
            cmd.forwardmove = ClampChar(forward).code.toByte()
            cmd.rightmove = ClampChar(side).code.toByte()
            cmd.upmove = ClampChar(up).code.toByte()
        }

        private fun JoystickMove() {
            val anglespeed: Float
            anglespeed = if ((toggled_run.on != 0) xor (in_alwaysRun.GetBool() && idAsyncNetwork.IsActive())) {
                M_MS2SEC * USERCMD_MSEC * in_angleSpeedKey.GetFloat()
            } else {
                M_MS2SEC * USERCMD_MSEC
            }
            if (0 == ButtonState(usercmdButton_t.UB_STRAFE)) {
                viewangles.plusAssign(
                    YAW,
                    anglespeed * in_yawSpeed.GetFloat() * joystickAxis[joystickAxis_t.AXIS_SIDE.ordinal]
                )
                viewangles.plusAssign(
                    PITCH,
                    anglespeed * in_pitchSpeed.GetFloat() * joystickAxis[joystickAxis_t.AXIS_FORWARD.ordinal]
                )
            } else {
                cmd.rightmove =
                    ClampChar(cmd.rightmove + joystickAxis[joystickAxis_t.AXIS_SIDE.ordinal]).code.toByte()
                cmd.forwardmove =
                    ClampChar(cmd.forwardmove + joystickAxis[joystickAxis_t.AXIS_FORWARD.ordinal]).code.toByte()
            }
            cmd.upmove = ClampChar(cmd.upmove + joystickAxis[joystickAxis_t.AXIS_UP.ordinal]).code.toByte()
        }

        private fun MouseMove() {
            var mx: Float
            var my: Float
            var strafeMx: Float
            var strafeMy: Float
            var i: Int
            history[historyCounter and 7][0] = mouseDx
            history[historyCounter and 7][1] = mouseDy

            // allow mouse movement to be smoothed together
            var smooth = m_smooth.GetInteger()
            if (smooth < 1) {
                smooth = 1
            }
            if (smooth > 8) {
                smooth = 8
            }
            mx = 0.0f
            my = 0.0f
            i = 0
            while (i < smooth) {
                mx += history[historyCounter - i + 8 and 7][0].toFloat()
                my += history[historyCounter - i + 8 and 7][1].toFloat()
                i++
            }
            mx /= smooth.toFloat()
            my /= smooth.toFloat()

            // use a larger smoothing for strafing
            smooth = m_strafeSmooth.GetInteger()
            if (smooth < 1) {
                smooth = 1
            }
            if (smooth > 8) {
                smooth = 8
            }
            strafeMx = 0.0f
            strafeMy = 0.0f
            i = 0
            while (i < smooth) {
                strafeMx += history[historyCounter - i + 8 and 7][0].toFloat()
                strafeMy += history[historyCounter - i + 8 and 7][1].toFloat()
                i++
            }
            strafeMx /= smooth.toFloat()
            strafeMy /= smooth.toFloat()
            historyCounter++
            if (abs(mx) > 1000 || abs(my) > 1000) {
                win_main.Sys_DebugPrintf("idUsercmdGenLocal.MouseMove: Ignoring ridiculous mouse delta.\n")
                my = 0.0f
                mx = my
            }
            mx *= sensitivity.GetFloat()
            my *= sensitivity.GetFloat()
            if (m_showMouseRate.GetBool()) {
                win_main.Sys_DebugPrintf(
                    "[%3d %3d  = %5.1f %5.1f = %5.1f %5.1f] ",
                    mouseDx,
                    mouseDy,
                    mx,
                    my,
                    strafeMx,
                    strafeMy
                )
            }
            mouseDx = 0.0
            mouseDy = 0.0
            if (0.0f == strafeMx && 0.0f == strafeMy) {
                return
            }
            if (ButtonState(usercmdButton_t.UB_STRAFE) != 0 || 0 == cmd.buttons.toInt() and BUTTON_MLOOK) {
                // add mouse X/Y movement to cmd
                strafeMx *= m_strafeScale.GetFloat()
                strafeMy *= m_strafeScale.GetFloat()
                // clamp as a vector, instead of separate floats
                val len = sqrt((strafeMx * strafeMx + strafeMy * strafeMy).toFloat()).toFloat()
                if (len > 127) {
                    strafeMx = strafeMx * 127 / len
                    strafeMy = strafeMy * 127 / len
                }
            }
            if (0 == ButtonState(usercmdButton_t.UB_STRAFE)) {
                viewangles.minusAssign(YAW, m_yaw.GetFloat() * mx)
            } else {
                cmd.rightmove = ClampChar((cmd.rightmove + strafeMx).toInt()).code.toByte()
            }
            if (0 == ButtonState(usercmdButton_t.UB_STRAFE) && cmd.buttons.toInt() and BUTTON_MLOOK != 0) {
                viewangles.plusAssign(PITCH, m_pitch.GetFloat() * my)
            } else {
                cmd.forwardmove = ClampChar((cmd.forwardmove - strafeMy).toInt()).code.toByte()
            }
        }

        private fun CmdButtons() {
            var i: Int
            cmd.buttons = 0

            // figure button bits
            i = 0
            while (i <= 7) {
                if (ButtonState( /*(usercmdButton_t)*/usercmdButton_t.UB_BUTTON0.ordinal + i) != 0) {
                    cmd.buttons = cmd.buttons or (1 shl i).toByte()
                }
                i++
            }

            // check the attack button
            if (ButtonState(usercmdButton_t.UB_ATTACK) != 0) {
                cmd.buttons = cmd.buttons or BUTTON_ATTACK.toByte()
            }

            // check the run button
            if ((toggled_run.on != 0) xor (in_alwaysRun.GetBool() && idAsyncNetwork.IsActive())) {
                cmd.buttons = cmd.buttons or BUTTON_RUN.toByte()
            }

            // check the zoom button
            if (toggled_zoom.on != 0) {
                cmd.buttons = cmd.buttons or BUTTON_ZOOM.toByte()
            }

            // check the scoreboard button
            if (ButtonState(usercmdButton_t.UB_SHOWSCORES) != 0 || ButtonState(usercmdButton_t.UB_IMPULSE19) != 0) {
                // the button is toggled in SP mode as well but without effect
                cmd.buttons = cmd.buttons or BUTTON_SCORES.toByte()
            }

            // check the mouse look button
            if (ButtonState(usercmdButton_t.UB_MLOOK) xor in_freeLook.GetInteger() != 0) {
                cmd.buttons = cmd.buttons or BUTTON_MLOOK.toByte()
            }
        }

        private fun Joystick() {
            Arrays.fill(joystickAxis, 0) //	memset( joystickAxis, 0, sizeof( joystickAxis ) );
        }

        /*
         ===================
         idUsercmdGenLocal::Key

         Handles async mouse/keyboard button actions
         ===================
         */
        private fun Key(keyNum: Int, down: Boolean) {

            // Sanity check, sometimes we get double message :(
            if (keyState[keyNum] == down) {
                return
            }
            keyState[keyNum] = down
            val action = idKeyInput.GetUsercmdAction(keyNum)
            if (down) {
                buttonState[action]++
                if (!Inhibited()) {
                    if (action >= usercmdButton_t.UB_IMPULSE0.ordinal && action <= usercmdButton_t.UB_IMPULSE61.ordinal) {
                        cmd.impulse = (action - usercmdButton_t.UB_IMPULSE0.ordinal).toByte()
                        impulse = cmd.impulse.toInt()
                        cmd.flags = cmd.flags xor UCF_IMPULSE_SEQUENCE.toByte()
                        flags = cmd.flags.toInt()
                    }
                }
            } else {
                buttonState[action]--
                // we might have one held down across an app active transition
                if (buttonState[action] < 0) {
                    buttonState[action] = 0
                }
            }
        }

        inner class MouseCursorCallback : GLFWCursorPosCallback() {
            private var prevX = 0.0
            private var prevY = 0.0
            override fun invoke(window: Long, xpos: Double, ypos: Double) {
                val dwTimeStamp = Instant.now().toEpochMilli()
                val dx = xpos - prevX
                val dy = ypos - prevY
                if (dx != 0.0 || dy != 0.0) {
                    mouseDx += dx
                    continuousMouseX += dx
                    prevX = xpos
                    mouseDy += dy
                    continuousMouseY += dy
                    prevY = ypos
                    win_main.Sys_QueEvent(dwTimeStamp, sysEventType_t.SE_MOUSE, dx.toInt(), dy.toInt(), 0, null)
                }
                win_input.Sys_EndMouseInputEvents()
            }
        }

        inner class MouseScrollCallback : GLFWScrollCallback() {
            override fun invoke(window: Long, xoffset: Double, yoffset: Double) {
                val dwTimeStamp = Instant.now().toEpochMilli()

                // mouse wheel actions are impulses, without a specific up / down
                var wheelValue = yoffset.toInt() //(int) polled_didod[n].dwData ) / WHEEL_DELTA;
                val key = if (yoffset < 0) KeyInput.K_MWHEELDOWN else KeyInput.K_MWHEELUP
                while (wheelValue-- > 0) {
                    Key(key, true)
                    Key(key, false)
                    mouseButton = key
                    mouseDown = true
                    win_main.Sys_QueEvent(dwTimeStamp, sysEventType_t.SE_KEY, key, TempDump.btoi(true), 0, null)
                    win_main.Sys_QueEvent(dwTimeStamp, sysEventType_t.SE_KEY, key, TempDump.btoi(false), 0, null)
                }
            }
        }

        inner class MouseButtonCallback : GLFWMouseButtonCallback() {
            override fun invoke(window: Long, button: Int, action: Int, mods: Int) {
                val dwTimeStamp = Instant.now().toEpochMilli()
                //
                // Study each of the buffer elements and process them.
                //
                if (button != -1) {
                    val buton = if (action != GLFW.GLFW_RELEASE) 0x80 else 0 // (polled_didod[n].dwData & 0x80) == 0x80;
                    mouseButton = KeyInput.K_MOUSE1 + button
                    mouseDown = buton != 0
                    Key(mouseButton, mouseDown)
                    win_main.Sys_QueEvent(dwTimeStamp, sysEventType_t.SE_KEY, mouseButton, buton, 0, null)
                }
                win_input.Sys_EndMouseInputEvents()
            }
        }

        inner class KeyboardCallback : GLFWKeyCallback() {
            override fun invoke(window: Long, key: Int, scancode: Int, action: Int, mods: Int) {
                val ch = intArrayOf(0)
                //                        //-+
                // Study each of the buffer elements and process them.
                //
                if (win_input.Sys_ReturnKeyboardInputEvent(ch, action, key, scancode, mods) != 0) {
                    Key(ch[0], action != GLFW.GLFW_RELEASE)
                }
                win_input.Sys_EndKeyboardInputEvents()
            }
        }

        companion object {
            private val in_alwaysRun: idCVar = idCVar(
                "in_alwaysRun",
                "0",
                CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_ARCHIVE or CVarSystem.CVAR_BOOL,
                "always run  = new idCVar(reverse _speed button) - only in MP"
            )
            private val in_angleSpeedKey: idCVar = idCVar(
                "in_anglespeedkey",
                "1.5",
                CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_ARCHIVE or CVarSystem.CVAR_FLOAT,
                "angle change scale when holding down _speed button"
            )
            private val in_freeLook: idCVar = idCVar(
                "in_freeLook",
                "1",
                CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_ARCHIVE or CVarSystem.CVAR_BOOL,
                "look around with mouse  = new idCVar(reverse _mlook button)"
            )
            private val in_pitchSpeed: idCVar = idCVar(
                "in_pitchspeed",
                "140",
                CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_ARCHIVE or CVarSystem.CVAR_FLOAT,
                "pitch change speed when holding down look _lookUp or _lookDown button"
            )
            private val in_toggleCrouch: idCVar = idCVar(
                "in_toggleCrouch",
                "0",
                CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_ARCHIVE or CVarSystem.CVAR_BOOL,
                "pressing _movedown button toggles player crouching/standing"
            )
            private val in_toggleRun: idCVar = idCVar(
                "in_toggleRun",
                "0",
                CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_ARCHIVE or CVarSystem.CVAR_BOOL,
                "pressing _speed button toggles run on/off - only in MP"
            )
            private val in_toggleZoom: idCVar = idCVar(
                "in_toggleZoom",
                "0",
                CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_ARCHIVE or CVarSystem.CVAR_BOOL,
                "pressing _zoom button toggles zoom on/off"
            )

            //
            private val in_yawSpeed: idCVar = idCVar(
                "in_yawspeed",
                "140",
                CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_ARCHIVE or CVarSystem.CVAR_FLOAT,
                "yaw change speed when holding down _left or _right button"
            )
            private val m_pitch: idCVar = idCVar(
                "m_pitch",
                "0.022",
                CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_ARCHIVE or CVarSystem.CVAR_FLOAT,
                "mouse pitch scale"
            )
            private val m_showMouseRate: idCVar =
                idCVar("m_showMouseRate", "0", CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_BOOL, "shows mouse movement")
            private val m_smooth: idCVar = idCVar(
                "m_smooth",
                "1",
                CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_ARCHIVE or CVarSystem.CVAR_INTEGER,
                "number of samples blended for mouse viewing",
                1.0f,
                8.0f,
                ArgCompletion_Integer(1, 8)
            )
            private val m_strafeScale: idCVar = idCVar(
                "m_strafeScale",
                "6.25",
                CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_ARCHIVE or CVarSystem.CVAR_FLOAT,
                "mouse strafe movement scale"
            )
            private val m_strafeSmooth: idCVar = idCVar(
                "m_strafeSmooth",
                "4",
                CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_ARCHIVE or CVarSystem.CVAR_INTEGER,
                "number of samples blended for mouse moving",
                1.0f,
                8.0f,
                ArgCompletion_Integer(1, 8)
            )
            private val m_yaw: idCVar = idCVar(
                "m_yaw",
                "0.022",
                CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_ARCHIVE or CVarSystem.CVAR_FLOAT,
                "mouse yaw scale"
            )
            private val sensitivity: idCVar = idCVar(
                "sensitivity",
                "5",
                CVarSystem.CVAR_SYSTEM or CVarSystem.CVAR_ARCHIVE or CVarSystem.CVAR_FLOAT,
                "mouse view sensitivity"
            )
            var history: Array<DoubleArray> = Array(8) { DoubleArray(2) }
            var historyCounter = 0
        }

        init {
            toggled_crouch = buttonState_t()
            toggled_run = buttonState_t()
            toggled_zoom = buttonState_t()
            toggled_run.on = TempDump.btoi(in_alwaysRun.GetBool())
            viewangles.set(idVec3()) //ClearAngles();
            cmd = usercmd_t()
            Clear()
            keyboardCallback = KeyboardCallback()
            mouseCursorCallback = MouseCursorCallback()
            mouseScrollCallback = MouseScrollCallback()
            mouseButtonCallback = MouseButtonCallback()
        }
    }
}
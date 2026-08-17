package neo.ui

import neo.framework.DemoFile.idDemoFile
import neo.framework.File_h.idFile
import neo.idlib.Dict_h.idDict
import neo.idlib.Text.Str.idStr
import neo.idlib.containers.CBool
import neo.sys.sysEvent_s
import neo.ui.ListGUI.idListGUI
import neo.ui.UserInterface.idUserInterface.idUserInterfaceManager
import neo.ui.UserInterfaceLocal.idUserInterfaceManagerLocal

object UserInterface {
    var uiManagerLocal = idUserInterfaceManagerLocal()


    var uiManager: idUserInterfaceManager = uiManagerLocal
    fun setUiManagers(uiManager: idUserInterfaceManager) {
        uiManagerLocal = uiManager as idUserInterfaceManagerLocal
        UserInterface.uiManager = uiManagerLocal
    }

    /*
     ===============================================================================

     Draws an interactive 2D surface.
     Used for all user interaction with the game.

     ===============================================================================
     */
    abstract class idUserInterface {
        // virtual						~idUserInterface() {};
        // Returns the name of the gui.
        abstract fun Name(): String

        // Returns a comment on the gui.
        abstract fun Comment(): String?

        // Returns true if the gui is interactive.
        abstract fun IsInteractive(): Boolean
        abstract fun IsUniqued(): Boolean
        abstract fun SetUniqued(b: Boolean)

        // returns false if it failed to load
        abstract fun InitFromFile(qpath: String?, rebuild: Boolean /*= true*/, cache: Boolean /*= true*/): Boolean

        fun InitFromFile(qpath: String?, rebuild: Boolean = true /*= true*/): Boolean {
            return InitFromFile(qpath, rebuild, true)
        }

        fun InitFromFile(qpath: idStr): Boolean {
            return InitFromFile(qpath.toString(), true)
        }

        // handles an event, can return an action string, the caller interprets
        // any return and acts accordingly
        abstract fun HandleEvent(event: sysEvent_s, time: Int, updateVisuals: CBool? /*= NULL*/): String?
        fun HandleEvent(event: sysEvent_s, time: Int): String? {
            return HandleEvent(event, time, null)
        }

        // handles a named event
        abstract fun HandleNamedEvent(eventName: String?)

        // repaints the ui
        abstract fun Redraw(time: Int)

        // repaints the cursor
        abstract fun DrawCursor()

        // Provides read access to the idDict that holds this gui's state.
        abstract fun State(): idDict

        // Removes a gui state variable
        abstract fun DeleteStateVar(varName: String?)

        // Sets a gui state variable.
        abstract fun SetStateString(varName: String?, value: String?)
        abstract fun SetStateBool(varName: String?, value: Boolean)
        abstract fun SetStateInt(varName: String?, value: Int)
        abstract fun SetStateFloat(varName: String?, value: Float)

        // Gets a gui state variable
        abstract fun GetStateString(varName: String?, defaultString: String? /*= ""*/): String?
        fun GetStateString(varName: String?): String? {
            return GetStateString(varName, "")
        }

        abstract fun GetStateboolean(varName: String?, defaultString: String /*= "0"*/): Boolean
        fun GetStateboolean(varName: String?): Boolean {
            return GetStateboolean(varName, "0")
        }

        abstract fun GetStateInt(varName: String?, defaultString: String? /*= "0"*/): Int
        fun GetStateInt(varName: String?): Int {
            return GetStateInt(varName, "0")
        }

        abstract fun GetStateFloat(varName: String?, defaultString: String? /*= "0"*/): Float
        fun GetStateFloat(varName: String?): Float {
            return GetStateFloat(varName, "0")
        }

        // The state has changed and the gui needs to update from the state idDict.
        abstract fun StateChanged(time: Int, redraw: Boolean /*= false*/)
        fun StateChanged(time: Int) {
            StateChanged(time, false)
        }

        // Activated the gui.
        abstract fun Activate(activate: Boolean, time: Int): String

        // Triggers the gui and runs the onTrigger scripts.
        abstract fun Trigger(time: Int)
        abstract fun ReadFromDemoFile(f: idDemoFile)
        abstract fun WriteToDemoFile(f: idDemoFile)
        abstract fun WriteToSaveGame(savefile: idFile): Boolean
        abstract fun ReadFromSaveGame(savefile: idFile): Boolean
        abstract fun SetKeyBindingNames()
        abstract fun SetCursor(x: Float, y: Float)
        abstract fun CursorX(): Float
        abstract fun CursorY(): Float
        abstract fun oSet(FindGui: idUserInterface?)
        abstract class idUserInterfaceManager {
            // virtual 						~idUserInterfaceManager( void ) {};
            abstract fun Init()
            abstract fun Shutdown()
            abstract fun Touch(name: String?)
            abstract fun WritePrecacheCommands(f: idFile)

            // Sets the size for 640x480 adjustment.
            abstract fun SetSize(width: Float, height: Float)
            abstract fun BeginLevelLoad()
            abstract fun EndLevelLoad()

            // Reloads changed guis, or all guis.
            abstract fun Reload(all: Boolean)

            // lists all guis
            abstract fun ListGuis()

            // Returns true if gui exists.
            abstract fun CheckGui(qpath: String?): Boolean

            // Allocates a new gui.
            abstract fun Alloc(): idUserInterface

            // De-allocates a gui.. ONLY USE FOR PRECACHING
            abstract fun DeAlloc(gui: idUserInterface?)

            // Returns NULL if gui by that name does not exist.
            abstract fun FindGui(
                qpath: String?,
                autoLoad: Boolean /*= false*/,
                needUnique: Boolean /*= false*/,
                forceUnique: Boolean /*= false*/
            ): idUserInterface?


            fun FindGui(
                qpath: String?, autoLoad: Boolean = false /*= false*/, needUnique: Boolean = false /*= false*/
            ): idUserInterface? {
                return FindGui(qpath, autoLoad, needUnique, false)
            }

            // Returns NULL if gui by that name does not exist.
            abstract fun FindDemoGui(qpath: String?): idUserInterface?

            // Allocates a new GUI list handler
            abstract fun AllocListGUI(): idListGUI

            // De-allocates a list gui
            abstract fun FreeListGUI(listgui: idListGUI?)
        }
    }
}

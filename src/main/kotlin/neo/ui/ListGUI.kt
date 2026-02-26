package neo.ui

import neo.idlib.Text.Str.idStr
import neo.idlib.containers.List.idList
import neo.ui.UserInterface.idUserInterface

class ListGUI {
    /*
     ===============================================================================

     feed data to a listDef
     each item has an id and a display string

     ===============================================================================
     */
    abstract class idListGUI : idList<idStr?>() {
        //TODO:what kind of impact does this inheritance which is farther inherited by ListGUILocal have!?
        // virtual				~idListGUI() { }
        abstract fun Config(pGUI: idUserInterface?, name: String?)
        abstract fun Add(id: Int, s: idStr?)

        // use the element count as index for the ids
        abstract fun Push(s: idStr?)
        abstract fun Del(id: Int): Boolean

        //        public abstract void Clear();
        //
        //        public abstract int Num();
        //        
        abstract fun GetSelection(
            s: Array<String?>?,
            size: Int,
            sel: Int /*= 0*/
        ): Int // returns the id, not the list index (or -1)

        fun GetSelection(s: Array<String?>?, size: Int): Int {
            return GetSelection(s, size, 0)
        }

        abstract fun SetSelection(sel: Int)
        abstract fun GetNumSelections(): Int
        abstract fun IsConfigured(): Boolean

        // by default, any modification to the list will trigger a full GUI refresh immediately
        abstract fun SetStateChanges(enable: Boolean)
        abstract fun Shutdown()
    }
}

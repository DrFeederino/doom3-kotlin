package neo.framework

import neo.framework.Session_local.fileTIME_T
import neo.idlib.containers.List.cmp_t

class Session_menu {
    /*
     ===============
     idListSaveGameCompare
     ===============
     */
    internal class idListSaveGameCompare : cmp_t<fileTIME_T> {
        /**
         * Ordinary sort, but with **null** objects at the end.
         */
        override fun compare(a: fileTIME_T?, b: fileTIME_T?): Int {
            //nulls should come at the end
            if (null == a) {
                return if (null == b) {
                    0
                } else 1
            }
            return if (null == b) {
                -1
            } else (b.timeStamp - a.timeStamp).toInt()
        }
    }
}
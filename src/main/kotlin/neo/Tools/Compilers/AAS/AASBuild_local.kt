package neo.Tools.Compilers.AAS

import neo.idlib.math.idPlane

class AASBuild_local {
    //===============================================================
    //
    //	idAASBuild
    //
    //===============================================================
    internal class aasProcNode_s {
        var children: IntArray = IntArray(2) // negative numbers are (-1 - areaNumber), 0 = solid
        val plane: idPlane = idPlane()
    }
}
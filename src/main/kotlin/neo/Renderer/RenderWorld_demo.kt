package neo.Renderer

import neo.idlib.containers.CInt

class RenderWorld_demo {
    //#define WRITE_GUIS
    internal class demoHeader_t {
        var mapname: CharArray = CharArray(256)
        var sizeofRenderEntity: CInt = CInt()
        var sizeofRenderLight: CInt = CInt()
        var version: CInt = CInt()
    }
}

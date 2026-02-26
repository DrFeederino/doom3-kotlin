package neo.Game.GameSys

object NoGameTypeInfo {
    var classTypeInfo: Array<classTypeInfo_t> = arrayOf(
        classTypeInfo_t(null, null, 0, null)
    )
    var constantInfo: Array<constantInfo_t> = arrayOf(
        constantInfo_t(null, null, null)
    )
    var enumTypeInfo: Array<enumTypeInfo_t> = arrayOf(
        enumTypeInfo_t(null, null)
    )

    /*
     ===================================================================================

     This file has been generated with the Type Info Generator v1.0f (c) 2004 id Software

     ===================================================================================
     */
    class constantInfo_t(var name: String?, var type: String?, var value: String?)
    class enumValueInfo_t(var name: String?, var value: Int)
    class enumTypeInfo_t(var typeName: String?, var values: Array<enumValueInfo_t?>?)
    class classVariableInfo_t {
        var name: String? = null
        var offset = 0
        var size = 0
        var type: String? = null
    }

    class classTypeInfo_t(
        var typeName: String?,
        var superType: String?,
        var size: Int,
        var variables: Array<classVariableInfo_t>?
    )
}
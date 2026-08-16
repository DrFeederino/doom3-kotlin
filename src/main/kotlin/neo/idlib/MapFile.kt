package neo.idlib

import neo.framework.File_h.idFile
import neo.idlib.Dict_h.idDict
import neo.idlib.Text.Lexer
import neo.idlib.Text.Lexer.idLexer
import neo.idlib.Text.Str
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Token
import neo.idlib.Text.Token.idToken
import neo.idlib.containers.List.idList
import neo.idlib.geometry.DrawVert.idDrawVert
import neo.idlib.math.idMath
import neo.idlib.math.idPlane
import neo.idlib.math.idVec3
import neo.idlib.math.idVec4
import java.util.regex.Pattern
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

object MapFile {
    /*
     ===============================================================================

     Reads or writes the contents of .map files into a standard internal
     format, which can then be moved into private formats for collision
     detection, map processing, or editor use.

     No validation (duplicate planes, null area brushes, etc) is performed.
     There are no limits to the number of any of the elements in maps.
     The order of entities, brushes, and sides is maintained.

     ===============================================================================
     */
    const val CURRENT_MAP_VERSION = 2
    const val DEFAULT_CURVE_MAX_ERROR = 4.0f
    const val DEFAULT_CURVE_MAX_ERROR_CD = 24.0f
    const val DEFAULT_CURVE_MAX_LENGTH = -1.0f
    const val DEFAULT_CURVE_MAX_LENGTH_CD = -1.0f
    const val DEFAULT_CURVE_SUBDIVISION = 4
    const val OLD_MAP_VERSION = 1

    /*
     =================
     ComputeAxisBase

     WARNING : special case behaviour of atan2(y,x) <-> atan(y/x) might not be the same everywhere when x == 0
     rotation by (0,RotY,RotZ) assigns X to normal
     =================
     */
    fun ComputeAxisBase(normal: idVec3, texS: idVec3, texT: idVec3) {
        val RotY: Float
        val RotZ: Float
        val n = idVec3()

        // do some cleaning
        n[0] = if (abs(normal[0]) < 1e-6f) 0.0f else normal[0]
        n[1] = if (abs(normal[1]) < 1e-6f) 0.0f else normal[1]
        n[2] = if (abs(normal[2]) < 1e-6f) 0.0f else normal[2]
        RotY = -atan2(n[2], idMath.Sqrt(n[1] * n[1] + n[0] * n[0]))
        RotZ = atan2(n[1], n[0])
        // rotate (0,1,0) and (0,0,1) to compute texS and texT
        texS[0] = -sin(RotZ)
        texS[1] = cos(RotZ)
        texS[2] = 0.0f
        // the texT vector is along -Z ( T texture coorinates axis )
        texT[0] = (-sin(RotY) * cos(RotZ))
        texT[1] = (-sin(RotY) * sin(RotZ))
        texT[2] = -cos(RotY)
    }

    private fun FloatCRC(f: Float): Long {
        return Integer.toUnsignedLong(f.toBits().toInt())
    }

    private fun StringCRC(str: String): Long {
        var i: Int
        var crc: Long
        crc = 0
        i = 0
        while (i < str.length) {
            crc = crc xor ((str[i].code shl (i and 3)).toLong())
            i++
        }
        return crc
    }

    open class idMapPrimitive {
        //
        //
        val epairs: idDict = idDict()
        var type: Int = TYPE_INVALID

        //public	virtual					~idMapPrimitive( void ) { }
        fun GetType(): Int {
            return type
        }

        companion object {
            //	enum { TYPE_INVALID = -1, TYPE_BRUSH, TYPE_PATCH };
            const val TYPE_INVALID = -1
            const val TYPE_BRUSH = 0
            const val TYPE_PATCH = 1
        }

    }

    class idMapBrushSide {
        //	friend class idMapBrush;
        val origin: idVec3 = idVec3()
        val plane: idPlane = idPlane()
        val texMat: Array<idVec3> = idVec3.generateArray(2)
        val material: idStr = idStr()

        //public							~idMapBrushSide( void ) { }
        fun GetMaterial(): idStr {
            return material
        }

        fun SetMaterial(p: String) {
            material.set(p)
        }

        fun GetPlane(): idPlane {
            return plane
        }

        fun SetPlane(p: idPlane) {
            plane.set(p)
        }

        fun SetTextureMatrix(mat: Array<idVec3>) {
            texMat[0].set(mat[0])
            texMat[1].set(mat[1])
        }

        fun GetTextureMatrix(mat1: Array<idVec3>, mat2: Array<idVec3>) {
            mat1[0].set(texMat[0])
            mat2[0].set(texMat[1])
        }

        fun GetTextureVectors(v: Array<idVec4>) {
            var i: Int
            val texX = idVec3()
            val texY = idVec3()
            ComputeAxisBase(plane.Normal(), texX, texY)
            i = 0
            while (i < 2) {
                v[i][0] = texX[0] * texMat[i][0] + texY[0] * texMat[i][1]
                v[i][1] = texX[1] * texMat[i][0] + texY[1] * texMat[i][1]
                v[i][2] = texX[2] * texMat[i][0] + texY[2] * texMat[i][1]
                v[i][3] = texMat[i][2] + origin.times(v[i].ToVec3())
                i++
            }
        }

    }

    class idMapBrush : idMapPrimitive() {
        protected val sides: idList<idMapBrushSide>
        protected var numSides = 0
        fun Write(fp: idFile, primitiveNum: Int, origin: idVec3): Boolean {
            var i: Int
            var side: idMapBrushSide
            fp.WriteFloatString("// primitive %d\n{\n brushDef3\n {\n", primitiveNum)

            // write brush epairs
            i = 0
            while (i < epairs.GetNumKeyVals()) {
                fp.WriteFloatString(
                    "  \"%s\" \"%s\"\n",
                    epairs.GetKeyVal(i)!!.GetKey(),
                    epairs.GetKeyVal(i)!!.GetValue()
                )
                i++
            }

            // write brush sides
            i = 0
            while (i < GetNumSides()) {
                side = GetSide(i)
                fp.WriteFloatString(
                    "  ( %f %f %f %f ) ",
                    side.plane[0],
                    side.plane[1],
                    side.plane[2],
                    side.plane[3]
                )
                fp.WriteFloatString(
                    "( ( %f %f %f ) ( %f %f %f ) ) \"%s\" 0 0 0\n",
                    side.texMat[0][0], side.texMat[0][1], side.texMat[0][2],
                    side.texMat[1][0], side.texMat[1][1], side.texMat[1][2],
                    side.material
                )
                i++
            }
            fp.WriteFloatString(" }\n}\n")
            return true
        }

        fun GetNumSides(): Int {
            return sides.Num()
        }

        fun AddSide(side: idMapBrushSide): Int {
            return sides.Append(side)
        }

        fun GetSide(i: Int): idMapBrushSide {
            return sides[i]
        }

        fun GetGeometryCRC(): Long {
            var i: Int
            var j: Int
            var mapSide: idMapBrushSide
            var crc: Long
            crc = 0
            i = 0
            while (i < GetNumSides()) {
                mapSide = GetSide(i)
                j = 0
                while (j < 4) {
                    crc = crc xor FloatCRC(mapSide.GetPlane()[j])
                    j++
                }
                crc = crc xor StringCRC(mapSide.GetMaterial().toString())
                i++
            }
            return crc
        }

        companion object {
            @Throws(idException::class)
            fun Parse(src: idLexer, origin: idVec3, newFormat: Boolean, version: Float): idMapBrush? {
                var i: Int
                val planepts: Array<idVec3> = idVec3.generateArray(3)
                val token = idToken()
                val sides = idList<idMapBrushSide>()
                var side: idMapBrushSide
                val epairs = idDict()
                if (!src.ExpectTokenString("{")) {
                    return null
                }
                do {
                    if (!src.ReadToken(token)) {
                        src.Error("idMapBrush::Parse: unexpected EOF")
                        sides.DeleteContents(true)
                        return null
                    }
                    if (token.toString() == "}") {
                        break
                    }

                    // here we may have to jump over brush epairs ( only used in editor )
                    do {
                        // if token is a brace
                        if (token.toString() == "(") {
                            break
                        }
                        // the token should be a key string for a key/value pair
                        if (token.type != Token.TT_STRING) {
                            src.Error("idMapBrush::Parse: unexpected %s, expected ( or epair key string", token)
                            sides.DeleteContents(true)
                            return null
                        }
                        val key: idStr = token
                        if (!src.ReadTokenOnLine(token) || token.type != Token.TT_STRING) {
                            src.Error("idMapBrush::Parse: expected epair value string not found")
                            sides.DeleteContents(true)
                            return null
                        }
                        epairs.Set(key, token)

                        // try to read the next key
                        if (!src.ReadToken(token)) {
                            src.Error("idMapBrush::Parse: unexpected EOF")
                            sides.DeleteContents(true)
                            return null
                        }
                    } while (true)
                    src.UnreadToken(token)
                    side = idMapBrushSide()
                    sides.Append(side)
                    if (newFormat) {
                        if (!src.Parse1DMatrix(4, side.plane)) {
                            src.Error("idMapBrush::Parse: unable to read brush side plane definition")
                            sides.DeleteContents(true)
                            return null
                        }
                    } else {
                        // read the three point plane definition
                        if (!src.Parse1DMatrix(3, planepts[0])
                            || !src.Parse1DMatrix(3, planepts[1])
                            || !src.Parse1DMatrix(3, planepts[2])
                        ) {
                            src.Error("idMapBrush::Parse: unable to read brush side plane definition")
                            sides.DeleteContents(true)
                            return null
                        }
                        planepts[0].minusAssign(origin)
                        planepts[1].minusAssign(origin)
                        planepts[2].minusAssign(origin)
                        side.plane.FromPoints(planepts[0], planepts[1], planepts[2])
                    }

                    // read the texture matrix
                    // this is odd, because the texmat is 2D relative to default planar texture axis
                    if (!src.Parse2DMatrix(2, 3, side.texMat)) {
                        src.Error("idMapBrush::Parse: unable to read brush side texture matrix")
                        sides.DeleteContents(true)
                        return null
                    }
                    side.origin.set(origin)

                    // read the material
                    if (!src.ReadTokenOnLine(token)) {
                        src.Error("idMapBrush::Parse: unable to read brush side material")
                        sides.DeleteContents(true)
                        return null
                    }

                    // we had an implicit 'textures/' in the old format...
                    if (version < 2.0f) {
                        side.material.set("textures/$token")
                    } else {
                        side.material.set(token)
                    }

                    // Q2 allowed override of default flags and values, but we don't any more
                    if (src.ReadTokenOnLine(token)) {
                        if (src.ReadTokenOnLine(token)) {
                            if (src.ReadTokenOnLine(token)) {
                            }
                        }
                    }
                } while (true)
                if (!src.ExpectTokenString("}")) {
                    sides.DeleteContents(true)
                    return null
                }
                val brush = idMapBrush()
                i = 0
                while (i < sides.Num()) {
                    brush.AddSide(sides[i])
                    i++
                }
                brush.epairs.set(epairs)
                return brush
            }

            @Throws(idException::class)
            fun ParseQ3(src: idLexer, origin: idVec3): idMapBrush? {
                var i: Int
                val planepts = idVec3.generateArray(3)
                val token = idToken()
                val sides = idList<idMapBrushSide>()
                var side: idMapBrushSide
                val epairs = idDict()
                do {
                    if (src.CheckTokenString("}")) {
                        break
                    }
                    side = idMapBrushSide()
                    sides.Append(side)

                    // read the three point plane definition
                    if (!src.Parse1DMatrix(3, planepts[0])
                        || !src.Parse1DMatrix(3, planepts[1])
                        || !src.Parse1DMatrix(3, planepts[2])
                    ) {
                        src.Error("idMapBrush::ParseQ3: unable to read brush side plane definition")
                        sides.DeleteContents(true)
                        return null
                    }
                    planepts[0].minusAssign(origin)
                    planepts[1].minusAssign(origin)
                    planepts[2].minusAssign(origin)
                    side.plane.FromPoints(planepts[0], planepts[1], planepts[2])

                    // read the material
                    if (!src.ReadTokenOnLine(token)) {
                        src.Error("idMapBrush::ParseQ3: unable to read brush side material")
                        sides.DeleteContents(true)
                        return null
                    }

                    // we have an implicit 'textures/' in the old format
                    side.material.set("textures/$token")

                    // skip the texture shift, rotate and scale
                    src.ParseInt()
                    src.ParseInt()
                    src.ParseInt()
                    src.ParseFloat()
                    src.ParseFloat()
                    side.texMat[0].set(idVec3(0.03125f, 0.0f, 0.0f))
                    side.texMat[1].set(idVec3(0.0f, 0.03125f, 0.0f))
                    side.origin.set(origin)

                    // Q2 allowed override of default flags and values, but we don't any more
                    if (src.ReadTokenOnLine(token)) {
                        if (src.ReadTokenOnLine(token)) {
                            if (src.ReadTokenOnLine(token)) {
                            }
                        }
                    }
                } while (true)
                val brush = idMapBrush()
                i = 0
                while (i < sides.Num()) {
                    brush.AddSide(sides[i])
                    i++
                }
                brush.epairs.set(epairs)
                return brush
            }
        }

        //
        //
        init {
            type = TYPE_BRUSH
            sides = idList()
            sides.Resize(8, 4)
        }
    }

    class idMapPatch : idMapPrimitive {
        protected val verts: idList<idDrawVert> = idList() // vertices
        protected var expanded // true if vertices are spaced out
                = false
        protected var explicitSubdivisions = false
        protected var height // height of patch
                = 0
        protected var horzSubdivisions = 0

        //
        //
        protected var material: idStr = idStr()
        protected var maxHeight // maximum height allocated for
                = 0
        protected var maxWidth // maximum width allocated for
                = 0
        protected var vertSubdivisions = 0

        /**
         * i d S u r f a c e_-_P a t c h
         */
        protected var width // width of patch
                = 0

        constructor() {
            type = TYPE_PATCH
            vertSubdivisions = 0
            horzSubdivisions = vertSubdivisions
            explicitSubdivisions = false
            height = 0
            width = height
            maxHeight = 0
            maxWidth = maxHeight
            expanded = false
        }

        constructor(maxPatchWidth: Int, maxPatchHeight: Int) {
            type = TYPE_PATCH
            vertSubdivisions = 0
            horzSubdivisions = vertSubdivisions
            explicitSubdivisions = false
            height = 0
            width = height
            maxWidth = maxPatchWidth
            maxHeight = maxPatchHeight
            verts.SetNum(maxWidth * maxHeight)
            expanded = false
        }

        @Deprecated("")
        constructor(mapPrimitive: idMapPrimitive) {
            epairs.set(mapPrimitive.epairs)
            type = mapPrimitive.type
        }

        fun Write(fp: idFile, primitiveNum: Int, origin: idVec3): Boolean {
            var i: Int
            var j: Int
            var v: idDrawVert
            if (GetExplicitlySubdivided()) {
                fp.WriteFloatString("// primitive %d\n{\n patchDef3\n {\n", primitiveNum)
                fp.WriteFloatString(
                    "  \"%s\"\n  ( %d %d %d %d 0 0 0 )\n",
                    GetMaterial(),
                    GetWidth(),
                    GetHeight(),
                    GetHorzSubdivisions(),
                    GetVertSubdivisions()
                )
            } else {
                fp.WriteFloatString("// primitive %d\n{\n patchDef2\n {\n", primitiveNum)
                fp.WriteFloatString("  \"%s\"\n  ( %d %d 0 0 0 )\n", GetMaterial(), GetWidth(), GetHeight())
            }
            fp.WriteFloatString("  (\n")
            i = 0
            while (i < GetWidth()) {
                fp.WriteFloatString("   ( ")
                j = 0
                while (j < GetHeight()) {
                    v = verts[j * GetWidth() + i]
                    fp.WriteFloatString(
                        " ( %f %f %f %f %f )",
                        v.xyz[0] + origin[0],
                        v.xyz[1] + origin[1],
                        v.xyz[2] + origin[2],
                        v.st[0],
                        v.st[1]
                    )
                    j++
                }
                fp.WriteFloatString(" )\n")
                i++
            }
            fp.WriteFloatString("  )\n }\n}\n")
            return true
        }

        fun GetMaterial(): idStr {
            return material
        }

        fun SetMaterial(p: String) {
            material = idStr(p)
        }

        fun GetHorzSubdivisions(): Int {
            return horzSubdivisions
        }

        fun GetVertSubdivisions(): Int {
            return vertSubdivisions
        }

        fun GetExplicitlySubdivided(): Boolean {
            return explicitSubdivisions
        }

        fun SetHorzSubdivisions(n: Int) {
            horzSubdivisions = n
        }

        fun SetVertSubdivisions(n: Int) {
            vertSubdivisions = n
        }

        fun SetExplicitlySubdivided(b: Boolean) {
            explicitSubdivisions = b
        }

        fun GetGeometryCRC(): Long {
            var i: Int
            var j: Int
            var crc: Long
            crc = (GetHorzSubdivisions() xor GetVertSubdivisions()).toLong()
            i = 0
            while (i < GetWidth()) {
                j = 0
                while (j < GetHeight()) {
                    crc = crc xor FloatCRC(verts[j * GetWidth() + i].xyz.x)
                    crc = crc xor FloatCRC(verts[j * GetWidth() + i].xyz.y)
                    crc = crc xor FloatCRC(verts[j * GetWidth() + i].xyz.z)
                    j++
                }
                i++
            }
            crc = crc xor StringCRC(GetMaterial().toString())
            return crc
        }

        fun GetMaxWidth(): Int {
            return maxWidth
        }

        fun GetMaxHeight(): Int {
            return maxHeight
        }

        fun GetVert(index: Int): idDrawVert {
            return verts[index]
        }

        fun GetWidth(): Int {
            return width
        }

        fun GetHeight(): Int {
            return height
        }

        @Throws(idException::class)
        fun SetSize(patchWidth: Int, patchHeight: Int) {
            if (patchWidth < 1 || patchWidth > maxWidth) {
                idLib.common.FatalError("idSurface_Patch::SetSize: invalid patchWidth")
            }
            if (patchHeight < 1 || patchHeight > maxHeight) {
                idLib.common.FatalError("idSurface_Patch::SetSize: invalid patchHeight")
            }
            width = patchWidth
            height = patchHeight
            verts.SetNum(width * height, false)
        }

        companion object {
            //public							~idMapPatch( void ) { }
            //public	static idMapPatch *		Parse( idLexer &src, const idVec3 &origin, bool patchDef3 = true, float version = CURRENT_MAP_VERSION );
            @Throws(idException::class)
            fun Parse(src: idLexer, origin: idVec3, patchDef3: Boolean, version: Float): idMapPatch? {
                val info = FloatArray(7)
                var vert: idDrawVert
                val token = idToken()
                var i: Int
                var j: Int
                if (!src.ExpectTokenString("{")) {
                    return null
                }

                // read the material (we had an implicit 'textures/' in the old format...)
                if (!src.ReadToken(token)) {
                    src.Error("idMapPatch::Parse: unexpected EOF")
                    return null
                }

                // Parse it
                if (patchDef3) {
                    if (!src.Parse1DMatrix(7, info)) {
                        src.Error("idMapPatch::Parse: unable to Parse patchDef3 info")
                        return null
                    }
                } else {
                    if (!src.Parse1DMatrix(5, info)) {
                        src.Error("idMapPatch::Parse: unable to parse patchDef2 info")
                        return null
                    }
                }
                val patch = idMapPatch(info[0].toInt(), info[1].toInt())
                patch.SetSize(info[0].toInt(), info[1].toInt())
                if (version < 2.0f) {
                    patch.SetMaterial("textures/$token")
                } else {
                    patch.SetMaterial("" + token) //TODO:accept Objects, and cast within
                }
                if (patchDef3) {
                    patch.SetHorzSubdivisions(info[2].toInt())
                    patch.SetVertSubdivisions(info[3].toInt())
                    patch.SetExplicitlySubdivided(true)
                }
                if (patch.GetWidth() < 0 || patch.GetHeight() < 0) {
                    src.Error("idMapPatch::Parse: bad size")
                    return null
                }

                // these were written out in the wrong order, IMHO
                if (!src.ExpectTokenString("(")) {
                    src.Error("idMapPatch::Parse: bad patch vertex data")
                    return null
                }
                j = 0
                while (j < patch.GetWidth()) {
                    if (!src.ExpectTokenString("(")) {
                        src.Error("idMapPatch::Parse: bad vertex row data")
                        return null
                    }
                    i = 0
                    while (i < patch.GetHeight()) {
                        val v = FloatArray(5)
                        if (!src.Parse1DMatrix(5, v)) {
                            src.Error("idMapPatch::Parse: bad vertex column data")
                            return null
                        }

//                    vert = patch.oGet(i * patch.GetWidth() + j);

//                    vert = patch.oGet(i * patch.GetWidth() + j);
                        vert = patch.verts.set(i * patch.GetWidth() + j, idDrawVert())
                        vert.xyz[0] = v[0] - origin[0]
                        vert.xyz[1] = v[1] - origin[1]
                        vert.xyz[2] = v[2] - origin[2]
                        vert.st[0] = v[3]
                        vert.st[1] = v[4]
                        i++
                    }
                    if (!src.ExpectTokenString(")")) {
                        src.Error("idMapPatch::Parse: unable to parse patch control points")
                        return null
                    }
                    j++
                }
                if (!src.ExpectTokenString(")")) {
                    src.Error("idMapPatch::Parse: unable to parse patch control points, no closure")
                    return null
                }

                // read any key/value pairs
                while (src.ReadToken(token)) {
                    if (token.toString() == "}") {
                        src.ExpectTokenString("}")
                        break
                    }
                    if (token.type == Token.TT_STRING) {
                        val key: idStr = token
                        src.ExpectTokenType(Token.TT_STRING, 0, token)
                        patch.epairs.Set(key.toString(), token.toString())
                    }
                }
                return patch
            }
        }
    }

    class idMapEntity {
        //	friend class			idMapFile;
        //
        //
        //
        val primitives: idList<idMapPrimitive>
        var epairs: idDict

        @Throws(idException::class)
        fun Write(fp: idFile, entityNum: Int): Boolean {
            var i: Int
            var mapPrim: idMapPrimitive
            val origin = idVec3()
            fp.WriteFloatString("// entity %d\n{\n", entityNum)

            // write entity epairs
            i = 0
            while (i < epairs.GetNumKeyVals()) {
                fp.WriteFloatString(
                    "\"%s\" \"%s\"\n",
                    epairs.GetKeyVal(i)!!.GetKey(),
                    epairs.GetKeyVal(i)!!.GetValue()
                )
                i++
            }
            epairs.GetVector("origin", "0 0 0", origin)

            // write pritimives
            i = 0
            while (i < GetNumPrimitives()) {
                mapPrim = GetPrimitive(i)
                when (mapPrim.GetType()) {
                    idMapPrimitive.TYPE_BRUSH -> (mapPrim as idMapBrush).Write(fp, i, origin)
                    idMapPrimitive.TYPE_PATCH -> (mapPrim as idMapPatch).Write(fp, i, origin)
                }
                i++
            }
            fp.WriteFloatString("}\n")
            return true
        }

        fun GetNumPrimitives(): Int {
            return primitives.Num()
        }

        fun GetPrimitive(i: Int): idMapPrimitive {
            return primitives[i]
        }

        fun AddPrimitive(p: idMapPrimitive) {
            primitives.Append(p)
        }

        fun GetGeometryCRC(): Long {
            var i: Int
            var crc: Long
            var mapPrim: idMapPrimitive
            crc = 0L
            i = 0
            while (i < GetNumPrimitives()) {
                mapPrim = GetPrimitive(i)
                when (mapPrim.GetType()) {
                    idMapPrimitive.TYPE_BRUSH -> crc = crc xor (mapPrim as idMapBrush).GetGeometryCRC()
                    idMapPrimitive.TYPE_PATCH -> crc = crc xor (mapPrim as idMapPatch).GetGeometryCRC()
                }
                i++
            }
            return crc
        }

        fun RemovePrimitiveData() {
            primitives.DeleteContents(true)
        }

        companion object {
            //public							~idMapEntity( void ) { primitives.DeleteContents( true ); }
            //public	static idMapEntity *	Parse( idLexer &src, bool worldSpawn = false, float version = CURRENT_MAP_VERSION );
            @Throws(idException::class)
            fun Parse(src: idLexer, worldSpawn: Boolean, version: Float): idMapEntity? {
                val token = idToken()
                val mapEnt: idMapEntity
                var mapPatch: idMapPatch?
                var mapBrush: idMapBrush?
                var worldent: Boolean
                val origin = idVec3()
                var v1: Float
                var v2: Float
                var v3: Float
                if (!src.ReadToken(token)) {
                    return null
                }
                if (token.toString() != "{") {
                    src.Error("idMapEntity::Parse: { not found, found %s", token /*c_str()*/)
                    return null
                }
                mapEnt = idMapEntity()
                if (worldSpawn) {
                    mapEnt.primitives.Resize(1024, 256)
                }
                origin.Zero()
                worldent = false
                do {
                    if (!src.ReadToken(token)) {
                        src.Error("idMapEntity::Parse: EOF without closing brace")
                        return null
                    }
                    if (token.toString() == "}") {
                        break
                    }
                    if (token.toString() == "{") {
                        // parse a brush or patch
                        if (!src.ReadToken(token)) {
                            src.Error("idMapEntity::Parse: unexpected EOF")
                            return null
                        }
                        if (worldent) {
                            origin.Zero()
                        }

                        // if is it a brush: brush, brushDef, brushDef2, brushDef3
                        if (token.Icmpn("brush", 5) == 0) {
                            mapBrush = idMapBrush.Parse(
                                src,
                                origin,
                                0 == token.Icmp("brushDef2") || 0 == token.Icmp("brushDef3"),
                                version
                            )
                            if (null == mapBrush) {
                                return null
                            }
                            mapEnt.AddPrimitive(mapBrush)
                        } // if is it a patch: patchDef2, patchDef3
                        else if (token.Icmpn("patch", 5) == 0) {
                            mapPatch = idMapPatch.Parse(src, origin, 0 == token.Icmp("patchDef3"), version)
                            if (null == mapPatch) {
                                return null
                            }
                            mapEnt.AddPrimitive(mapPatch)
                        } // assume it's a brush in Q3 or older style
                        else {
                            src.UnreadToken(token)
                            mapBrush = idMapBrush.ParseQ3(src, origin)
                            if (null == mapBrush) {
                                return null
                            }
                            mapEnt.AddPrimitive(mapBrush)
                        }
                    } else {
                        var key: idStr
                        var value: idStr

                        // parse a key / value pair
                        key = idStr(token)
                        src.ReadTokenOnLine(token)
                        value = idStr(token)

                        // strip trailing spaces that sometimes get accidentally
                        // added in the editor
                        value.StripTrailingWhitespace()
                        key.StripTrailingWhitespace()
                        mapEnt.epairs.Set(key, value)
                        if (0 == idStr.Icmp(key, "origin")) {
                            // scanf into doubles, then assign, so it is idVec size independent
                            v3 = 0.0f
                            v2 = v3
                            v1 = v2
                            //                        sscanf(value, "%lf %lf %lf",  & v1,  & v2,  & v3);
                            val values: Array<String> = value.toString().split(Pattern.compile("\\s+")).toTypedArray()
                            v1 = values[0].toFloat()
                            origin.x = v1
                            v2 = values[1].toFloat()
                            origin.y = v2
                            v3 = values[2].toFloat()
                            origin.z = v3
                        } else if (0 == idStr.Icmp(key, "classname") && 0 == idStr.Icmp(
                                value,
                                "worldspawn"
                            )
                        ) {
                            worldent = true
                        }
                    }
                } while (true)
                return mapEnt
            }
        }

        //
        //
        init {
            epairs = idDict()
            epairs.SetHashSize(64)
            primitives = idList()
        }
    }

    class idMapFile {
        protected val entities: idList<idMapEntity>
        protected var fileTime: Long
        protected var geometryCRC: Long
        protected var hasPrimitiveData: Boolean
        protected val name: idStr = idStr()
        protected var version: Float

        //public							~idMapFile( void ) { entities.DeleteContents( true ); }
        //
        // filename does not require an extension
        // normally this will use a .reg file instead of a .map file if it exists,
        // which is what the game and dmap want, but the editor will want to always
        // load a .map file

        @Throws(idException::class)
        fun Parse(
            filename: String,
            ignoreRegion: Boolean = false /*= false*/,
            osPath: Boolean = false /*= false*/
        ): Boolean {
            // no string concatenation for epairs and allow path names for materials
            val src =
                idLexer(Lexer.LEXFL_NOSTRINGCONCAT or Lexer.LEXFL_NOSTRINGESCAPECHARS or Lexer.LEXFL_ALLOWPATHNAMES)
            val token = idToken()
            val fullName: idStr
            var mapEnt: idMapEntity?
            var i: Int
            var j: Int
            var k: Int
            name.set(filename)
            name.StripFileExtension()
            fullName = name
            hasPrimitiveData = false
            if (!ignoreRegion) {
                // try loading a .reg file first
                fullName.SetFileExtension("reg")
                src.LoadFile(fullName.toString(), osPath)
            }
            if (!src.IsLoaded()) {
                // now try a .map file
                fullName.SetFileExtension("map")
                src.LoadFile(fullName.toString(), osPath)
                if (!src.IsLoaded()) {
                    // didn't get anything at all
                    return false
                }
            }
            version = OLD_MAP_VERSION.toFloat()
            fileTime = src.GetFileTime()
            entities.DeleteContents(true)
            if (src.CheckTokenString("Version")) {
                src.ReadTokenOnLine(token)
                version = token.GetFloatValue()
            }
            while (true) {
                mapEnt = idMapEntity.Parse(src, entities.Num() == 0, version)
                if (null == mapEnt) {
                    break
                }
                entities.Append(mapEnt)
            }
            SetGeometryCRC()

            // if the map has a worldspawn
            if (entities.Num() != 0) {

                // "removeEntities" "classname" can be set in the worldspawn to remove all entities with the given classname
                var removeEntities = entities[0].epairs.MatchPrefix("removeEntities", null)
                while (removeEntities != null) {
                    RemoveEntities(removeEntities.GetValue().toString())
                    removeEntities = entities[0].epairs.MatchPrefix("removeEntities", removeEntities)
                }

                // "overrideMaterial" "material" can be set in the worldspawn to reset all materials
                val material = idStr()
                if (entities[0].epairs.GetString("overrideMaterial", "", material)) {
                    i = 0
                    while (i < entities.Num()) {
                        mapEnt = entities[i]
                        j = 0
                        while (j < mapEnt.GetNumPrimitives()) {
                            val mapPrimitive = mapEnt.GetPrimitive(j)
                            when (mapPrimitive.GetType()) {
                                idMapPrimitive.TYPE_BRUSH -> {
                                    val mapBrush = mapPrimitive as idMapBrush
                                    k = 0
                                    while (k < mapBrush.GetNumSides()) {
                                        mapBrush.GetSide(k).SetMaterial(material.toString())
                                        k++
                                    }
                                }

                                idMapPrimitive.TYPE_PATCH -> {
                                    (mapPrimitive as idMapPatch).SetMaterial(material.toString())
                                }
                            }
                            j++
                        }
                        i++
                    }
                }

                // force all entities to have a name key/value pair
                if (entities[0].epairs.GetBool("forceEntityNames")) {
                    i = 1
                    while (i < entities.Num()) {
                        mapEnt = entities[i]
                        if (null == mapEnt.epairs.FindKey("name")) {
                            mapEnt.epairs.Set(
                                "name",
                                Str.va("%s%d", mapEnt.epairs.GetString("classname", "forcedName"), i)
                            )
                        }
                        i++
                    }
                }

                // move the primitives of any func_group entities to the worldspawn
                if (entities[0].epairs.GetBool("moveFuncGroups")) {
                    i = 1
                    while (i < entities.Num()) {
                        mapEnt = entities[i]
                        if (idStr.Icmp(mapEnt.epairs.GetString("classname"), "func_group") == 0) {
                            entities[0].primitives.Append(mapEnt.primitives)
                            mapEnt.primitives.Clear()
                        }
                        i++
                    }
                }
            }
            hasPrimitiveData = true
            return true
        }

        @Throws(idException::class)
        fun Parse(filename: idStr): Boolean {
            return Parse(filename.toString())
        }


        @Throws(idException::class)
        fun Write(fileName: String, ext: String, fromBasePath: Boolean = true): Boolean {
            var i: Int
            val qpath: idStr
            val fp: idFile?
            qpath = idStr(fileName)
            qpath.SetFileExtension(ext)
            idLib.common.Printf("writing %s...\n", qpath)
            fp = if (fromBasePath) {
                idLib.fileSystem.OpenFileWrite(qpath.toString(), "fs_devpath")
            } else {
                idLib.fileSystem.OpenExplicitFileWrite(qpath.toString())
            }
            if (null == fp) {
                idLib.common.Warning("Couldn't open %s\n", qpath)
                return false
            }
            fp.WriteFloatString("Version %f\n", CURRENT_MAP_VERSION)
            i = 0
            while (i < entities.Num()) {
                entities[i].Write(fp, i)
                i++
            }
            idLib.fileSystem.CloseFile(fp)
            return true
        }

        // get the number of entities in the map
        fun GetNumEntities(): Int {
            return entities.Num()
        }

        // get the specified entity
        fun GetEntity(i: Int): idMapEntity {
            return entities[i]
        }

        // get the name without file extension
        fun GetName(): String {
            return name.toString()
        }

        fun GetNameStr(): idStr {
            return name
        }

        // get the file time
        fun GetFileTime(): Long {
            return fileTime
        }

        // get CRC for the map geometry
        // texture coordinates and entity key/value pairs are not taken into account
        fun GetGeometryCRC(): Long {
            return geometryCRC
        }

        // returns true if the file on disk changed
        fun NeedsReload(): Boolean {
            if (name.Length() != 0) {
//		ID_TIME_T time = (ID_TIME_T)-1;
                val time = longArrayOf(Long.MAX_VALUE)
                if (idLib.fileSystem.ReadFile(name.toString(), null, time) > 0) {
                    return time[0] > fileTime
                }
            }
            return true
        }

        //
        fun AddEntity(mapentity: idMapEntity): Int {
            return entities.Append(mapentity)
        }

        @Throws(idException::class)
        fun FindEntity(name: String): idMapEntity? {
            for (i in 0 until entities.Num()) {
                val ent = entities[i]
                if (idStr.Icmp(ent.epairs.GetString("name"), name) == 0) {
                    return ent
                }
            }
            return null
        }

        @Throws(idException::class)
        fun FindEntity(name: idStr): idMapEntity? {
            return this.FindEntity(name.toString())
        }

        fun RemoveEntity(mapEnt: idMapEntity) {
            entities.Remove(mapEnt)
            //	delete mapEnt;
        }

        @Throws(idException::class)
        fun RemoveEntities(classname: String) {
            var i = 0
            while (i < entities.Num()) {
                val ent = entities[i]
                if (idStr.Icmp(ent.epairs.GetString("classname"), classname) == 0) {
//			delete entities[i];
                    entities.RemoveIndex(i)
                    i--
                }
                i++
            }
        }

        fun RemoveAllEntities() {
            entities.DeleteContents(true)
            hasPrimitiveData = false
        }

        fun RemovePrimitiveData() {
            for (i in 0 until entities.Num()) {
                val ent = entities[i]
                ent.RemovePrimitiveData()
            }
            hasPrimitiveData = false
        }

        fun HasPrimitiveData(): Boolean {
            return hasPrimitiveData
        }

        private fun SetGeometryCRC() {
            var i: Int
            geometryCRC = 0
            i = 0
            while (i < entities.Num()) {
                geometryCRC = geometryCRC xor entities[i].GetGeometryCRC()
                i++
            }
        }

        //
        //
        init {
            version = CURRENT_MAP_VERSION.toFloat()
            fileTime = 0
            geometryCRC = 0
            entities = idList()
            entities.Resize(1024, 256)
            hasPrimitiveData = false
        }
    }
}
package neo.Tools.Compilers.DMap

import neo.Game.GameEdit
import neo.Renderer.Material
import neo.Renderer.Material.materialCoverage_t
import neo.Renderer.tr_lightrun
import neo.Tools.Compilers.DMap.dmap.mapLight_t
import neo.Tools.Compilers.DMap.dmap.mapTri_s
import neo.Tools.Compilers.DMap.dmap.optimizeGroup_s
import neo.Tools.Compilers.DMap.dmap.primitive_s
import neo.Tools.Compilers.DMap.dmap.side_s
import neo.Tools.Compilers.DMap.dmap.uArea_t
import neo.Tools.Compilers.DMap.dmap.uBrush_t
import neo.Tools.Compilers.DMap.dmap.uEntity_t
import neo.framework.Common
import neo.framework.DeclManager
import neo.idlib.BV.idBounds
import neo.idlib.MapFile
import neo.idlib.MapFile.idMapBrush
import neo.idlib.MapFile.idMapBrushSide
import neo.idlib.MapFile.idMapEntity
import neo.idlib.MapFile.idMapFile
import neo.idlib.MapFile.idMapPatch
import neo.idlib.MapFile.idMapPrimitive
import neo.idlib.Text.Str.idStr
import neo.idlib.containers.CBool
import neo.idlib.geometry.Surface.idSurface
import neo.idlib.geometry.Surface_Patch.idSurface_Patch
import neo.idlib.math.DotProduct
import neo.idlib.math.idPlane
import kotlin.math.floor

object map {
    const val DIST_EPSILON = 0.01f

    /*

     After parsing, there will be a list of entities that each has
     a list of primitives.

     Primitives are either brushes, triangle soups, or model references.

     Curves are tesselated to triangle soups at load time, but model
     references are
     Brushes will have

     brushes, each of which has a side definition.

     */
    const val MAX_BUILD_SIDES = 300
    const val NORMAL_EPSILON = 0.00001f

    // brushes are parsed into a temporary array of sides,
    // which will have duplicates removed before the final brush is allocated
    var buildBrush: uBrush_t = uBrush_t()
    var c_areaportals = 0
    var c_numMapPatches = 0
    var entityPrimitive // to track editor brush numbers
            = 0
    var uEntity: uEntity_t = uEntity_t()

    /*
     ===========
     FindFloatPlane
     ===========
     */

    fun FindFloatPlane(plane: idPlane, fixedDegeneracies: BooleanArray? = null): Int {
        val p = idPlane(plane) // not sure! however why re-declare it?
        val fixed = p.FixDegeneracies(DIST_EPSILON)
        if (fixed && fixedDegeneracies != null) {
            fixedDegeneracies[0] = true
        }
        return dmap.dmapGlobals.mapPlanes.FindPlane(p, NORMAL_EPSILON, DIST_EPSILON)
    }

    /*
     ===========
     SetBrushContents

     The contents on all sides of a brush should be the same
     Sets contentsShader, contents, opaque
     ===========
     */
    fun SetBrushContents(b: uBrush_t) {
        var contents: Int
        var c2: Int
        var s: side_s?
        var i: Int
        var mixed: Boolean
        s = b.sides[0]
        contents = s.material!!.GetContentFlags()
        b.contentShader = s.material
        mixed = false

        // a brush is only opaque if all sides are opaque
        b.opaque = true
        i = 1
        while (i < b.numsides) {
            s = b.sides[i]
            if (null == s.material) {
                i++
                continue
            }
            c2 = s.material!!.GetContentFlags()
            if (c2 != contents) {
                mixed = true
                contents = contents or c2
            }
            if (s.material!!.Coverage() != materialCoverage_t.MC_OPAQUE) {
                b.opaque = false
            }
            i++
        }
        if (contents and Material.CONTENTS_AREAPORTAL != 0) {
            c_areaportals++
        }
        b.contents = contents
    }

    //============================================================================
    /*
     ===============
     FreeBuildBrush
     ===============
     */
    fun FreeBuildBrush() {
        var i: Int
        i = 0
        while (i < buildBrush.numsides) {
            if (buildBrush.sides[i].winding != null) {
//			delete buildBrush.sides[i].winding;
                buildBrush.sides[i].winding = null
            }
            i++
        }
        buildBrush.numsides = 0
    }

    /*
     ===============
     FinishBrush

     Produces a final brush based on the buildBrush.sides array
     and links it to the current entity
     ===============
     */
    fun FinishBrush(): uBrush_t? {
        val b: uBrush_t?
        val prim: primitive_s

        // create windings for sides and bounds for brush
        if (!ubrush.CreateBrushWindings(buildBrush)) {
            // don't keep this brush
            FreeBuildBrush()
            return null
        }
        if (buildBrush.contents and Material.CONTENTS_AREAPORTAL != 0) {
            if (dmap.dmapGlobals.num_entities != 1) {
                Common.common.Printf(
                    "Entity %d, Brush %d: areaportals only allowed in world\n",
                    dmap.dmapGlobals.num_entities - 1,
                    entityPrimitive
                )
                FreeBuildBrush()
                return null
            }
        }

        // keep it
        b = ubrush.CopyBrush(buildBrush)
        FreeBuildBrush()
        b.entitynum = dmap.dmapGlobals.num_entities - 1
        b.brushnum = entityPrimitive
        b.original = b
        prim = primitive_s() // Mem_Alloc(sizeof(prim));
        //	memset( prim, 0, sizeof( *prim ) );
        prim.next = uEntity.primitives
        uEntity.primitives = prim
        prim.brush = b
        return b
    }

    /*
     ================
     AdjustEntityForOrigin
     ================
     */
    fun AdjustEntityForOrigin(ent: uEntity_t) {
        var prim: primitive_s?
        var b: uBrush_t?
        var i: Int
        var s: side_s?
        prim = ent.primitives
        while (prim != null) {
            b = prim.brush as uBrush_t?
            if (null == b) {
                prim = prim.next
                continue
            }
            i = 0
            while (i < b.numsides) {
                val plane = idPlane()
                s = b.sides[i]
                plane.set(dmap.dmapGlobals.mapPlanes[s.planenum])
                plane.plusAssign(3, plane.Normal().times(ent.origin))
                s.planenum = FindFloatPlane(plane)
                s.texVec.v[0].plusAssign(3, DotProduct(ent.origin, s.texVec.v[0]))
                s.texVec.v[1].plusAssign(3, DotProduct(ent.origin, s.texVec.v[1]))

                // remove any integral shift
                s.texVec.v[0].minusAssign(3, floor(s.texVec.v[0][3]))
                s.texVec.v[1].minusAssign(3, floor(s.texVec.v[1][3]))
                i++
            }
            ubrush.CreateBrushWindings(b)
            prim = prim.next
        }
    }

    /*
     =================
     RemoveDuplicateBrushPlanes

     Returns false if the brush has a mirrored set of planes,
     meaning it encloses no volume.
     Also removes planes without any normal
     =================
     */
    fun RemoveDuplicateBrushPlanes(b: uBrush_t): Boolean {
        var i: Int
        var j: Int
        var k: Int
        val sides: Array<side_s>
        sides = b.sides
        i = 1
        while (i < b.numsides) {

            // check for a degenerate plane
            if (sides[i].planenum == -1) {
                Common.common.Printf("Entity %d, Brush %d: degenerate plane\n", b.entitynum, b.brushnum)
                // remove it
                k = i + 1
                while (k < b.numsides) {
                    sides[k - 1] = sides[k]
                    k++
                }
                b.numsides--
                i--
                i++
                continue
            }

            // check for duplication and mirroring
            j = 0
            while (j < i) {
                if (sides[i].planenum == sides[j].planenum) {
                    Common.common.Printf("Entity %d, Brush %d: duplicate plane\n", b.entitynum, b.brushnum)
                    // remove the second duplicate
                    k = i + 1
                    while (k < b.numsides) {
                        sides[k - 1] = sides[k]
                        k++
                    }
                    b.numsides--
                    i--
                    break
                }
                if (sides[i].planenum == sides[j].planenum xor 1) {
                    // mirror plane, brush is invalid
                    Common.common.Printf("Entity %d, Brush %d: mirrored plane\n", b.entitynum, b.brushnum)
                    return false
                }
                j++
            }
            i++
        }
        return true
    }

    /*
     =================
     ParseBrush
     =================
     */
    fun ParseBrush(mapBrush: idMapBrush, primitiveNum: Int) {
        val b: uBrush_t?
        var s: side_s?
        var ms: idMapBrushSide?
        var i: Int
        val fixedDegeneracies = booleanArrayOf(false)
        buildBrush.entitynum = dmap.dmapGlobals.num_entities - 1
        buildBrush.brushnum = entityPrimitive
        buildBrush.numsides = mapBrush.GetNumSides()
        i = 0
        while (i < mapBrush.GetNumSides()) {

//            s = buildBrush.sides[i];
            ms = mapBrush.GetSide(i)

//		memset( s, 0, sizeof( *s ) );
            buildBrush.sides[i] = side_s()
            s = buildBrush.sides[i]
            s.planenum = FindFloatPlane(ms.GetPlane(), fixedDegeneracies)
            s.material = DeclManager.declManager.FindMaterial(ms.GetMaterial())
            ms.GetTextureVectors(s.texVec.v)
            // remove any integral shift, which will help with grouping
            s.texVec.v[0].minusAssign(3, floor(s.texVec.v[0][3]))
            s.texVec.v[1].minusAssign(3, floor(s.texVec.v[1][3]))
            i++
        }

        // if there are mirrored planes, the entire brush is invalid
        if (!RemoveDuplicateBrushPlanes(buildBrush)) {
            return
        }

        // get the content for the entire brush
        SetBrushContents(buildBrush)
        b = FinishBrush()
        if (b == null) {
            return
        }
        if (fixedDegeneracies[0] && dmap.dmapGlobals.verboseentities) {
            Common.common.Warning("brush %d has degenerate plane equations", primitiveNum)
        }
    }

    /*
     ================
     ParseSurface
     ================
     */
    fun ParseSurface(patch: idMapPatch?, surface: idSurface, material: Material.idMaterial) {
        var i: Int
        var tri: mapTri_s?
        val prim: primitive_s
        prim = primitive_s() // Mem_Alloc(sizeof(prim));
        //	memset( prim, 0, sizeof( *prim ) );
        prim.next = uEntity.primitives
        uEntity.primitives = prim
        i = 0
        while (i < surface.GetNumIndexes()) {
            tri = tritools.AllocTri()
            tri.v[2] = surface[surface.GetIndexes()[i + 0]]
            tri.v[1] = surface[surface.GetIndexes()[i + 2]]
            tri.v[0] = surface[surface.GetIndexes()[i + 1]]
            tri.material = material
            tri.next = prim.tris
            prim.tris = tri
            i += 3
        }

        // set merge groups if needed, to prevent multiple sides from being
        // merged into a single surface in the case of gui shaders, mirrors, and autosprites
        if (material.IsDiscrete()) {
            tri = prim.tris
            while (tri != null) {
                tri.mergeGroup = patch
                tri = tri.next
            }
        }
    }

    /*
     ================
     ParsePatch
     ================
     */
    fun ParsePatch(patch: idMapPatch, primitiveNum: Int) {
        val mat: Material.idMaterial
        if (dmap.dmapGlobals.noCurves) {
            return
        }
        c_numMapPatches++
        mat = DeclManager.declManager.FindMaterial(patch.GetMaterial())!!
        val cp = idSurface_Patch(patch)
        if (patch.GetExplicitlySubdivided()) {
            cp.SubdivideExplicit(patch.GetHorzSubdivisions(), patch.GetVertSubdivisions(), true)
        } else {
            cp.Subdivide(
                MapFile.DEFAULT_CURVE_MAX_ERROR,
                MapFile.DEFAULT_CURVE_MAX_ERROR,
                MapFile.DEFAULT_CURVE_MAX_LENGTH,
                true
            )
        }
        ParseSurface(patch, cp, mat)

//	delete cp;
    }

    /*
     ================
     ProcessMapEntity
     ================
     */
    fun ProcessMapEntity(mapEnt: idMapEntity): Boolean {
        var prim: idMapPrimitive?
        uEntity = dmap.dmapGlobals.uEntities[dmap.dmapGlobals.num_entities]
        //	memset( uEntity, 0, sizeof(*uEntity) );
        uEntity.mapEntity = mapEnt
        dmap.dmapGlobals.num_entities++
        entityPrimitive = 0
        while (entityPrimitive < mapEnt.GetNumPrimitives()) {
            prim = mapEnt.GetPrimitive(entityPrimitive)
            if (prim.GetType() == idMapPrimitive.TYPE_BRUSH) {
                ParseBrush(prim as idMapBrush, entityPrimitive)
            } else if (prim.GetType() == idMapPrimitive.TYPE_PATCH) {
                ParsePatch(prim as idMapPatch, entityPrimitive)
            }
            entityPrimitive++
        }

        // never put an origin on the world, even if the editor left one there
        if (dmap.dmapGlobals.num_entities != 1) {
            uEntity.mapEntity.epairs.GetVector("origin", "", uEntity.origin)
        }
        return true
    }

    //===================================================================
    /*
     ==============
     CreateMapLight

     ==============
     */
    fun CreateMapLight(mapEnt: idMapEntity) {
        val light: mapLight_t
        val dynamic = CBool(false)

        // designers can add the "noPrelight" flag to signal that
        // the lights will move around, so we don't want
        // to bother chopping up the surfaces under it or creating
        // shadow volumes
        mapEnt.epairs.GetBool("noPrelight", "0", dynamic)
        if (dynamic._val) {
            return
        }
        light = mapLight_t()
        light.name[0] = '\u0000'
        light.shadowTris = null

        // parse parms exactly as the game do
        // use the game's epair parsing code so
        // we can use the same renderLight generation
        GameEdit.gameEdit.ParseSpawnArgsToRenderLight(mapEnt.epairs, light.def.parms)
        tr_lightrun.R_DeriveLightData(light.def)

        // get the name for naming the shadow surfaces
        val name = arrayOfNulls<String>(1)
        mapEnt.epairs.GetString("name", "", name)
        idStr.Copynz(light.name, name[0]!!, light.name.size)
        if (light.name[0] == null) {
            Common.common.Error(
                "Light at (%f,%f,%f) didn't have a name",
                light.def.parms.origin[0], light.def.parms.origin[1], light.def.parms.origin[2]
            )
        }
        dmap.dmapGlobals.mapLights.Append(light)
    }

    /*
     ==============
     CreateMapLights

     ==============
     */
    fun CreateMapLights(dmapFile: idMapFile) {
        var i: Int
        var mapEnt: idMapEntity?
        val value = arrayOfNulls<String>(1)
        i = 0
        while (i < dmapFile.GetNumEntities()) {
            mapEnt = dmapFile.GetEntity(i)
            mapEnt.epairs.GetString("classname", "", value)
            if (0 == idStr.Icmp(value[0]!!, "light")) {
                CreateMapLight(mapEnt)
            }
            i++
        }
    }

    /*
     ================
     LoadDMapFile
     ================
     */
    fun LoadDMapFile(filename: String): Boolean {
        var prim: primitive_s?
        val mapBounds = idBounds()
        var brushes: Int
        var triSurfs: Int
        var i: Int
        val size: Int
        Common.common.Printf("--- LoadDMapFile ---\n")
        Common.common.Printf("loading %s\n", filename)

        // load and parse the map file into canonical form
        dmap.dmapGlobals.dmapFile = idMapFile()
        if (!dmap.dmapGlobals.dmapFile!!.Parse(filename)) {
//		delete dmapGlobals.dmapFile;
            dmap.dmapGlobals.dmapFile = null
            Common.common.Warning("Couldn't load map file: '%s'", filename)
            return false
        }
        dmap.dmapGlobals.mapPlanes.Clear()
        dmap.dmapGlobals.mapPlanes.SetGranularity(1024)

        // process the canonical form into utility form
        dmap.dmapGlobals.num_entities = 0
        c_numMapPatches = 0
        c_areaportals = 0
        size = dmap.dmapGlobals.dmapFile!!.GetNumEntities() //* sizeof(dmapGlobals.uEntities[0]);
        dmap.dmapGlobals.uEntities = Array(size) { uEntity_t() } // Mem_Alloc(size);
        //	memset( dmapGlobals.uEntities, 0, size );

        // allocate a very large temporary brush for building
        // the brushes as they are loaded
        buildBrush = uBrush_t() //AllocBrush(MAX_BUILD_SIDES);
        ubrush.c_active_brushes++
        i = 0
        while (i < dmap.dmapGlobals.dmapFile!!.GetNumEntities()) {
            ProcessMapEntity(dmap.dmapGlobals.dmapFile!!.GetEntity(i))
            i++
        }
        CreateMapLights(dmap.dmapGlobals.dmapFile!!)
        brushes = 0
        triSurfs = 0
        mapBounds.Clear()
        prim = dmap.dmapGlobals.uEntities[0].primitives
        while (prim != null) {
            if (prim.brush != null) {
                brushes++
                mapBounds.AddBounds(prim.brush!!.bounds)
            } else if (prim.tris != null) {
                triSurfs++
            }
            prim = prim.next
        }
        Common.common.Printf("%5d total world brushes\n", brushes)
        Common.common.Printf("%5d total world triSurfs\n", triSurfs)
        Common.common.Printf("%5d patches\n", c_numMapPatches)
        Common.common.Printf("%5d entities\n", dmap.dmapGlobals.num_entities)
        Common.common.Printf("%5d planes\n", dmap.dmapGlobals.mapPlanes.Num())
        Common.common.Printf("%5d areaportals\n", c_areaportals)
        Common.common.Printf(
            "size: %5.0f,%5.0f,%5.0f to %5.0f,%5.0f,%5.0f\n",
            mapBounds[0, 0], mapBounds[0, 1], mapBounds[0, 2],
            mapBounds[1, 0], mapBounds[1, 1], mapBounds[1, 2]
        )
        return true
    }

    /*
     ================
     FreeOptimizeGroupList
     ================
     */
    fun FreeOptimizeGroupList(groups: optimizeGroup_s?) {
        var groups = groups
        var next: optimizeGroup_s?
        while (groups != null) {
            next = groups.nextGroup
            tritools.FreeTriList(groups.triList)
            groups.clear() //Mem_Free(groups);
            groups = next
        }
    }

    /*
     ================
     FreeDMapFile
     ================
     */
    fun FreeDMapFile() {
        var i: Int
        var j: Int

//        FreeBrush(buildBrush);
        buildBrush = uBrush_t()

        // free the entities and brushes
        i = 0
        while (i < dmap.dmapGlobals.num_entities) {
            var ent: uEntity_t?
            var prim: primitive_s?
            var nextPrim: primitive_s?
            ent = dmap.dmapGlobals.uEntities[i]
            facebsp.FreeTree(ent.tree)

            // free primitives
            prim = ent.primitives
            while (prim != null) {
                nextPrim = prim.next
                if (prim.brush != null) {
//                    FreeBrush(prim.brush);
                    prim.brush = null
                }
                if (prim.tris != null) {
                    tritools.FreeTriList(prim.tris)
                }
                prim = null //Mem_Free(prim);
                prim = nextPrim
            }

            // free area surfaces
            if (ent.areas.isNotEmpty()) {
                j = 0
                while (j < ent.numAreas) {
                    var area: uArea_t = ent.areas[j]
                    FreeOptimizeGroupList(area.groups)
                    j++
                }
                ent.areas = emptyArray() //Mem_Free(ent.areas);
            }
            i++
        }
        dmap.dmapGlobals.uEntities = emptyArray() //Mem_Free(dmapGlobals.uEntities);
        dmap.dmapGlobals.num_entities = 0

        // free the map lights
        i = 0
        while (i < dmap.dmapGlobals.mapLights.Num()) {
            tr_lightrun.R_FreeLightDefDerivedData(dmap.dmapGlobals.mapLights[i].def)
            i++
        }
        dmap.dmapGlobals.mapLights.DeleteContents(true)
    }
}
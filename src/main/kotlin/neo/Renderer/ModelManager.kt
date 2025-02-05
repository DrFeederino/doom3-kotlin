package neo.Renderer

import neo.Renderer.Model.idRenderModel
import neo.Renderer.Model_beam.idRenderModelBeam
import neo.Renderer.Model_liquid.idRenderModelLiquid
import neo.Renderer.Model_local.idRenderModelStatic
import neo.Renderer.Model_md3.idRenderModelMD3
import neo.Renderer.Model_md5.idRenderModelMD5
import neo.Renderer.Model_prt.idRenderModelPrt
import neo.Renderer.Model_sprite.idRenderModelSprite
import neo.framework.CmdSystem
import neo.framework.CmdSystem.cmdFunction_t
import neo.framework.CmdSystem.idCmdSystem.ArgCompletion_ModelName
import neo.framework.Common
import neo.framework.Common.MemInfo_t
import neo.framework.FileSystem_h
import neo.framework.File_h.idFile
import neo.framework.Session
import neo.idlib.CmdArgs
import neo.idlib.Lib.idException
import neo.idlib.Text.Str.idStr
import neo.idlib.containers.HashIndex.idHashIndex
import neo.sys.win_shared

/**
 *
 */
object ModelManager {
    private var localModelManager: idRenderModelManagerLocal = idRenderModelManagerLocal()
    var renderModelManager: idRenderModelManager = localModelManager
    fun setRenderModelManagers(renderModelManager: idRenderModelManager) {
        localModelManager = renderModelManager as idRenderModelManagerLocal
        ModelManager.renderModelManager = localModelManager
    }

    /*
     ===============================================================================

     Model Manager

     Temporarily created models do not need to be added to the model manager.

     ===============================================================================
     */
    abstract class idRenderModelManager {
        // public abstract					~idRenderModelManager() {}
        // registers console commands and clears the list
        @Throws(idException::class)
        abstract fun Init()

        // frees all the models
        abstract fun Shutdown()

        // called only by renderer::BeginLevelLoad
        abstract fun BeginLevelLoad()

        // called only by renderer::EndLevelLoad
        abstract fun EndLevelLoad()

        // allocates a new empty render model.
        abstract fun AllocModel(): idRenderModel

        // frees a render model
        abstract fun FreeModel(model: idRenderModel)

        // returns NULL if modelName is NULL or an empty string, otherwise
        // it will create a default model if not loadable
        abstract fun FindModel(modelName: String): idRenderModel?
        fun FindModel(modelName: idStr): idRenderModel? {
            return FindModel(modelName.toString())
        }

        // returns NULL if not loadable
        abstract fun CheckModel(modelName: String): idRenderModel?
        fun CheckModel(modelName: idStr): idRenderModel? {
            return CheckModel(modelName.toString())
        }

        // returns the default cube model
        abstract fun DefaultModel(): idRenderModel?

        // world map parsing will add all the inline models with this call
        abstract fun AddModel(model: idRenderModel)

        // when a world map unloads, it removes its internal models from the list
        // before freeing them.
        // There may be an issue with multiple renderWorlds that share data...
        abstract fun RemoveModel(model: idRenderModel)

        // the reloadModels console command calls this, but it can
        // also be explicitly invoked
        abstract fun ReloadModels(forceAll: Boolean /*= false*/)
        fun ReloadModels() {
            ReloadModels(false)
        }

        // write "touchModel <model>" commands for each non-world-map model
        abstract fun WritePrecacheCommands(f: idFile)

        // called during vid_restart
        abstract fun FreeModelVertexCaches()

        // print memory info
        abstract fun PrintMemInfo(mi: MemInfo_t)
    }

    class idRenderModelManagerLocal : idRenderModelManager() {
        private var beamModel: idRenderModel?
        private var defaultModel: idRenderModel?
        private val hash: idHashIndex
        private var insideLevelLoad // don't actually load now
                : Boolean
        private val models: ArrayList<idRenderModel>
        private var spriteModel: idRenderModel?
        private val trailModel: idRenderModel?

        // virtual					~idRenderModelManagerLocal() {}
        @Throws(idException::class)
        override fun Init() {
            CmdSystem.cmdSystem.AddCommand(
                "listModels",
                ListModels_f.getInstance(),
                CmdSystem.CMD_FL_RENDERER,
                "lists all models"
            )
            CmdSystem.cmdSystem.AddCommand(
                "printModel",
                PrintModel_f.getInstance(),
                CmdSystem.CMD_FL_RENDERER,
                "prints model info",
                ArgCompletion_ModelName.Companion.getInstance()
            )
            CmdSystem.cmdSystem.AddCommand(
                "reloadModels",
                ReloadModels_f.getInstance(),
                CmdSystem.CMD_FL_RENDERER or CmdSystem.CMD_FL_CHEAT,
                "reloads models"
            )
            CmdSystem.cmdSystem.AddCommand(
                "touchModel",
                TouchModel_f.getInstance(),
                CmdSystem.CMD_FL_RENDERER,
                "touches a model",
                ArgCompletion_ModelName.Companion.getInstance()
            )
            insideLevelLoad = false

            // create a default model
            val model = idRenderModelStatic()
            model.InitEmpty("_DEFAULT")
            model.MakeDefaultModel()
            model.SetLevelLoadReferenced(true)
            defaultModel = model
            AddModel(model)

            // create the beam model
            val beam: idRenderModelStatic = idRenderModelBeam()
            beam.InitEmpty("_BEAM")
            beam.SetLevelLoadReferenced(true)
            beamModel = beam
            AddModel(beam)
            val sprite: idRenderModelStatic = idRenderModelSprite()
            sprite.InitEmpty("_SPRITE")
            sprite.SetLevelLoadReferenced(true)
            spriteModel = sprite
            AddModel(sprite)
        }

        override fun Shutdown() {
            models.clear()
            hash.Free()
        }

        override fun AllocModel(): idRenderModel {
            return idRenderModelStatic()
        }

        override fun FreeModel(model: idRenderModel) {
            if (null == model) {
                return
            }
            if (null == model) { //TODO:always false?
                Common.common.Error("idRenderModelManager::FreeModel: model '%s' is not a static model", model.Name())
                return
            }
            if (model === defaultModel) {
                Common.common.Error("idRenderModelManager::FreeModel: can't free the default model")
                return
            }
            if (model === beamModel) {
                Common.common.Error("idRenderModelManager::FreeModel: can't free the beam model")
                return
            }
            if (model === spriteModel) {
                Common.common.Error("idRenderModelManager::FreeModel: can't free the sprite model")
                return
            }
            tr_lightrun.R_CheckForEntityDefsUsingModel(model)

//	delete model;
        }

        override fun FindModel(modelName: String): idRenderModel? {
            return GetModel(modelName, true)
        }

        override fun CheckModel(modelName: String): idRenderModel? {
            return GetModel(modelName, false)
        }

        override fun DefaultModel(): idRenderModel? {
            return defaultModel
        }

        override fun AddModel(model: idRenderModel) {
            models.add(model)
            hash.Add(hash.GenerateKey(model.Name(), false), models.indexOf(model))
        }

        override fun RemoveModel(model: idRenderModel) {
            val index = models.indexOf(model)
            hash.RemoveIndex(hash.GenerateKey(model.Name(), false), index)
            models.removeAt(index)
        }

        override fun ReloadModels(forceAll: Boolean) {
            if (forceAll) {
                Common.common.Printf("Reloading all model files...\n")
            } else {
                Common.common.Printf("Checking for changed model files...\n")
            }
            tr_lightrun.R_FreeDerivedData()

            // skip the default model at index 0
            for (i in 1 until models.size) {
                val model = models.get(i)

                // we may want to allow world model reloading in the future, but we don't now
                if (!model.IsReloadable()) {
                    continue
                }
                if (!forceAll) {
                    // check timestamp
                    val current = LongArray(1)
                    FileSystem_h.fileSystem.ReadFile(model.Name(), null, current)
                    if (current[0] <= model.Timestamp()[0]) {
                        continue
                    }
                }
                Common.common.DPrintf("reloading %s.\n", model.Name())
                model.LoadModel()
            }

            // we must force the world to regenerate, because models may
            // have changed size, making their references invalid
            tr_lightrun.R_ReCreateWorldReferences()
        }

        override fun FreeModelVertexCaches() {
            for (i in 0 until models.size) {
                val model = models.get(i)
                model.FreeVertexCache()
            }
        }

        override fun WritePrecacheCommands(f: idFile) {
            for (i in 0 until models.size) {
                val model = models.get(i)
                if (!model.IsReloadable()) {
                    continue
                }

//		char	str[1024];
                val str = String.format("touchModel %s\n", model.Name())
                Common.common.Printf("%s", str)
                f.Printf("%s", str)
            }
        }

        override fun BeginLevelLoad() {
            insideLevelLoad = true
            for (i in 0 until models.size) {
                val model = models.get(i)
                if (Common.com_purgeAll.GetBool() && model.IsReloadable()) {
                    tr_lightrun.R_CheckForEntityDefsUsingModel(model)
                    model.PurgeModel()
                }
                model.SetLevelLoadReferenced(false)
            }

            // purge unused triangle surface memory
            tr_trisurf.R_PurgeTriSurfData(tr_local.frameData)
        }

        override fun EndLevelLoad() {
            Common.common.Printf("----- idRenderModelManagerLocal::EndLevelLoad -----\n")
            val start = win_shared.Sys_Milliseconds()
            insideLevelLoad = false
            var purgeCount = 0
            var keepCount = 0
            var loadCount = 0

            // purge any models not touched
            for (i in 0 until models.size) {
                val model = models.get(i)
                if (!model.IsLevelLoadReferenced() && model.IsLoaded() && model.IsReloadable()) {

//			common.Printf( "purging %s\n", model.Name() );
                    purgeCount++
                    tr_lightrun.R_CheckForEntityDefsUsingModel(model)
                    model.PurgeModel()
                } else {

//			common.Printf( "keeping %s\n", model.Name() );
                    keepCount++
                }
            }

            // purge unused triangle surface memory
            tr_trisurf.R_PurgeTriSurfData(tr_local.frameData)

            // load any new ones
            for (i in 0 until models.size) {
                val model = models.get(i)
                if (model.IsLevelLoadReferenced() && !model.IsLoaded() && model.IsReloadable()) {
                    loadCount++
                    model.LoadModel()
                    if (loadCount and 15 == 0) {
                        Session.Companion.session.PacifierUpdate()
                    }
                }
            }

            // _D3XP added this
            val end = win_shared.Sys_Milliseconds()
            Common.common.Printf("%5d models purged from previous level, ", purgeCount)
            Common.common.Printf("%5d models kept.\n", keepCount)
            if (loadCount != 0) {
                Common.common.Printf("%5d new models loaded in %5.1f seconds\n", loadCount, (end - start) * 0.001)
            }
            Common.common.Printf("---------------------------------------------------\n")
        }

        override fun PrintMemInfo(mi: MemInfo_t) {
            var i: Int
            var j: Int
            var totalMem = 0
            val sortIndex: IntArray
            val f: idFile?
            f = FileSystem_h.fileSystem.OpenFileWrite(mi.filebase.toString() + "_models.txt")
            if (null == f) {
                return
            }

            // sort first
            sortIndex = IntArray(localModelManager.models.size)
            i = 0
            while (i < localModelManager.models.size) {
                sortIndex[i] = i
                i++
            }
            i = 0
            while (i < localModelManager.models.size - 1) {
                j = i + 1
                while (j < localModelManager.models.size) {
                    if (localModelManager.models.get(sortIndex[i])
                            .Memory() < localModelManager.models.get(sortIndex[j]).Memory()
                    ) {
                        val temp = sortIndex[i]
                        sortIndex[i] = sortIndex[j]
                        sortIndex[j] = temp
                    }
                    j++
                }
                i++
            }

            // print next
            i = 0
            while (i < localModelManager.models.size) {
                val model = localModelManager.models.get(sortIndex[i])
                var mem: Int
                if (!model.IsLoaded()) {
                    i++
                    continue
                }
                mem = model.Memory()
                totalMem += mem
                f.Printf("%s %s\n", idStr.Companion.FormatNumber(mem).toString(), model.Name())
                i++
            }

//	delete sortIndex;
            mi.modelAssetsTotal = totalMem
            f.Printf("\nTotal model bytes allocated: %s\n", idStr.Companion.FormatNumber(totalMem).toString())
            FileSystem_h.fileSystem.CloseFile(f)
        }

        private fun GetModel(modelName: String, createIfNotFound: Boolean): idRenderModel? {
            val canonical: idStr
            val extension = idStr()
            if (modelName.isEmpty()) {
                return null
            }
            canonical = idStr(modelName)
            canonical.ToLower()

            // see if it is already present
            val key = hash.GenerateKey(modelName, false)
            var i = hash.First(key)
            while (i != -1) {
                val model = models.get(i)
                if (canonical.Icmp(model.Name()) == 0) {
                    if (!model.IsLoaded()) {
                        // reload it if it was purged
                        model.LoadModel()
                    } else if (insideLevelLoad && !model.IsLevelLoadReferenced()) {
                        // we are reusing a model already in memory, but
                        // touch all the materials to make sure they stay
                        // in memory as well
                        model.TouchData()
                    }
                    model.SetLevelLoadReferenced(true)
                    return model
                }
                i = hash.Next(i)
            }

            // see if we can load it
            // determine which subclass of idRenderModel to initialize
            var model: idRenderModel?
            canonical.ExtractFileExtension(extension)
            if (extension.Icmp("ase") == 0 || extension.Icmp("lwo") == 0 || extension.Icmp("flt") == 0) {
                model = idRenderModelStatic()
                model.InitFromFile(modelName)

                //HACKME::9
//                idRenderModelStatic m = (idRenderModelStatic) model;
//                for (modelSurface_s mimi : m.surfaces.Ptr(modelSurface_s[].class)) {
//                    for (int i = 0; i < mimi.geometry.numVerts; i++) {
//                        final Vector.idVec3 xyz = mimi.geometry.verts[i].xyz;
//                        xyz.oSet(xyz.plus(-50));
//                    }
//                }
            } else if (extension.Icmp("ma") == 0) {
                model = idRenderModelStatic()
                model.InitFromFile(modelName)
            } else if (extension.Icmp(Model.MD5_MESH_EXT) == 0) {
                model = idRenderModelMD5()
                model.InitFromFile(modelName)
            } else if (extension.Icmp("md3") == 0) {
                model = idRenderModelMD3()
                model.InitFromFile(modelName)
            } else if (extension.Icmp("prt") == 0) {
                model = idRenderModelPrt()
                model.InitFromFile(modelName)
            } else if (extension.Icmp("liquid") == 0) {
                model = idRenderModelLiquid()
                model.InitFromFile(modelName)
            } else {
                if (extension.Length() != 0) {
                    Common.common.Warning("unknown model type '%s'", canonical)
                }
                if (!createIfNotFound) {
                    return null
                }
                val smodel = idRenderModelStatic()
                smodel.InitEmpty(modelName)
                smodel.MakeDefaultModel()
                model = smodel
            }
            model.SetLevelLoadReferenced(true)
            if (!createIfNotFound && model.IsDefaultModel()) {
//		delete model;
                model = null
                return null
            }
            AddModel(model)
            return model
        }

        /*
         ==============
         idRenderModelManagerLocal::PrintModel_f
         ==============
         */
        private class PrintModel_f private constructor() : cmdFunction_t() {
            override fun run(args: CmdArgs.idCmdArgs) {
                val model: idRenderModel?
                if (args.Argc() != 2) {
                    Common.common.Printf("usage: printModel <modelName>\n")
                    return
                }
                model = ModelManager.renderModelManager.CheckModel(args.Argv(1))
                if (null == model) {
                    Common.common.Printf("model \"%s\" not found\n", args.Argv(1))
                    return
                }
                model.Print()
            }

            companion object {
                private val instance: cmdFunction_t = PrintModel_f()
                fun getInstance(): cmdFunction_t {
                    return instance
                }
            }
        }

        /*
         ==============
         idRenderModelManagerLocal::ListModels_f
         ==============
         */
        private class ListModels_f private constructor() : cmdFunction_t() {
            override fun run(args: CmdArgs.idCmdArgs) {
                var totalMem = 0
                var inUse = 0
                Common.common.Printf(" mem   srf verts tris\n")
                Common.common.Printf(" ---   --- ----- ----\n")
                for (i in 0 until localModelManager.models.size) {
                    val model = localModelManager.models.get(i)
                    if (!model.IsLoaded()) {
                        continue
                    }
                    model.List()
                    totalMem += model.Memory()
                    inUse++
                }
                Common.common.Printf(" ---   --- ----- ----\n")
                Common.common.Printf(" mem   srf verts tris\n")
                Common.common.Printf("%d loaded models\n", inUse)
                Common.common.Printf("total memory: %4.1fM\n", totalMem.toFloat() / (1024 * 1024))
            }

            companion object {
                private val instance: cmdFunction_t = ListModels_f()
                fun getInstance(): cmdFunction_t {
                    return instance
                }
            }
        }

        /*
         ==============
         idRenderModelManagerLocal::ReloadModels_f
         ==============
         */
        private class ReloadModels_f private constructor() : cmdFunction_t() {
            override fun run(args: CmdArgs.idCmdArgs) {
                localModelManager.ReloadModels(idStr.Companion.Icmp(args.Argv(1), "all") == 0)
            }

            companion object {
                private val instance: cmdFunction_t = ReloadModels_f()
                fun getInstance(): cmdFunction_t {
                    return instance
                }
            }
        }

        /*
         ==============
         idRenderModelManagerLocal::TouchModel_f

         Precache a specific model
         ==============
         */
        private class TouchModel_f private constructor() : cmdFunction_t() {
            override fun run(args: CmdArgs.idCmdArgs) {
                val model = args.Argv(1)
                if (model.isEmpty()) {
                    Common.common.Printf("usage: touchModel <modelName>\n")
                    return
                }
                Common.common.Printf("touchModel %s\n", model)
                Session.Companion.session.UpdateScreen()
                val m = ModelManager.renderModelManager.CheckModel(model)
                if (null == m) {
                    Common.common.Printf("...not found\n")
                }
            }

            companion object {
                private val instance: cmdFunction_t = TouchModel_f()
                fun getInstance(): cmdFunction_t {
                    return instance
                }
            }
        }

        //
        //
        init {
            models = ArrayList()
            hash = idHashIndex()
            defaultModel = null
            beamModel = null
            spriteModel = null
            trailModel = null
            insideLevelLoad = false
        }
    }
}
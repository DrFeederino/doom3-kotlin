/*
 * ===========================================================================
 *
 * Doom 3 GPL Source Code
 * Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
 * Translated to Kotlin by Dr. Feederino with support of Claude Code
 *
 * This file is part of the Doom 3 GPL Source Code ("Doom 3 Source Code").
 *
 * Doom 3 Source Code is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Doom 3 Source Code is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Doom 3 Source Code. If not, see <http://www.gnu.org/licenses/>.
 *
 * In addition, the Doom 3 Source Code is also subject to certain additional terms.
 * You should have received a copy of these additional terms immediately following
 * the terms and conditions of the GNU General Public License which accompanied the
 * Doom 3 Source Code. If not, please request a copy in writing from id Software
 * at the address below.
 *
 * If you have questions concerning this license or the applicable additional terms,
 * you may contact in writing id Software LLC, c/o ZeniMax Media Inc., Suite 120,
 * Rockville, Maryland 20850 USA.
 *
 * ===========================================================================
 *
 * Original source: neo/game/WorldSpawn.cpp, neo/game/WorldSpawn.h
 */

package neo.Game

import neo.Game.GameSys.Class.*
import neo.Game.GameSys.EV_Remove
import neo.Game.GameSys.EV_SafeRemove
import neo.Game.GameSys.Event.idEventDef
import neo.Game.GameSys.SaveGame.idRestoreGame
import neo.Game.GameSys.SysCvar
import neo.Game.Game_local.idGameLocal
import neo.Game.Script.Script_Program.function_t
import neo.Game.Script.Script_Thread.idThread
import neo.framework.FileSystem_h
import neo.idlib.Dict_h
import neo.idlib.Text.Str
import neo.idlib.Text.Str.idStr

/*
 ================
 idWorldspawn

 Worldspawn class.  Each map has one worldspawn which handles global spawnargs.
 Every map should have exactly one worldspawn.
 ================
 */
class WorldSpawn {
    /*
     ===============================================================================

     World entity.

     Every map should have exactly one worldspawn.

     ===============================================================================
     */
    class idWorldspawn : idEntity() {
        companion object {
            val Type = idTypeInfo("idWorldspawn", "idEntity") { idWorldspawn() }

            // CLASS_DECLARATION( idEntity, idWorldspawn )
            //   EVENT( EV_Remove,      idWorldspawn::Event_Remove )
            //   EVENT( EV_SafeRemove,  idWorldspawn::Event_Remove )
            // END_CLASS
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>?> = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>?> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.getEventCallBacks())
                eventCallbacks[EV_Remove] =
                    eventCallback_t0<idWorldspawn> { obj: idWorldspawn -> obj.Event_Remove() }
                eventCallbacks[EV_SafeRemove] =
                    eventCallback_t0<idWorldspawn> { obj: idWorldspawn -> obj.Event_Remove() }
            }
        }

        /*
         ================
         idWorldspawn::~idWorldspawn
         ================
         */
        // FIX: Added missing destructor — C++ ~idWorldspawn clears gameLocal.world
        override fun _deconstructor() {
            if (Game_local.gameLocal.world === this) {
                Game_local.gameLocal.world = null
            }
            super._deconstructor()
        }

        /*
         ================
         idWorldspawn::Spawn
         ================
         */
        override fun Spawn() {
            super.Spawn()
            val scriptname: idStr
            var thread: idThread
            var func: function_t?
            var kv: Dict_h.idKeyValue?

            assert(Game_local.gameLocal.world == null)
            Game_local.gameLocal.world = this

            SysCvar.g_gravity.SetFloat(spawnArgs.GetFloat("gravity", Str.va("%f", Game_local.DEFAULT_GRAVITY)))

            // disable stamina on hell levels
            if (spawnArgs.GetBool("no_stamina")) {
                SysCvar.pm_stamina.SetFloat(0.0f)
            }

            // load script
            scriptname = idStr(Game_local.gameLocal.GetMapName())
            scriptname.SetFileExtension(".script")
            if (FileSystem_h.fileSystem.ReadFile(scriptname.toString(), null, null) > 0) {
                Game_local.gameLocal.program.CompileFile(scriptname.toString())

                // call the main function by default
                func = Game_local.gameLocal.program.FindFunction("main")
                if (func != null) {
                    thread = idThread(func)
                    thread.DelayedStart(0)
                }
            }

            // call any functions specified in worldspawn
            kv = spawnArgs.MatchPrefix("call")
            while (kv != null) {
                func = Game_local.gameLocal.program.FindFunction(kv.GetValue().toString())
                if (func == null) {
                    idGameLocal.Error(
                        "Function '%s' not found in script for '%s' key on worldspawn",
                        kv.GetValue(),
                        kv.GetKey()
                    )
                }
                thread = idThread(func!!)
                thread.DelayedStart(0)
                kv = spawnArgs.MatchPrefix("call", kv)
            }
        }

        /*
         ================
         idWorldspawn::Restore
         ================
         */
        override fun Restore(savefile: idRestoreGame) {
            super.Restore(savefile)
            assert(Game_local.gameLocal.world === this)

            SysCvar.g_gravity.SetFloat(spawnArgs.GetFloat("gravity", Str.va("%f", Game_local.DEFAULT_GRAVITY)))

            // disable stamina on hell levels
            if (spawnArgs.GetBool("no_stamina")) {
                SysCvar.pm_stamina.SetFloat(0.0f)
            }
        }

        /*
         ================
         idWorldspawn::Event_Remove
         ================
         */
        override fun Event_Remove() {
            idGameLocal.Error("Tried to remove world")
        }

        override fun GetType(): idTypeInfo = Type
        override fun CreateInstance(): idClass = idWorldspawn()

        override fun oSet(oGet: idClass?) {
            throw UnsupportedOperationException("Not supported yet.")
        }

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks[event]
        }
    }
}

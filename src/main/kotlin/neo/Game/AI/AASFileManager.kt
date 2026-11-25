/*
===========================================================================

Doom 3 GPL Source Code
Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.

This file is part of the Doom 3 GPL Source Code ("Doom 3 Source Code").

Doom 3 Source Code is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

Doom 3 Source Code is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with Doom 3 Source Code.  If not, see <http://www.gnu.org/licenses/>.

In addition, the Doom 3 Source Code is also subject to certain additional terms. You should have received a copy of these additional terms immediately following the terms and conditions of the GNU General Public License which accompanied the Doom 3 Source Code.  If not, please request a copy in writing from id Software at the address below.

If you have questions concerning this license or the applicable additional terms, you may contact in writing id Software LLC, c/o ZeniMax Media Inc., Suite 120, Rockville, Maryland 20850 USA.

===========================================================================
*/

package neo.Game.AI

import neo.Game.AI.AASFile.idAASFile
import neo.Game.AI.AASFile_local.idAASFileLocal
import neo.idlib.Text.Str.idStr

object AASFileManager {
    private var AASFileManagerLocal: idAASFileManagerLocal = idAASFileManagerLocal()
    var AASFileManager: idAASFileManager = AASFileManagerLocal
    fun setAASFileManagers(AASFileManager: idAASFileManager) {
        this.AASFileManagerLocal = AASFileManager as idAASFileManagerLocal
        this.AASFileManager = AASFileManagerLocal
    }

    /*
     ===============================================================================

     AAS File Manager

     ===============================================================================
     */
    abstract class idAASFileManager {
        //	virtual						~idAASFileManager( void ) {}
        abstract fun LoadAAS(fileName: String,    /*unsigned int*/mapFileCRC: Long): idAASFile?
        abstract fun FreeAAS(file: idAASFile)
    }

    /*
     ===============================================================================

     AAS File Manager

     ===============================================================================
     */
    internal class idAASFileManagerLocal : idAASFileManager() {
        //        virtual						~idAASFileManagerLocal( void ) {}
        override fun LoadAAS(fileName: String, mapFileCRC: Long): idAASFile? {
            val file = idAASFileLocal()
            return if (!file.Load(idStr(fileName), mapFileCRC)) { //		delete file;
                null
            } else file
        }

        override fun FreeAAS(file: idAASFile) { //            delete file
        }
    }
}
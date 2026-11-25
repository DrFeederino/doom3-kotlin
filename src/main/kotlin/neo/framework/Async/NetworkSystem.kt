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

package neo.framework.Async

import neo.framework.Async.AsyncNetwork.idAsyncNetwork
import neo.idlib.BitMsg.idBitMsg

object NetworkSystem {
    /**
     * Disclaimer: Use at own risk! @see https://www.ietf.org/rfc/rfc3514.txt
     */
    private var networkSystemLocal: idNetworkSystem = idNetworkSystem()
    var networkSystem: idNetworkSystem = networkSystemLocal
    fun setNetworkSystems(networkSystem: idNetworkSystem) {
        networkSystemLocal = networkSystem
        NetworkSystem.networkSystem = networkSystemLocal
    }

    /*
     ===============================================================================

     Network System.

     ===============================================================================
     */
    class idNetworkSystem {
        //	virtual					~idNetworkSystem( void ) {}
        fun ServerSendReliableMessage(clientNum: Int, msg: idBitMsg) {
            if (idAsyncNetwork.server.IsActive()) {
                idAsyncNetwork.server.SendReliableGameMessage(clientNum, msg)
            }
        }

        fun ServerSendReliableMessageExcluding(clientNum: Int, msg: idBitMsg) {
            if (idAsyncNetwork.server.IsActive()) {
                idAsyncNetwork.server.SendReliableGameMessageExcluding(clientNum, msg)
            }
        }

        fun ServerGetClientPing(clientNum: Int): Int {
            return if (idAsyncNetwork.server.IsActive()) {
                idAsyncNetwork.server.GetClientPing(clientNum)
            } else 0
        }

        fun ServerGetClientPrediction(clientNum: Int): Int {
            return if (idAsyncNetwork.server.IsActive()) {
                idAsyncNetwork.server.GetClientPrediction(clientNum)
            } else 0
        }

        fun ServerGetClientTimeSinceLastPacket(clientNum: Int): Int {
            return if (idAsyncNetwork.server.IsActive()) {
                idAsyncNetwork.server.GetClientTimeSinceLastPacket(clientNum)
            } else 0
        }

        fun ServerGetClientTimeSinceLastInput(clientNum: Int): Int {
            return if (idAsyncNetwork.server.IsActive()) {
                idAsyncNetwork.server.GetClientTimeSinceLastInput(clientNum)
            } else 0
        }

        fun ServerGetClientOutgoingRate(clientNum: Int): Int {
            return if (idAsyncNetwork.server.IsActive()) {
                idAsyncNetwork.server.GetClientOutgoingRate(clientNum)
            } else 0
        }

        fun ServerGetClientIncomingRate(clientNum: Int): Int {
            return if (idAsyncNetwork.server.IsActive()) {
                idAsyncNetwork.server.GetClientIncomingRate(clientNum)
            } else 0
        }

        fun ServerGetClientIncomingPacketLoss(clientNum: Int): Float {
            return if (idAsyncNetwork.server.IsActive()) {
                idAsyncNetwork.server.GetClientIncomingPacketLoss(clientNum)
            } else 0.0f
        }

        fun ClientSendReliableMessage(msg: idBitMsg) {
            if (idAsyncNetwork.client.IsActive()) {
                idAsyncNetwork.client.SendReliableGameMessage(msg)
            } else if (idAsyncNetwork.server.IsActive()) {
                idAsyncNetwork.server.LocalClientSendReliableMessage(msg)
            }
        }

        fun ClientGetPrediction(): Int {
            return if (idAsyncNetwork.client.IsActive()) {
                idAsyncNetwork.client.GetPrediction()
            } else 0
        }

        fun ClientGetTimeSinceLastPacket(): Int {
            return if (idAsyncNetwork.client.IsActive()) {
                idAsyncNetwork.client.GetTimeSinceLastPacket()
            } else 0
        }

        fun ClientGetOutgoingRate(): Int {
            return if (idAsyncNetwork.client.IsActive()) {
                idAsyncNetwork.client.GetOutgoingRate()
            } else 0
        }

        fun ClientGetIncomingRate(): Int {
            return if (idAsyncNetwork.client.IsActive()) {
                idAsyncNetwork.client.GetIncomingRate()
            } else 0
        }

        fun ClientGetIncomingPacketLoss(): Float {
            return if (idAsyncNetwork.client.IsActive()) {
                idAsyncNetwork.client.GetIncomingPacketLoss()
            } else 0.0f
        }
    }
}
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
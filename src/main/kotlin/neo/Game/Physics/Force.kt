package neo.Game.Physics

import neo.Game.GameSys.Class.eventCallback_t
import neo.Game.GameSys.Class.idClass
import neo.Game.GameSys.Event.idEventDef
import neo.Game.Physics.Physics.idPhysics
import neo.idlib.containers.List.idList

class Force {
    /*
     ===============================================================================

     Force base class

     A force object applies a force to a physics object.

     ===============================================================================
     */
    open class idForce : idClass() {
        // virtual				~idForce( void );
        override fun _deconstructor() {
            forceList.Remove(this)
            super._deconstructor()
        }

        // common force interface
        // evalulate the force up to the given time
        open fun Evaluate(time: Int) {}

        // removes any pointers to the physics object
        open fun RemovePhysics(phys: idPhysics) {}
        override fun CreateInstance(): idClass {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun  /*idTypeInfo*/GetType(): Class<out idClass> {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return null
        }

        override fun oSet(oGet: idClass?) {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        companion object {
            // CLASS_PROTOTYPE( idForce );
            private val forceList: idList<idForce> = idList()
            fun DeletePhysics(phys: idPhysics) {
                var i: Int
                i = 0
                while (i < forceList.Num()) {
                    forceList[i].RemovePhysics(phys)
                    i++
                }
            }

            fun ClearForceList() {
                forceList.Clear()
            }
        }

        init {
            forceList.Append(this)
        }
    }
}
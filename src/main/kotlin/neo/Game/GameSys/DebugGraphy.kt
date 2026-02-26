package neo.Game.GameSys

import neo.Game.Game_local
import neo.Game.Game_local.Companion.gameLocal
import neo.Game.Game_local.idGameLocal
import neo.idlib.containers.List.idList
import neo.idlib.math.idVec3
import neo.idlib.math.idVec4

class DebugGraphy {
    class idDebugGraph     //
    //
    {
        private var index = 0
        private val samples: idList<Float> = idList()
        fun SetNumSamples(num: Int) {
            index = 0
            samples.Clear()
            samples.SetNum(num)
            //            memset(samples.Ptr(), 0, samples.MemoryUsed());
        }

        fun AddValue(value: Float) {
            samples[index++] = value
            if (index >= samples.Num()) {
                index = 0
            }
        }

        fun Draw(color: idVec4, scale: Float) {
            var value1: Float
            var value2: Float
            val vec1 = idVec3()
            val vec2 = idVec3()
            val axis = gameLocal.GetLocalPlayer()!!.viewAxis
            val pos = gameLocal.GetLocalPlayer()!!.GetPhysics().GetOrigin() + axis[1] * samples.Num() * 0.5f
            value1 = samples[index] * scale
            var i = 1
            while (i < samples.Num()) {
                value2 = samples[(i + index) % samples.Num()] * scale
                vec1.set(
                    pos + axis[2] * value1 - axis[1] * (i - 1) + axis[0] * samples.Num()
                )
                vec2.set(
                    pos + axis[2] * value2 - axis[1] * i + axis[0] * samples.Num()
                )
                Game_local.gameRenderWorld!!.DebugLine(color, vec1, vec2, idGameLocal.msec, false)
                value1 = value2
                i++
            }
        }
    }
}
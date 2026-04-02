package neo.idlib.containers

class CInt {
    var _val: Int = 0

    constructor()
    constructor(out: Int) {
        this._val = out
    }

    fun increment(): Int {
        return _val++
    }

    fun decrement(): Int {
        return _val--
    }

    fun rightShift(power: Int) {
        _val = _val shr power
    }

    fun leftShift(power: Int) {
        _val = _val shl power
    }

    fun toFloat(): Float = _val.toFloat()
}
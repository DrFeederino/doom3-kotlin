package neo.idlib.containers

class CInt {
    var integerValue: Int = 0

    constructor()
    constructor(out: Int) {
        this.integerValue = out
    }

    fun increment(): Int {
        return integerValue++
    }

    fun decrement(): Int {
        return integerValue--
    }

    fun rightShift(power: Int) {
        integerValue = integerValue shr power
    }

    fun leftShift(power: Int) {
        integerValue = integerValue shl power
    }
}
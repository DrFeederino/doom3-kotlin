package neo.idlib.containers

// This class represents how floats are treated in C with pointers
class CFloat {
    var _val = 0.0f

    constructor()
    constructor(out: Float) {
        this._val = out
    }
}
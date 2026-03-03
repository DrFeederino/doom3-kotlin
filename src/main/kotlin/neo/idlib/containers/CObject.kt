package neo.idlib.containers

/**
 * Simulates C++ pointer-to-pointer (**) semantics for object types.
 * Allows callees to reassign or nullify the wrapped reference.
 *
 * Usage mirrors CInt/CFloat pattern:
 *   val entityRef = CObject<idEntity>(null)
 *   someFunction(entityRef)        // callee can do ref[0] = x or ref[0] = null
 *   val result = entityRef[0]      // read back modified value
 */
class CObject<T>(private var value: T) {
    /** For cases where you want explicit method access instead of [0] */
    fun get(): T = value
    fun set(newValue: T) {
        value = newValue
    }
}
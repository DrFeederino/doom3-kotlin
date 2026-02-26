package neo.idlib.math.Matrix

/**
 * Yes, the one, the only, the ever illusive zero matrix.
 */
const val MATRIX_EPSILON = 1.0E-6f
const val MATRIX_INVERSE_EPSILON = 1.0E-14f

fun matrixPrint(x: idMatX, label: String) {
    val rows = x.GetNumRows()
    val columns = x.GetNumColumns()
    println("START $label")
    for (b in 0 until rows) {
        for (a in 0 until columns) {
            print(x[b, a].toString() + "\t")
        }
        println()
    }
    println("STOP $label")
}
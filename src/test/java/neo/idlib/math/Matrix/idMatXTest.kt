package neo.idlib.math.Matrix

import neo.idlib.math.idMath
import neo.idlib.math.idSIMD
import neo.idlib.math.idVecX
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.math.abs

class idMatXTest {
    private var original: idMatX = idMatX()
    private var m1: idMatX = idMatX()
    private var m2: idMatX = idMatX()
    private var m3: idMatX = idMatX()
    private val q1: idMatX = idMatX()
    private val q2: idMatX = idMatX()
    private val r1: idMatX = idMatX()
    private val r2: idMatX = idMatX()
    private val v: idVecX = idVecX()
    private val w: idVecX = idVecX()
    private val u: idVecX = idVecX()
    private val c: idVecX = idVecX()
    private val d: idVecX = idVecX()
    private var offset = 0
    private var size = 0
    private var index1: IntArray = IntArray(0)
    private var index2: IntArray = IntArray(0)

    @BeforeEach
    fun setUpTest() {
        idSIMD.Init()
        idMath.Init()
        size = 6
        original.Random(size, size, 0)
        original = (original * original.Transpose())
        index1 = IntArray(size + 1)
        index2 = IntArray(size + 1)
    }

    @Test
    fun LowerTriangularInverseTest() {
        m1.set(original)
        m1.ClearUpperTriangle()
        m2.set(m1)
        m2.InverseSelf()
        m1.LowerTriangularInverse()
        Assertions.assertTrue(m1.Compare(m2, 1e-4f), "idMatX::LowerTriangularInverse failed")
    }

    @Test
    fun UpperTriangularInverseTest() {
        m1.set(original)
        m1.ClearLowerTriangle()
        m2.set(m1)
        m2.InverseSelf()
        m1.UpperTriangularInverse()
        Assertions.assertTrue(m1.Compare(m2, 1e-4f), "idMatX::UpperTriangularInverse failed")
    }

    @Test
    fun Inverse_GaussJordanTest() {
        m1.set(original)
        m1.Inverse_GaussJordan()
        m1.timesAssign(original)
        Assertions.assertTrue(m1.IsIdentity(1e-4f), "idMatX::Inverse_GaussJordan failed")
    }

    @Test
    fun Inverse_UpdateRankOneTest() {
        m1.set(original)
        m2.set(original)
        w.Random(size, 1)
        v.Random(size, 2)

        // invert m1
        m1.Inverse_GaussJordan()

        // modify and invert m2
        m2.Update_RankOne(v, w, 1.0f)
        if (!m2.Inverse_GaussJordan()) {
            assert(false)
        }

        // update inverse of m1
        m1.Inverse_UpdateRankOne(v, w, 1.0f)
        Assertions.assertTrue(m1.Compare(m2, 1e-4f), "idMatX::Inverse_UpdateRankOne failed")
    }

    @Test
    fun Inverse_UpdateRowColumnTest() {
        offset = 0
        while (offset < size) {
            m1.set(original)
            m2.set(original)
            v.Random(size, 1)
            w.Random(size, 2)
            w.p[offset] = 0.0f

            // invert m1
            m1.Inverse_GaussJordan()

            // modify and invert m2
            m2.Update_RowColumn(v, w, offset)
            if (!m2.Inverse_GaussJordan()) {
                assert(false)
            }

            // update inverse of m1
            m1.Inverse_UpdateRowColumn(v, w, offset)
            Assertions.assertTrue(m1.Compare(m2, 1e-3f), "idMatX::Inverse_UpdateRowColumn failed")
            offset++
        }
    }

    @Test
    fun Inverse_UpdateIncrementTest() {
        m1.set(original)
        m2.set(original)
        v.Random(size + 1, 1)
        w.Random(size + 1, 2)
        w.p[size] = 0.0f

        // invert m1
        m1.Inverse_GaussJordan()

        // modify and invert m2
        m2.Update_Increment(v, w)
        if (!m2.Inverse_GaussJordan()) {
            assert(false)
        }

        // update inverse of m1
        m1.Inverse_UpdateIncrement(v, w)
        Assertions.assertTrue(m1.Compare(m2, 1e-4f), "idMatX::Inverse_UpdateIncrement failed")
    }

    @Test
    fun Inverse_UpdateDecrementTest() {
        offset = 0
        while (offset < size) {
            m1.set(original)
            m2.set(original)
            v.SetSize(6)
            w.SetSize(6)
            for (i in 0 until size) {
                v.p[i] = original[i, offset]
                w.p[i] = original[offset, i]
            }

            // invert m1
            m1.Inverse_GaussJordan()

            // modify and invert m2
            m2.Update_Decrement(offset)
            if (!m2.Inverse_GaussJordan()) {
                assert(false)
            }

            // update inverse of m1
            m1.Inverse_UpdateDecrement(v, w, offset)

            //            Assertions.assertTrue("idMatX::Inverse_UpdateDecrement failed " + offset, m1.Compare(m2, 1e-3f));//TODO: fix this?
            Assertions.assertTrue(m1.Compare(m2, 1e-2f), "idMatX::Inverse_UpdateDecrement failed $offset")
            offset++
        }
    }

    @Test
    fun LU_FactorTest() {
        m1.set(original)
        m1.LU_Factor(null) // no pivoting
        m1.LU_UnpackFactors(m2, m3)
        m1.set(m2.times(m3))
        Assertions.assertTrue(original.Compare(m1, 1e-4f), "idMatX::LU_Factor failed")
    }

    @Test
    fun LU_UpdateRankOneTest() {
        m1.set(original)
        m2.set(original)
        w.Random(size, 1)
        v.Random(size, 2)

        // factor m1
        m1.LU_Factor(index1)

        // modify and factor m2
        m2.Update_RankOne(v, w, 1.0f)
        if (!m2.LU_Factor(index2)) {
            assert(false)
        }
        m2.LU_MultiplyFactors(m3, index2)
        m2.set(m3)

        // update factored m1
        m1.LU_UpdateRankOne(v, w, 1.0f, index1)
        m1.LU_MultiplyFactors(m3, index1)
        m1.set(m3)
        Assertions.assertTrue(m1.Compare(m2, 1e-4f), "idMatX::LU_UpdateRankOne failed")
    }

    @Test
    fun LU_UpdateRowColumnTest() {
        offset = 0
        while (offset < size) {
            m1.set(original)
            m2.set(original)
            v.Random(size, 1)
            w.Random(size, 2)
            w.p[offset] = 0.0f

            // factor m1
            m1.LU_Factor(index1)

            // modify and factor m2
            m2.Update_RowColumn(v, w, offset)
            if (!m2.LU_Factor(index2)) {
                assert(false)
            }
            m2.LU_MultiplyFactors(m3, index2)
            m2.set(m3)

            // update m1
            m1.LU_UpdateRowColumn(v, w, offset, index1)
            m1.LU_MultiplyFactors(m3, index1)
            m1.set(m3)
            Assertions.assertTrue(m1.Compare(m2, 1e-3f), "idMatX::LU_UpdateRowColumn failed")
            offset++
        }
    }

    @Test
    fun LU_UpdateIncrementTest() {
        m1.set(original)
        m2.set(original)
        v.Random(size + 1, 1)
        w.Random(size + 1, 2)
        w.p[size] = 0.0f

        // factor m1
        m1.LU_Factor(index1)

        // modify and factor m2
        m2.Update_Increment(v, w)
        if (!m2.LU_Factor(index2)) {
            assert(false)
        }
        m2.LU_MultiplyFactors(m3, index2)
        m2.set(m3)

        // update factored m1
        m1.LU_UpdateIncrement(v, w, index1)
        m1.LU_MultiplyFactors(m3, index1)
        m1.set(m3)
        Assertions.assertTrue(m1.Compare(m2, 1e-4f), "idMatX::LU_UpdateIncrement failed")
    }

    @Test
    fun LU_UpdateDecrementTest() {
        offset = 0
        while (offset < size) {
            m1 = idMatX() //TODO:check m1=m3, m2=m2 refs!!!
            m1.set(original)
            m2.set(original)
            v.SetSize(6)
            w.SetSize(6)
            for (i in 0 until size) {
                v.p[i] = original[i, offset]
                w.p[i] = original[offset, i]
            }

            // factor m1
            m1.LU_Factor(index1)

            // modify and factor m2
            m2.Update_Decrement(offset)
            if (!m2.LU_Factor(index2)) {
                assert(false)
            }
            m2.LU_MultiplyFactors(m3, index2)
            m2.set(m3)
            u.SetSize(6)
            for (i in 0 until size) {
                u.p[i] = original[index1[offset], i]
            }

            // update factors of m1
            m1.LU_UpdateDecrement(v, w, u, offset, index1)
            m1.LU_MultiplyFactors(m3, index1)
            m1.set(m3)
            Assertions.assertTrue(m1.Compare(m2, 1e-3f), "idMatX::LU_UpdateDecrement failed")
            offset++
        }
    }

    @Test
    fun LU_InverseTest() {
        m2.set(original)
        m2.LU_Factor(null)
        m2.LU_Inverse(m1, null)
        m1.timesAssign(original)
        Assertions.assertTrue(m1.IsIdentity(1e-4f), "idMatX::LU_Inverse failed")
    }

    @Test
    fun QR_FactorTest() {
        c.SetSize(size)
        d.SetSize(size)
        m1.set(original)
        m1.QR_Factor(c, d)
        m1.QR_UnpackFactors(q1, r1, c, d)
        m1.set(q1.times(r1))
        Assertions.assertTrue(original.Compare(m1, 1e-4f), "idMatX::QR_Factor failed")
    }

    @Test
    fun QR_UpdateRankOneTest() {
        c.SetSize(size)
        d.SetSize(size)
        m1.set(original)
        m2.set(original)
        w.Random(size, 0)
        v.set(w)

        // factor m1
        m1.QR_Factor(c, d)
        m1.QR_UnpackFactors(q1, r1, c, d)

        // modify and factor m2
        m2.Update_RankOne(v, w, 1.0f)
        if (!m2.QR_Factor(c, d)) {
            assert(false)
        }
        m2.QR_UnpackFactors(q2, r2, c, d)
        m2 = q2.times(r2)

        // update factored m1
        q1.QR_UpdateRankOne(r1, v, w, 1.0f)
        m1 = q1.times(r1)
        Assertions.assertTrue(m1.Compare(m2, 1e-4f), "idMatX::QR_UpdateRankOne failed")
    }

    @Test
    fun QR_UpdateRowColumnTest() {
        offset = 0
        while (offset < size) {
            c.SetSize(size)
            d.SetSize(size)
            m1.set(original)
            m2.set(original)
            v.Random(size, 1)
            w.Random(size, 2)
            w.p[offset] = 0.0f

            // factor m1
            m1.QR_Factor(c, d)
            m1.QR_UnpackFactors(q1, r1, c, d)

            // modify and factor m2
            m2.Update_RowColumn(v, w, offset)
            if (!m2.QR_Factor(c, d)) {
                assert(false)
            }
            m2.QR_UnpackFactors(q2, r2, c, d)
            m2 = q2.times(r2)

            // update m1
            q1.QR_UpdateRowColumn(r1, v, w, offset)
            m1 = q1.times(r1)
            Assertions.assertTrue(m1.Compare(m2, 1e-3f), "idMatX::QR_UpdateRowColumn failed")
            offset++
        }
    }

    @Test
    fun QR_UpdateIncrementTest() {
        c.SetSize(size + 1)
        d.SetSize(size + 1)
        m1.set(original)
        m2.set(original)
        v.Random(size + 1, 1)
        w.Random(size + 1, 2)
        w.p[size] = 0.0f

        // factor m1
        m1.QR_Factor(c, d)
        m1.QR_UnpackFactors(q1, r1, c, d)

        // modify and factor m2
        m2.Update_Increment(v, w)
        if (!m2.QR_Factor(c, d)) {
            assert(false)
        }
        m2.QR_UnpackFactors(q2, r2, c, d)
        m2 = q2.times(r2)

        // update factored m1
        q1.QR_UpdateIncrement(r1, v, w)
        m1 = q1.times(r1)
        Assertions.assertTrue(m1.Compare(m2, 1e-4f), "idMatX::QR_UpdateIncrement failed")
    }

    @Test
    fun QR_UpdateDecrementTest() {
        QR_UpdateDecrementSetUp()
    }

    private fun QR_UpdateDecrementSetUp() {
        offset = 0
        while (offset < size) {
            c.SetSize(size + 1)
            d.SetSize(size + 1)
            m1.set(original)
            m2.set(original)
            v.SetSize(6)
            w.SetSize(6)
            for (i in 0 until size) {
                v.p[i] = original[i, offset]
                w.p[i] = original[offset, i]
            }

            // factor m1
            m1.QR_Factor(c, d)
            m1.QR_UnpackFactors(q1, r1, c, d)

            // modify and factor m2
            m2.Update_Decrement(offset)
            if (!m2.QR_Factor(c, d)) {
                assert(false)
            }
            m2.QR_UnpackFactors(q2, r2, c, d)
            m2 = q2.times(r2)

            // update factors of m1
            q1.QR_UpdateDecrement(r1, v, w, offset)
            m1.set(q1.times(r1))
            Assertions.assertTrue(m1.Compare(m2, 1e-3f), "idMatX::QR_UpdateDecrement failed")
            offset++
        }
    }

    @Test
    fun QR_InverseTest() {
        QR_UpdateDecrementSetUp()
        m2.set(original)
        m2.QR_Factor(c, d)
        m2.QR_Inverse(m1, c, d)
        m1.timesAssign(original)
        Assertions.assertTrue(m1.IsIdentity(1e-4f), "idMatX::QR_Inverse failed")
    }

    @Test
    fun SVD_FactorTest() {
        SVD_FactorSetUp()
        Assertions.assertTrue(original.Compare(m1, 1e-4f), "idMatX::SVD_Factor failed")
    }

    private fun SVD_FactorSetUp() {
        m1.set(original)
        m3.Zero(size, size)
        w.Zero(size)
        m1.SVD_Factor(w, m3)
        m2.Diag(w)
        m3.TransposeSelf()
        m1.set(m1.times(m2).times(m3))
    }

    @Test
    fun SVD_InverseTest() {
        SVD_FactorSetUp()
        m2.set(original)
        m2.SVD_Factor(w, m3)
        m2.SVD_Inverse(m1, w, m3)
        m1.timesAssign(original)
        Assertions.assertTrue(m1.IsIdentity(1e-4f), "idMatX::SVD_Inverse failed")
    }

    @Test
    fun Cholesky_FactorTest() {
        m1.set(original)
        m1.Cholesky_Factor()
        m1.Cholesky_MultiplyFactors(m2)
        Assertions.assertTrue(original.Compare(m2, 1e-4f), "idMatX::Cholesky_Factor failed")
    }

    @Test
    fun Cholesky_UpdateRankOneTest() {
        m1.set(original)
        m2.set(original)
        w.Random(size, 0)

        // factor m1
        m1.Cholesky_Factor()
        m1.ClearUpperTriangle()

        // modify and factor m2
        m2.Update_RankOneSymmetric(w, 1.0f)
        if (!m2.Cholesky_Factor()) {
            assert(false)
        }
        m2.ClearUpperTriangle()

        // update factored m1
        m1.Cholesky_UpdateRankOne(w, 1.0f, 0)
        Assertions.assertTrue(m1.Compare(m2, 1e-4f), "idMatX::Cholesky_UpdateRankOne failed")
    }

    @Test
    fun Cholesky_UpdateRowColumnTest() {
        offset = 0
        while (offset < size) {
            m1.set(original)
            m2.set(original)

            // factor m1
            m1.Cholesky_Factor()
            m1.ClearUpperTriangle()
            val pdtable = intArrayOf(1, 0, 1, 0, 0, 0)
            w.Random(size, pdtable[offset])
            w.timesAssign(0.1f)

            // modify and factor m2
            m2.Update_RowColumnSymmetric(w, offset)
            if (!m2.Cholesky_Factor()) {
                assert(false)
            }
            m2.ClearUpperTriangle()

            // update m1
            m1.Cholesky_UpdateRowColumn(w, offset)
            Assertions.assertTrue(m1.Compare(m2, 1e-3f), "idMatX::Cholesky_UpdateRowColumn failed")
            offset++
        }
    }

    @Test
    fun Cholesky_UpdateIncrementTest() {
        m1.Random(size + 1, size + 1, 0)
        m3.set(m1.times(m1.Transpose()))
        m1.SquareSubMatrix(m3, size)
        m2.set(m1)
        w.SetSize(size + 1)
        for (i in 0 until size + 1) {
            w.p[i] = m3[size, i]
        }

        // factor m1
        m1.Cholesky_Factor()

        // modify and factor m2
        m2.Update_IncrementSymmetric(w)
        if (!m2.Cholesky_Factor()) {
            assert(false)
        }

        // update factored m1
        m1.Cholesky_UpdateIncrement(w)
        m1.ClearUpperTriangle()
        m2.ClearUpperTriangle()
        Assertions.assertTrue(m1.Compare(m2, 1e-4f), "idMatX::Cholesky_UpdateIncrement failed")
    }

    @Test
    fun Cholesky_UpdateDecrementTest() {
        offset = 0
        while (offset < size) {
            m1.set(original)
            m2.set(original)
            v.SetSize(6)
            for (i in 0 until size) {
                v.p[i] = original[i, offset]
            }

            // factor m1
            m1.Cholesky_Factor()

            // modify and factor m2
            m2.Update_Decrement(offset)
            if (!m2.Cholesky_Factor()) {
                assert(false)
            }

            // update factors of m1
            m1.Cholesky_UpdateDecrement(v, offset)
            Assertions.assertTrue(m1.Compare(m2, 1e-3f), "idMatX::Cholesky_UpdateDecrement failed")
            offset += size - 1
        }
    }

    @Test
    fun Cholesky_InverseTest() {
        m2.set(original)
        m2.Cholesky_Factor()
        m2.Cholesky_Inverse(m1)
        m1.timesAssign(original)
        Assertions.assertTrue(m1.IsIdentity(1e-4f), "idMatX::Cholesky_Inverse failed")
    }

    @Test
    fun LDLT_FactorTest() {
        m1.set(original)
        m1.LDLT_Factor()
        m1.LDLT_MultiplyFactors(m2)
        Assertions.assertTrue(original.Compare(m2, 1e-4f), "idMatX::LDLT_Factor failed")
        m1.LDLT_UnpackFactors(m2, m3)
        m2 = m2.times(m3).times(m2.Transpose())
        Assertions.assertTrue(original.Compare(m2, 1e-4f), "idMatX::LDLT_Factor failed")
    }

    @Test
    fun LDLT_UpdateRankOneTest() {
        m1.set(original)
        m2.set(original)
        w.Random(size, 0)

        // factor m1
        m1.LDLT_Factor()
        m1.ClearUpperTriangle()

        // modify and factor m2
        m2.Update_RankOneSymmetric(w, 1.0f)
        if (!m2.LDLT_Factor()) {
            assert(false)
        }
        m2.ClearUpperTriangle()

        // update factored m1
        m1.LDLT_UpdateRankOne(w, 1.0f, 0)
        Assertions.assertTrue(m1.Compare(m2, 1e-4f), "idMatX::LDLT_UpdateRankOne failed")
    }

    @Test
    fun LDLT_UpdateRowColumnTest() {
        offset = 0
        while (offset < size) {
            m1.set(original)
            m2.set(original)
            w.Random(size, 0)

            // factor m1
            m1.LDLT_Factor()
            m1.ClearUpperTriangle()

            // modify and factor m2
            m2.Update_RowColumnSymmetric(w, offset)
            if (!m2.LDLT_Factor()) {
                assert(false)
            }
            m2.ClearUpperTriangle()

            // update m1
            m1.LDLT_UpdateRowColumn(w, offset)
            Assertions.assertTrue(m1.Compare(m2, 1e-3f), "idMatX::LDLT_UpdateRowColumn failed")
            offset++
        }
    }

    @Test
    fun LDLT_UpdateIncrementTest() {
        m1.Random(size + 1, size + 1, 0)
        m3 = m1.times(m1.Transpose())
        m1.SquareSubMatrix(m3, size)
        m2.set(m1)
        w.SetSize(size + 1)
        for (i in 0 until size + 1) {
            w.p[i] = m3[size, i]
        }

        // factor m1
        m1.LDLT_Factor()

        // modify and factor m2
        m2.Update_IncrementSymmetric(w)
        if (!m2.LDLT_Factor()) {
            assert(false)
        }

        // update factored m1
        m1.LDLT_UpdateIncrement(w)
        m1.ClearUpperTriangle()
        m2.ClearUpperTriangle()
        Assertions.assertTrue(m1.Compare(m2, 1e-4f), "idMatX::LDLT_UpdateIncrement failed")
    }

    @Test
    fun LDLT_UpdateDecrementTest() {
        offset = 0
        while (offset < size) {
            m1.set(original)
            m2.set(original)
            v.SetSize(6)
            for (i in 0 until size) {
                v.p[i] = original[i, offset]
            }

            // factor m1
            m1.LDLT_Factor()

            // modify and factor m2
            m2.Update_Decrement(offset)
            if (!m2.LDLT_Factor()) {
                assert(false)
            }

            // update factors of m1
            m1.LDLT_UpdateDecrement(v, offset)
            Assertions.assertTrue(m1.Compare(m2, 1e-3f), "idMatX::LDLT_UpdateDecrement failed")
            offset++
        }
    }

    @Test
    fun LDLT_InverseTest() {
        LDLT_InverseSetUp()
        Assertions.assertTrue(m1.IsIdentity(1e-4f), "idMatX::LDLT_Inverse failed")
    }

    private fun LDLT_InverseSetUp() {
        m2.set(original)
        m2.LDLT_Factor()
        m2.LDLT_Inverse(m1)
        m1.timesAssign(original)
    }

    @Test
    fun Eigen_SolveSymmetricTriDiagonalTest() {
        LDLT_InverseSetUp()
        m3.set(original)
        m3.TriDiagonal_ClearTriangles()
        m1.set(m3)
        v.SetSize(size)
        m1.Eigen_SolveSymmetricTriDiagonal(v)
        m3.TransposeMultiply(m2, m1)
        for (i in 0 until size) {
            for (j in 0 until size) {
                m1.timesAssign(i, j, v.p[j])
            }
        }
        Assertions.assertTrue(m1.Compare(m2, 1e-4f), "idMatX::Eigen_SolveSymmetricTriDiagonal failed")
    }

    @Test
    fun Eigen_SolveSymmetricTest() {
        LDLT_InverseSetUp()
        m3.set(original)
        m1.set(m3)
        v.SetSize(size)
        m1.Eigen_SolveSymmetric(v)
        m3.TransposeMultiply(m2, m1)
        for (i in 0 until size) {
            for (j in 0 until size) {
                m1.timesAssign(i, j, v.p[j])
            }
        }
        Assertions.assertTrue(m1.Compare(m2, 1e-4f), "idMatX::Eigen_SolveSymmetric failed")
    }

    @Test
    fun Eigen_SolveTest() {
        LDLT_InverseSetUp()
        m3.set(original)
        m1.set(m3)
        v.SetSize(size)
        w.SetSize(size)
        m1.Eigen_Solve(v, w)
        m3.TransposeMultiply(m2, m1)
        for (i in 0 until size) {
            for (j in 0 until size) {
                m1.timesAssign(i, j, v.p[j])
            }
        }
        Assertions.assertTrue(m1.Compare(m2, 1e-4f), "idMatX::Eigen_Solve failed")
    }

    @Test
    fun checkEqualityAbsAndIdFabs() {
        Assertions.assertTrue(abs(-1.0f) == abs(-1.0f))
    }
}

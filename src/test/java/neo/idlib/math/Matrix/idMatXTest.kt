package neo.idlib.math.Matrix

import neo.idlib.math.idMath
import neo.idlib.math.idSIMD
import neo.idlib.math.idVecX
import org.junit.Assert
import org.junit.Before
import org.junit.Test
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

    @Before
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
        Assert.assertTrue("idMatX::LowerTriangularInverse failed", m1.Compare(m2, 1e-4f))
    }

    @Test
    fun UpperTriangularInverseTest() {
        m1.set(original)
        m1.ClearLowerTriangle()
        m2.set(m1)
        m2.InverseSelf()
        m1.UpperTriangularInverse()
        Assert.assertTrue("idMatX::UpperTriangularInverse failed", m1.Compare(m2, 1e-4f))
    }

    @Test
    fun Inverse_GaussJordanTest() {
        m1.set(original)
        m1.Inverse_GaussJordan()
        m1.timesAssign(original)
        Assert.assertTrue("idMatX::Inverse_GaussJordan failed", m1.IsIdentity(1e-4f))
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
        Assert.assertTrue("idMatX::Inverse_UpdateRankOne failed", m1.Compare(m2, 1e-4f))
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
            Assert.assertTrue("idMatX::Inverse_UpdateRowColumn failed", m1.Compare(m2, 1e-3f))
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
        Assert.assertTrue("idMatX::Inverse_UpdateIncrement failed", m1.Compare(m2, 1e-4f))
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

//            Assert.assertTrue("idMatX::Inverse_UpdateDecrement failed " + offset, m1.Compare(m2, 1e-3f));//TODO: fix this?
            Assert.assertTrue("idMatX::Inverse_UpdateDecrement failed $offset", m1.Compare(m2, 1e-2f))
            offset++
        }
    }

    @Test
    fun LU_FactorTest() {
        m1.set(original)
        m1.LU_Factor(null) // no pivoting
        m1.LU_UnpackFactors(m2, m3)
        m1.set(m2.times(m3))
        Assert.assertTrue("idMatX::LU_Factor failed", original.Compare(m1, 1e-4f))
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
        Assert.assertTrue("idMatX::LU_UpdateRankOne failed", m1.Compare(m2, 1e-4f))
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
            Assert.assertTrue("idMatX::LU_UpdateRowColumn failed", m1.Compare(m2, 1e-3f))
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
        Assert.assertTrue("idMatX::LU_UpdateIncrement failed", m1.Compare(m2, 1e-4f))
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
            Assert.assertTrue("idMatX::LU_UpdateDecrement failed", m1.Compare(m2, 1e-3f))
            offset++
        }
    }

    @Test
    fun LU_InverseTest() {
        m2.set(original)
        m2.LU_Factor(null)
        m2.LU_Inverse(m1, null)
        m1.timesAssign(original)
        Assert.assertTrue("idMatX::LU_Inverse failed", m1.IsIdentity(1e-4f))
    }

    @Test
    fun QR_FactorTest() {
        c.SetSize(size)
        d.SetSize(size)
        m1.set(original)
        m1.QR_Factor(c, d)
        m1.QR_UnpackFactors(q1, r1, c, d)
        m1.set(q1.times(r1))
        Assert.assertTrue("idMatX::QR_Factor failed", original.Compare(m1, 1e-4f))
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
        Assert.assertTrue("idMatX::QR_UpdateRankOne failed", m1.Compare(m2, 1e-4f))
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
            Assert.assertTrue("idMatX::QR_UpdateRowColumn failed", m1.Compare(m2, 1e-3f))
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
        Assert.assertTrue("idMatX::QR_UpdateIncrement failed", m1.Compare(m2, 1e-4f))
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
            Assert.assertTrue("idMatX::QR_UpdateDecrement failed", m1.Compare(m2, 1e-3f))
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
        Assert.assertTrue("idMatX::QR_Inverse failed", m1.IsIdentity(1e-4f))
    }

    @Test
    fun SVD_FactorTest() {
        SVD_FactorSetUp()
        Assert.assertTrue("idMatX::SVD_Factor failed", original.Compare(m1, 1e-4f))
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
        Assert.assertTrue("idMatX::SVD_Inverse failed", m1.IsIdentity(1e-4f))
    }

    @Test
    fun Cholesky_FactorTest() {
        m1.set(original)
        m1.Cholesky_Factor()
        m1.Cholesky_MultiplyFactors(m2)
        Assert.assertTrue("idMatX::Cholesky_Factor failed", original.Compare(m2, 1e-4f))
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
        Assert.assertTrue("idMatX::Cholesky_UpdateRankOne failed", m1.Compare(m2, 1e-4f))
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
            Assert.assertTrue("idMatX::Cholesky_UpdateRowColumn failed", m1.Compare(m2, 1e-3f))
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
        Assert.assertTrue("idMatX::Cholesky_UpdateIncrement failed", m1.Compare(m2, 1e-4f))
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
            Assert.assertTrue("idMatX::Cholesky_UpdateDecrement failed", m1.Compare(m2, 1e-3f))
            offset += size - 1
        }
    }

    @Test
    fun Cholesky_InverseTest() {
        m2.set(original)
        m2.Cholesky_Factor()
        m2.Cholesky_Inverse(m1)
        m1.timesAssign(original)
        Assert.assertTrue("idMatX::Cholesky_Inverse failed", m1.IsIdentity(1e-4f))
    }

    @Test
    fun LDLT_FactorTest() {
        m1.set(original)
        m1.LDLT_Factor()
        m1.LDLT_MultiplyFactors(m2)
        Assert.assertTrue("idMatX::LDLT_Factor failed", original.Compare(m2, 1e-4f))
        m1.LDLT_UnpackFactors(m2, m3)
        m2 = m2.times(m3).times(m2.Transpose())
        Assert.assertTrue("idMatX::LDLT_Factor failed", original.Compare(m2, 1e-4f))
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
        Assert.assertTrue("idMatX::LDLT_UpdateRankOne failed", m1.Compare(m2, 1e-4f))
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
            Assert.assertTrue("idMatX::LDLT_UpdateRowColumn failed", m1.Compare(m2, 1e-3f))
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
        Assert.assertTrue("idMatX::LDLT_UpdateIncrement failed", m1.Compare(m2, 1e-4f))
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
            Assert.assertTrue("idMatX::LDLT_UpdateDecrement failed", m1.Compare(m2, 1e-3f))
            offset++
        }
    }

    @Test
    fun LDLT_InverseTest() {
        LDLT_InverseSetUp()
        Assert.assertTrue("idMatX::LDLT_Inverse failed", m1.IsIdentity(1e-4f))
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
        Assert.assertTrue("idMatX::Eigen_SolveSymmetricTriDiagonal failed", m1.Compare(m2, 1e-4f))
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
        Assert.assertTrue("idMatX::Eigen_SolveSymmetric failed", m1.Compare(m2, 1e-4f))
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
        Assert.assertTrue("idMatX::Eigen_Solve failed", m1.Compare(m2, 1e-4f))
    }

    @Test
    fun checkEqualityAbsAndIdFabs() {
        Assert.assertTrue(abs(-1.0f) == abs(-1.0f))
    }
}

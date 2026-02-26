package neo.idlib.math

import org.junit.Assert
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

class PolynomialTest {
    private var i = 0
    private var num = 0
    private var roots: FloatArray = FloatArray(0)
    private var value = 0f
    private var complexRoots: Array<idComplex> = Array(0) { idComplex() }
    private var complexValue: idComplex = idComplex()
    private var p: idPolynomial = idPolynomial()

    @Before
    @Throws(Exception::class)
    fun setUp() {
        idMath.Init()
        roots = FloatArray(4)
        complexRoots = Array(4) { idComplex() }
    }

    @Test
    fun Test1() {
        p = idPolynomial(-5.0f, 4.0f)
        num = p.GetRoots(roots)
        i = 0
        while (i < num) {
            value = p.GetValue(roots[i])
            Assert.assertTrue(abs(value) < 1e-4f)
            i++
        }
    }

    @Test
    fun Test2() {
        p = idPolynomial(-5.0f, 4.0f, 3.0f)
        num = p.GetRoots(roots)
        i = 0
        while (i < num) {
            value = p.GetValue(roots[i])
            Assert.assertTrue(abs(value) < 1e-4f)
            i++
        }
    }

    @Test
    fun Test3() {
        p = idPolynomial(1.0f, 4.0f, 3.0f, -2.0f)
        num = p.GetRoots(roots)
        i = 0
        while (i < num) {
            value = p.GetValue(roots[i])
            Assert.assertTrue(abs(value) < 1e-4f)
            i++
        }
    }

    @Test
    fun Test4() {
        p = idPolynomial(5.0f, 4.0f, 3.0f, -2.0f)
        num = p.GetRoots(roots)
        i = 0
        while (i < num) {
            value = p.GetValue(roots[i])
            Assert.assertTrue(abs(value) < 1e-4f)
            i++
        }
    }

    @Test
    fun Test5() {
        p = idPolynomial(-5.0f, 4.0f, 3.0f, 2.0f, 1.0f)
        num = p.GetRoots(roots)
        i = 0
        while (i < num) {
            value = p.GetValue(roots[i])
            Assert.assertTrue(abs(value) < 1e-4f)
            i++
        }
    }

    @Test
    fun Test6() {
        p = idPolynomial(1.0f, 4.0f, 3.0f, -2.0f)
        num = p.GetRoots(complexRoots)
        i = 0
        while (i < num) {
            complexValue = p.GetValue(complexRoots[i])
            Assert.assertTrue(abs(complexValue.r) < 1e-4f && abs(complexValue.i) < 1e-4f)
            i++
        }
    }

    @Test
    fun Test7() {
        p = idPolynomial(5.0f, 4.0f, 3.0f, -2.0f)
        num = p.GetRoots(complexRoots)
        i = 0
        while (i < num) {
            complexValue = p.GetValue(complexRoots[i])
            Assert.assertTrue(abs(complexValue.r) < 1e-4f && abs(complexValue.i) < 1e-4f)
            i++
        }
    }
}
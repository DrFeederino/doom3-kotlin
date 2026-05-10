package neo.idlib.Text

import org.junit.Assert.assertEquals
import org.junit.Test

class CStrTest {
    @Test
    fun atoiParsesLeadingIntegerLikeC() {
        assertEquals(43, atoi("43.75"))
        assertEquals(-12, atoi("  -12.9"))
        assertEquals(7, atoi("+7xyz"))
        assertEquals(0, atoi("true"))
        assertEquals(0, atoi("abc123"))
    }

    @Test
    fun atofParsesLeadingFloatLikeC() {
        assertEquals(0.25f, atof("0.25f"), 0.0f)
        assertEquals(-12.5f, atof("  -12.5xyz"), 0.0f)
        assertEquals(1000.0f, atof("1e3f"), 0.0f)
        assertEquals(1.5f, atof("1,5"), 0.0f)
        assertEquals(0.0f, atof("true"), 0.0f)
    }
}

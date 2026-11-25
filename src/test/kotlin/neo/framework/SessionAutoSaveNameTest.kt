package neo.framework

import neo.framework.Session_local.idSessionLocal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class SessionAutoSaveNameTest {
    @Test
    fun autoSaveFileNameUsesMapName() {
        val marsCity1Save = idSessionLocal.autoSaveFileName("mars_city1")
        val marsCity2Save = idSessionLocal.autoSaveFileName("mars_city2")

        assertEquals("Autosave_mars_city1", marsCity1Save)
        assertEquals("Autosave_mars_city2", marsCity2Save)
        assertNotEquals(marsCity1Save, marsCity2Save)
    }
}
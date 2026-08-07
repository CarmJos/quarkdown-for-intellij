package cc.carm.plugin.intellij.quarkdown.lang.function

import org.junit.Assert.assertEquals
import org.junit.Test

class QuarkdownNamingTest {

    @Test
    fun `enum constants are converted to quarkdown names`() {
        assertEquals("bottomcenter", QuarkdownNaming.enumValueName("BOTTOM_CENTER"))
        assertEquals("topleftcorner", QuarkdownNaming.enumValueName("TOP_LEFT_CORNER"))
        assertEquals("topinside", QuarkdownNaming.enumValueName("TOP_INSIDE"))
        assertEquals("paged", QuarkdownNaming.enumValueName("PAGED"))
    }
}

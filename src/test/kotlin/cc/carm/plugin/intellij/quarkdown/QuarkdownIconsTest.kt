package cc.carm.plugin.intellij.quarkdown

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies every icon referenced in plugin.xml actually loads (a missing icon would show
 * as a broken/empty image in the floating table toolbar).
 */
class QuarkdownIconsTest {

    @Test
    fun `all table editor icons load`() {
        val icons = mapOf(
            "ALIGN_LEFT" to QuarkdownIcons.ALIGN_LEFT,
            "ALIGN_CENTER" to QuarkdownIcons.ALIGN_CENTER,
            "ALIGN_RIGHT" to QuarkdownIcons.ALIGN_RIGHT,
            "ADD_ROW_ABOVE" to QuarkdownIcons.ADD_ROW_ABOVE,
            "ADD_ROW_BELOW" to QuarkdownIcons.ADD_ROW_BELOW,
            "ADD_COLUMN_LEFT" to QuarkdownIcons.ADD_COLUMN_LEFT,
            "ADD_COLUMN_RIGHT" to QuarkdownIcons.ADD_COLUMN_RIGHT
        )
        for ((name, icon) in icons) {
            assertNotNull("$name icon must load", icon)
            // IconLoader may return a lazy stub; ensure the actual pixels are available.
            assertTrue("$name icon should have width", icon.iconWidth > 0)
            assertTrue("$name icon should have height", icon.iconHeight > 0)
        }
    }

    @Test
    fun `file and gutter icons load`() {
        assertTrue(QuarkdownIcons.FILE.iconWidth > 0)
        assertTrue(QuarkdownIcons.IMAGE.iconWidth > 0)
        assertTrue(QuarkdownIcons.TABLE.iconWidth > 0)
    }
}


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
            "TABLE_MOVE_ROW_UP" to QuarkdownIcons.TABLE_MOVE_ROW_UP,
            "TABLE_MOVE_ROW_DOWN" to QuarkdownIcons.TABLE_MOVE_ROW_DOWN,
            "TABLE_MOVE_COLUMN_LEFT" to QuarkdownIcons.TABLE_MOVE_COLUMN_LEFT,
            "TABLE_MOVE_COLUMN_RIGHT" to QuarkdownIcons.TABLE_MOVE_COLUMN_RIGHT,
            "TABLE_SELECT" to QuarkdownIcons.TABLE_SELECT,
            "TABLE_REMOVE" to QuarkdownIcons.TABLE_REMOVE,
            "TABLE_ALIGN_LEFT" to QuarkdownIcons.TABLE_ALIGN_LEFT,
            "TABLE_ALIGN_CENTER" to QuarkdownIcons.TABLE_ALIGN_CENTER,
            "TABLE_ALIGN_RIGHT" to QuarkdownIcons.TABLE_ALIGN_RIGHT,
            "TABLE_ADD_ROW_ABOVE" to QuarkdownIcons.TABLE_ADD_ROW_ABOVE,
            "TABLE_ADD_ROW_BELOW" to QuarkdownIcons.TABLE_ADD_ROW_BELOW,
            "TABLE_ADD_COLUMN_LEFT" to QuarkdownIcons.TABLE_ADD_COLUMN_LEFT,
            "TABLE_ADD_COLUMN_RIGHT" to QuarkdownIcons.TABLE_ADD_COLUMN_RIGHT,
            "TABLE_FORMAT" to QuarkdownIcons.TABLE_FORMAT
        )
        for ((name, icon) in icons) {
            assertNotNull("$name icon must load", icon)
            // IconLoader may return a lazy stub; ensure the actual pixels are available.
            assertTrue("$name icon should have width", icon.iconWidth > 0)
            assertTrue("$name icon should have height", icon.iconHeight > 0)
        }
    }

    @Test
    fun `text formatting icons load`() {
        val icons = mapOf(
            "TEXT_BOLD" to QuarkdownIcons.TEXT_BOLD,
            "TEXT_ITALIC" to QuarkdownIcons.TEXT_ITALIC,
            "TEXT_STRIKETHROUGH" to QuarkdownIcons.TEXT_STRIKETHROUGH,
            "TEXT_CODE" to QuarkdownIcons.TEXT_CODE,
            "TEXT_LINK" to QuarkdownIcons.TEXT_LINK
        )
        for ((name, icon) in icons) {
            assertNotNull("$name icon must load", icon)
            assertTrue("$name icon should have width", icon.iconWidth > 0)
            assertTrue("$name icon should have height", icon.iconHeight > 0)
        }
    }

    @Test
    fun `file and gutter icons load`() {
        assertTrue(QuarkdownIcons.FILE.iconWidth > 0)
        assertTrue(QuarkdownIcons.IMAGE_MARKER.iconWidth > 0)
        assertTrue(QuarkdownIcons.TABLE_MARKER.iconWidth > 0)
        assertTrue(QuarkdownIcons.CODE_MARKER.iconWidth > 0)
        assertTrue(QuarkdownIcons.EQUATION_MARKER.iconWidth > 0)
    }
}

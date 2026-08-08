package cc.carm.plugin.intellij.quarkdown.lang.editor

import cc.carm.plugin.intellij.quarkdown.action.table.TableDialog
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

/**
 * Verifies parsing/building of a Quarkdown table's trailing `"label" {#id}` line.
 */
class QuarkdownTableLabelTest : BasePlatformTestCase() {

    private val plainTable = "| Device | IP Address    |\n" +
        "|:------:|:-------------:|\n" +
        "| PC     | 192.168.1.100 |\n"

    fun `test table without label line has null label`() {
        val block = QuarkdownTableModificationUtils.findTableBlocks(plainTable).first()
        assertNull(block.labelLine)
        assertEquals(block.endOffset, block.fullEndOffset)
    }

    fun `test table with label and id is detected`() {
        val text = plainTable + "\"Device IP Table\" {#device-ip-table}\n"
        val block = QuarkdownTableModificationUtils.findTableBlocks(text).first()
        assertNotNull(block.labelLine)
        assertTrue(block.labelLine!!.contains("Device IP Table"))
        assertTrue(block.labelLine!!.contains("#device-ip-table"))
    }

    fun `test label line shares indentation`() {
        val text = "  | A |\n  |---|\n  | 1 |\n  \"Label\" {#tbl}\n"
        val block = QuarkdownTableModificationUtils.findTableBlocks(text).first()
        assertNotNull(block.labelLine)
        assertTrue(block.labelLine!!.startsWith("  "))
        assertTrue(block.labelLineStart > block.endOffset)
    }

    fun `test table dialog builds label line with same indent`() {
        val text = "  | A |\n  |---|\n  | 1 |\n"
        val block = QuarkdownTableModificationUtils.findTableBlocks(text).first()

        val dialog = TableDialog(project)
        dialog.parseExistingTable(block.lines, block.labelLine)
        dialog.setLabelForTest("Device IP Table")
        dialog.setIdForTest("device-ip-table")

        val lines = dialog.buildTableLines()
        assertEquals(4, lines.size)
        assertTrue(lines[3].startsWith("  "))
        assertTrue(lines[3].contains("\"Device IP Table\""))
        assertTrue(lines[3].contains("{#device-ip-table}"))
    }

    fun `test dialog with empty label and id keeps only the table`() {
        val text = "| A |\n|---|\n| 1 |\n"
        val block = QuarkdownTableModificationUtils.findTableBlocks(text).first()
        val dialog = TableDialog(project)
        dialog.parseExistingTable(block.lines, block.labelLine)
        val lines = dialog.buildTableLines()
        assertEquals(3, lines.size)
    }
}

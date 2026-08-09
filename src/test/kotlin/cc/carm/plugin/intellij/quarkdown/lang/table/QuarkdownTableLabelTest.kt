package cc.carm.plugin.intellij.quarkdown.lang.table

import cc.carm.plugin.intellij.quarkdown.action.table.TableDialog
import com.intellij.testFramework.fixtures.BasePlatformTestCase

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
        assertTrue(block.labelLine.contains("#device-ip-table"))
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

    fun `test format re-aligns internal lines`() {
        // An un-aligned table (cells not padded to a common column width).
        val raw = listOf("| A | B |", "|---|---|", "|x|yy|")
        val dialog = TableDialog(project)
        dialog.parseExistingTable(raw, null)

        dialog.formatTable()

        val lines = dialog.buildTableLines()
        assertEquals(3, lines.size)
        // Every column must share one width across header/separator/data rows
        // (the separator marker's minimum width is 3 dashes).
        assertEquals("| A   | B   |", lines[0])
        assertEquals("| --- | --- |", lines[1])
        assertEquals("| x   | yy  |", lines[2])
    }

    fun `test format writes re-aligned table into the document`() {
        myFixture.configureByText("t.qd", "| A | B |\n|---|---|\n|x|yy|")
        val document = myFixture.editor.document
        val block = QuarkdownTableModificationUtils.findTableBlocks(document.immutableCharSequence).first()

        val dialog = TableDialog(project)
        dialog.parseExistingTable(block.lines, block.labelLine)
        dialog.setTarget(document, block)
        dialog.formatTable()

        val text = document.text
        assertTrue("document should contain aligned header, got: $text", text.contains("| A   | B   |"))
        assertTrue("document should contain aligned separator, got: $text", text.contains("| --- | --- |"))
        assertTrue("document should contain aligned cell, got: $text", text.contains("| x   | yy  |"))
    }

    fun `test format preserves indentation`() {
        val raw = listOf("  | A | B |", "  |---|---|", "  |x|yy|")
        val dialog = TableDialog(project)
        dialog.parseExistingTable(raw, null)

        dialog.formatTable()

        val lines = dialog.buildTableLines()
        assertTrue("indent should be preserved", lines.all { it.startsWith("  ") })
        assertTrue(lines[0].contains("| A   | B   |"))
    }

    fun `test repeated format on live document does not corrupt`() {
        myFixture.configureByText(
            "t.qd",
            "| 设备名称 | 地址 |\n|:---:|:---:|\n| 肥桶1 | Q0.0 |\n| 循环桶 | Q0.5 |\n"
        )
        val document = myFixture.editor.document
        val block = QuarkdownTableModificationUtils.findTableBlocks(document.immutableCharSequence).first()

        val dialog = TableDialog(project)
        dialog.parseExistingTable(block.lines, block.labelLine)
        dialog.setTarget(document, block)

        val before = document.text
        dialog.formatTable()
        val once = document.text
        dialog.formatTable()
        val twice = document.text
        dialog.formatTable()
        val thrice = document.text

        assertTrue("the first format must re-align the table", once != before)
        assertEquals("a second format must not change the text", once, twice)
        assertEquals("a third format must not change the text", twice, thrice)

        // Exactly the 4 table lines must remain - no leftover fragments such as
        // "环桶   |" or swallowed following lines.
        val pipeLines = thrice.split("\n").filter { it.contains('|') }
        assertEquals("document must contain exactly 4 table lines", 4, pipeLines.size)
        assertEquals("| 循环桶   | Q0.5  |", pipeLines[3].trim())
    }

    fun `test repeated format preserves label line`() {
        val tableLines = listOf(
            "| 设备名称 | 地址 |",
            "|:---:|:---:|",
            "| 肥桶1 | Q0.0 |",
            "| 循环桶 | Q0.5 |"
        )
        val dialog = TableDialog(project)
        dialog.parseExistingTable(tableLines, "\"设备表\" {#device-table}")
        dialog.formatTable()
        dialog.formatTable()

        val lines = dialog.buildTableLines()
        assertEquals(5, lines.size)
        assertEquals(4, lines.take(4).filter { it.contains('|') }.size)
        assertTrue("label line must be appended exactly once", lines.last().contains("\"设备表\" {#device-table}"))
    }
}

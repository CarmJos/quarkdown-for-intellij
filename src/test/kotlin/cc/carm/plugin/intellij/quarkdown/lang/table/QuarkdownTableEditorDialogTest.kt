package cc.carm.plugin.intellij.quarkdown.lang.table

import cc.carm.plugin.intellij.quarkdown.action.table.TableEditorDialog
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Verifies the table editor dialog: grid round-trips, caption/id handling, and the
 * Markdown ⇄ `.tablebyrows` conversion (issue #22).
 */
class QuarkdownTableEditorDialogTest : BasePlatformTestCase() {

    private val plainTable = "| Device | IP Address    |\n" +
            "|:------:|:-------------:|\n" +
            "| PC     | 192.168.1.100 |\n"

    private fun parseMarkdown(text: String) =
        QuarkdownTableParser.parse(QuarkdownTableModificationUtils.findTableBlocks(text).first().lines)!!

    // ------------------------------------------------------------------
    // Markdown round-trip & caption line
    // ------------------------------------------------------------------

    fun `test markdown table round-trips with same indent`() {
        val text = "  | A |\n  |---|\n  | 1 |\n"
        val block = QuarkdownTableModificationUtils.findTableBlocks(text).first()

        val dialog = TableEditorDialog(project)
        dialog.loadMarkdownTable(QuarkdownTableParser.parse(block.lines)!!, block.labelLine, "  ")

        val lines = dialog.buildReplacementLines()
        assertEquals(3, lines.size)
        assertTrue("indent should be preserved", lines.all { it.startsWith("  ") })
        assertTrue(lines[0].contains("| A"))
    }

    fun `test caption and id are written with the table indent`() {
        val block = QuarkdownTableModificationUtils.findTableBlocks(plainTable).first()
        val dialog = TableEditorDialog(project)
        dialog.loadMarkdownTable(parseMarkdown(plainTable), null, "")
        dialog.setLabelForTest("Device IP Table")
        dialog.setIdForTest("device-ip-table")

        val lines = dialog.buildReplacementLines()
        val last = lines.last()
        assertTrue(last.contains("\"Device IP Table\""))
        assertTrue(last.contains("{#device-ip-table}"))
        assertEquals(4, lines.size)
    }

    fun `test existing caption line is loaded`() {
        val text = plainTable + "\"Device IP Table\" {#device-ip-table}\n"
        val block = QuarkdownTableModificationUtils.findTableBlocks(text).first()

        val dialog = TableEditorDialog(project)
        dialog.loadMarkdownTable(QuarkdownTableParser.parse(block.lines)!!, block.labelLine, "")

        val lines = dialog.buildReplacementLines()
        assertTrue(lines.last().contains("\"Device IP Table\" {#device-ip-table}"))
    }

    fun `test id only and label only caption lines`() {
        val withId = TableEditorDialog(project).apply {
            loadMarkdownTable(parseMarkdown(plainTable), "{#table-id}", "")
        }
        assertTrue(withId.buildReplacementLines().last().contains("{#table-id}"))

        val withLabel = TableEditorDialog(project).apply {
            loadMarkdownTable(parseMarkdown(plainTable), "\"Device Table\"", "")
        }
        assertTrue(withLabel.buildReplacementLines().last().contains("\"Device Table\""))
    }

    fun `test alignments survive markdown round-trip`() {
        val block = QuarkdownTableModificationUtils.findTableBlocks(plainTable).first()
        val dialog = TableEditorDialog(project)
        dialog.loadMarkdownTable(QuarkdownTableParser.parse(block.lines)!!, null, "")

        val rebuilt = dialog.buildOutputLines()
        val reparsed = QuarkdownTableParser.parse(rebuilt)!!
        assertEquals(
            listOf(QuarkdownTableParser.Alignment.CENTER, QuarkdownTableParser.Alignment.CENTER),
            reparsed.alignments
        )
    }

    // ------------------------------------------------------------------
    // Conversion: Markdown -> .tablebyrows
    // ------------------------------------------------------------------

    fun `test converting markdown table to tablebyrows`() {
        val dialog = TableEditorDialog(project)
        dialog.loadMarkdownTable(parseMarkdown(plainTable), null, "")
        dialog.selectFormat(TableEditorDialog.OutputFormat.TABLE_BY_ROWS)

        val lines = dialog.buildOutputLines()
        assertEquals(".tablebyrows {", lines.first())
        assertTrue(lines.contains("    - Device"))
        assertTrue(lines.contains("    - IP Address"))
        assertTrue(lines.contains("}"))
        assertTrue(lines.contains("    - - PC"))
        assertTrue(lines.contains("      - 192.168.1.100"))

        // The converted block must re-parse to the same data.
        val reparsed = QuarkdownTableByRows.findBlocks(lines.joinToString("\n")).single()
        assertTrue(reparsed.isEditable)
        assertEquals(listOf("Device", "IP Address"), reparsed.headerItems)
        assertEquals(listOf(listOf("PC", "192.168.1.100")), reparsed.rows)
    }

    fun `test caption is dropped and warned for tablebyrows output`() {
        val dialog = TableEditorDialog(project)
        dialog.loadMarkdownTable(parseMarkdown(plainTable), "\"Caption\" {#tbl}", "")
        dialog.selectFormat(TableEditorDialog.OutputFormat.TABLE_BY_ROWS)

        assertTrue(dialog.isCaptionWarningVisibleForTest())
        val lines = dialog.buildReplacementLines()
        assertTrue("no caption line may be written", lines.none { it.contains("{#tbl}") })

        // Switching back restores the caption fields.
        dialog.selectFormat(TableEditorDialog.OutputFormat.MARKDOWN)
        assertFalse(dialog.isCaptionWarningVisibleForTest())
        assertTrue(dialog.buildReplacementLines().last().contains("\"Caption\" {#tbl}"))
    }

    // ------------------------------------------------------------------
    // Conversion: .tablebyrows -> Markdown
    // ------------------------------------------------------------------

    fun `test converting tablebyrows table to markdown`() {
        val source = ".tablebyrows {\n" +
                "    - Name\n" +
                "    - Age\n" +
                "}\n" +
                "    - - John\n" +
                "      - 25\n" +
                "    - - Lisa\n" +
                "      - 32\n"
        val block = QuarkdownTableByRows.findBlocks(source).single()
        val table = QuarkdownTableByRows.toTable(block)!!

        val dialog = TableEditorDialog(project)
        dialog.loadTableByRows(table, null, "")
        dialog.selectFormat(TableEditorDialog.OutputFormat.MARKDOWN)

        val lines = dialog.buildOutputLines()
        val reparsed = QuarkdownTableParser.parse(lines)!!
        assertEquals(listOf("Name", "Age"), reparsed.headers)
        assertEquals(listOf(listOf("John", "25"), listOf("Lisa", "32")), reparsed.rows)
    }

    fun `test tablebyrows without headers gets empty header row in markdown`() {
        val source = ".tablebyrows\n" +
                "    - - John\n" +
                "      - 25\n"
        val block = QuarkdownTableByRows.findBlocks(source).single()

        val dialog = TableEditorDialog(project)
        dialog.loadTableByRows(QuarkdownTableByRows.toTable(block)!!, null, "")

        // Row 0 is always the header: a blank one was added for the headerless call.
        assertEquals(listOf("", ""), dialog.getGridRowsForTest()[0])
        dialog.selectFormat(TableEditorDialog.OutputFormat.MARKDOWN)
        val reparsed = QuarkdownTableParser.parse(dialog.buildOutputLines())!!
        assertEquals(listOf("", ""), reparsed.headers)
        assertEquals(listOf(listOf("John", "25")), reparsed.rows)
    }

    fun `test headerless tablebyrows keeps all rows as data below blank header`() {
        val source = ".tablebyrows\n" +
                "    - - John\n" +
                "      - 25\n" +
                "    - - Lisa\n" +
                "      - 32\n"
        val block = QuarkdownTableByRows.findBlocks(source).single()
        val dialog = TableEditorDialog(project)
        dialog.loadTableByRows(QuarkdownTableByRows.toTable(block)!!, null, "")

        assertEquals(3, dialog.getGridRowsForTest().size)
        assertEquals(listOf("John", "25"), dialog.getGridRowsForTest()[1])
        // With blank headers the tablebyrows output stays headerless.
        val rebuilt = QuarkdownTableByRows.findBlocks(dialog.buildOutputLines().joinToString("\n")).single()
        assertTrue(rebuilt.headersSource is QuarkdownTableByRows.HeadersSource.Absent)
        assertEquals(listOf(listOf("John", "25"), listOf("Lisa", "32")), rebuilt.rows)
    }

    // ------------------------------------------------------------------
    // Grid display
    // ------------------------------------------------------------------

    fun `test grid displays every column of a wide table`() {
        // Regression: after loading, the grid kept its construction-time column
        // model and only showed the first column.
        val text = "| Header 1 | Header 2 | Header 3 | Header 4 | Header 5 |\n" +
                "| -------- | :------: | -------- | -------- | -------- |\n" +
                "| Value A  | Value A  | Value A  | Value A  | Value A  |\n" +
                "| Value B  | Value B  | Value B  | Value B  | Value B  |\n"
        val block = QuarkdownTableModificationUtils.findTableBlocks(text).first()

        val dialog = TableEditorDialog(project)
        dialog.loadMarkdownTable(QuarkdownTableParser.parse(block.lines)!!, block.labelLine, "")

        assertEquals(5, dialog.getColumnCountForTest())
        val rows = dialog.getGridRowsForTest()
        assertEquals(3, rows.size)
        assertEquals(List(5) { "Header ${it + 1}" }, rows[0])
        assertEquals(List(5) { "Value A" }, rows[1])
        assertEquals(List(5) { "Value B" }, rows[2])
    }

    fun `test column letter header is hidden`() {
        val dialog = TableEditorDialog(project)
        dialog.loadMarkdownTable(parseMarkdown(plainTable), null, "")
        assertTrue("the A/B/C column-letter header must be hidden", dialog.isColumnHeaderHiddenForTest())
    }

    fun `test insert and delete column operations`() {
        val dialog = TableEditorDialog(project)
        dialog.loadMarkdownTable(parseMarkdown(plainTable), null, "")
        assertEquals(2, dialog.getColumnCountForTest())

        dialog.insertColumnForTest(1)
        assertEquals(3, dialog.getColumnCountForTest())
        assertEquals(3, dialog.getGridRowsForTest()[0].size)

        dialog.removeColumnForTest(1)
        assertEquals(2, dialog.getColumnCountForTest())
        assertEquals(2, dialog.getGridRowsForTest()[0].size)
    }

    fun `test delete last column is prevented`() {
        val source = "| A |\n|---|\n| 1 |\n"
        val block = QuarkdownTableModificationUtils.findTableBlocks(source).first()
        val dialog = TableEditorDialog(project)
        dialog.loadMarkdownTable(QuarkdownTableParser.parse(block.lines)!!, block.labelLine, "")

        dialog.removeColumnForTest(0)
        assertEquals("the single remaining column must be kept", 1, dialog.getColumnCountForTest())
    }

    fun `test insert and delete row operations`() {
        val dialog = TableEditorDialog(project)
        dialog.loadMarkdownTable(parseMarkdown(plainTable), null, "")
        val initial = dialog.getGridRowsForTest().size

        dialog.insertRowForTest(1)
        assertEquals(initial + 1, dialog.getGridRowsForTest().size)

        dialog.deleteRowForTest(1)
        assertEquals(initial, dialog.getGridRowsForTest().size)

        // The header row (index 0) is never removable.
        dialog.deleteRowForTest(0)
        assertEquals(initial, dialog.getGridRowsForTest().size)
    }

    fun `test auto-fit caps long columns and grows wrapped rows`() {
        // Column 1 holds a 30-char cell (wraps, capped at 16 chars); column 2 has a
        // 16-char header. Both must end up the same (capped) width, and the row that
        // holds the 30-char cell must be taller than a single-line row.
        val long = "C".repeat(30)
        val sixteen = "D".repeat(16)
        val text = "| H | Header2 | $sixteen |\n" +
                "|---|---------|------------------|\n" +
                "| x | short | z |\n" +
                "| y | $long | w |\n"
        val block = QuarkdownTableModificationUtils.findTableBlocks(text).first()

        val dialog = TableEditorDialog(project)
        dialog.loadMarkdownTable(QuarkdownTableParser.parse(block.lines)!!, block.labelLine, "")

        assertEquals(
            "both long columns must be capped to the same width",
            dialog.getColumnWidthForTest(2),
            dialog.getColumnWidthForTest(1)
        )
        assertTrue(
            "the wrapped 30-char row must be taller than a single-line row",
            dialog.getRowHeightForTest(2) > dialog.getRowHeightForTest(1)
        )
    }

    fun `test auto-fit sizes wider content wider below the wrap cap`() {
        val text = "| A | WideHeader |\n" +
                "|---|------------|\n" +
                "| x | wide value |\n"
        val block = QuarkdownTableModificationUtils.findTableBlocks(text).first()

        val dialog = TableEditorDialog(project)
        dialog.loadMarkdownTable(QuarkdownTableParser.parse(block.lines)!!, block.labelLine, "")

        assertTrue(
            "the wider column must be rendered wider than the narrow one",
            dialog.getColumnWidthForTest(1) > dialog.getColumnWidthForTest(0)
        )
    }

    fun `test format table writes the edited table into the document`() {
        // An in-memory (non file-backed) document is used deliberately: writing to a
        // fixture file triggers the OS file-indexer/antivirus to hold a handle on the
        // modified file, which then blocks the test framework from deleting its temp
        // directory on teardown. The live-write logic under test is identical.
        val document = com.intellij.openapi.editor.impl.DocumentImpl("| A | B |\n|---|---|\n|x|yy|")
        val block = QuarkdownTableModificationUtils.findTableBlocks(document.immutableCharSequence).first()

        val dialog = TableEditorDialog(project)
        dialog.loadMarkdownTable(QuarkdownTableParser.parse(block.lines)!!, block.labelLine, "")
        dialog.setTarget(document, block.startOffset)
        dialog.formatTable()

        val text = document.text
        assertTrue("document should contain aligned header, got: $text", text.contains("| A   | B   |"))
        assertTrue("document should contain aligned separator, got: $text", text.contains("| --- | --- |"))
        assertTrue("document should contain aligned cell, got: $text", text.contains("| x   | yy  |"))
    }

    fun `test format table applies live conversion without corrupting offsets`() {
        val document = com.intellij.openapi.editor.impl.DocumentImpl("| A | B |\n|---|---|\n|x|yy|\ntrailing text")
        val block = QuarkdownTableModificationUtils.findTableBlocks(document.immutableCharSequence).first()

        val dialog = TableEditorDialog(project)
        dialog.loadMarkdownTable(QuarkdownTableParser.parse(block.lines)!!, block.labelLine, "")
        dialog.setTarget(document, block.startOffset)
        dialog.selectFormat(TableEditorDialog.OutputFormat.TABLE_BY_ROWS)
        dialog.formatTable()

        // The live write converted the block; a second write must re-resolve the
        // `.tablebyrows` block and never touch the trailing text.
        dialog.formatTable()
        val text = document.text
        assertTrue(text.contains(".tablebyrows"))
        assertTrue("trailing text must survive, got: $text", text.endsWith("trailing text"))
    }

    fun `test blank line inside markdown table does not truncate conversion`() {
        // Regression (issue: only the rows before an accidental blank line were
        // converted). Every row must survive the Markdown -> .tablebyrows conversion.
        val md = buildString {
            append("| Name | Age |\n| ---- | --- |\n")
            for (i in 1..8) {
                append("| P$i | $i |\n")
                if (i == 3) append("\n")
            }
        }
        val block = QuarkdownTableModificationUtils.findTableBlocks(md).first()

        val dialog = TableEditorDialog(project)
        dialog.loadMarkdownTable(QuarkdownTableParser.parse(block.lines)!!, block.labelLine, "")
        dialog.selectFormat(TableEditorDialog.OutputFormat.TABLE_BY_ROWS)

        val out = dialog.buildOutputLines()
        val reparsed = QuarkdownTableByRows.findBlocks(out.joinToString("\n")).single()
        assertEquals("all 8 rows must survive the conversion", 8, reparsed.rows?.size)
    }

    fun `test rows wider than header convert without losing cells`() {
        // Regression: the header had 3 columns but the data rows 5 — the extra cells
        // used to be truncated during parsing and never reached the converted table.
        val md = "| H1 | H2 | H3 |\n" +
                "|----|----|----|\n" +
                "| a | b | c | d | e |\n" +
                "| f | g | h | i | j |\n"
        val block = QuarkdownTableModificationUtils.findTableBlocks(md).first()

        val dialog = TableEditorDialog(project)
        dialog.loadMarkdownTable(QuarkdownTableParser.parse(block.lines)!!, block.labelLine, "")
        assertEquals("the grid must show all 5 columns", 5, dialog.getColumnCountForTest())

        dialog.selectFormat(TableEditorDialog.OutputFormat.TABLE_BY_ROWS)
        val out = dialog.buildOutputLines()
        val reparsed = QuarkdownTableByRows.findBlocks(out.joinToString("\n")).single()
        val rows = reparsed.rows!!
        assertEquals(2, rows.size)
        assertEquals(listOf("a", "b", "c", "d", "e"), rows[0])
        assertEquals(listOf("f", "g", "h", "i", "j"), rows[1])
    }

    // ------------------------------------------------------------------
    // Headers variable reference
    // ------------------------------------------------------------------

    fun `test unchanged headers keep variable reference`() {
        val source = ".var {headers}\n" +
                "    - Name\n" +
                "    - Age\n" +
                "\n" +
                ".tablebyrows {.headers}\n" +
                "    - - John\n" +
                "      - 25\n"
        val block = QuarkdownTableByRows.findBlocks(source).last()
        val table = QuarkdownTableByRows.toTable(block)!!

        val dialog = TableEditorDialog(project)
        dialog.loadTableByRows(table, "headers", "")

        val lines = dialog.buildOutputLines()
        assertEquals(".tablebyrows {.headers}", lines.first())
    }

    fun `test edited headers replace variable reference with literal list`() {
        val source = ".var {headers}\n" +
                "    - Name\n" +
                "    - Age\n" +
                "\n" +
                ".tablebyrows {.headers}\n" +
                "    - - John\n" +
                "      - 25\n"
        val block = QuarkdownTableByRows.findBlocks(source).last()
        val table = QuarkdownTableByRows.toTable(block)!!

        val dialog = TableEditorDialog(project)
        dialog.loadTableByRows(table, "headers", "")
        dialog.setCellForTest(0, 1, "City")

        val lines = dialog.buildOutputLines()
        assertEquals(".tablebyrows {", lines.first())
        assertTrue(lines.contains("    - City"))
        assertFalse(lines.any { it.contains(".headers") })
    }

    // ------------------------------------------------------------------
    // Grid editing
    // ------------------------------------------------------------------

    fun `test grid edits are reflected in output`() {
        val dialog = TableEditorDialog(project)
        dialog.loadMarkdownTable(parseMarkdown(plainTable), null, "")

        // Edit a cell and add a row through the model (as the grid does).
        dialog.setCellForTest(1, 0, "Server")

        val reparsed = QuarkdownTableParser.parse(dialog.buildOutputLines())!!
        assertEquals(listOf("Server", "192.168.1.100"), reparsed.rows[0])
    }
}

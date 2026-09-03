package cc.carm.plugin.intellij.quarkdown.lang.table

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for parsing/building `.tablebyrows` calls, kept free of the IntelliJ platform
 * so the syntax logic is verified in isolation.
 */
class QuarkdownTableByRowsTest {

    // ------------------------------------------------------------------
    // Detection & parsing
    // ------------------------------------------------------------------

    @Test
    fun `parses variable headers and body rows`() {
        val text = ".var {headers}\n" +
                "    - Name\n" +
                "    - Age\n" +
                "\n" +
                ".tablebyrows {.headers}\n" +
                "    - - John\n" +
                "      - 25\n" +
                "    - - Lisa\n" +
                "      - 32\n"

        val block = QuarkdownTableByRows.findBlocks(text).single()
        assertTrue(block.isEditable)
        assertEquals(listOf("Name", "Age"), block.headerItems)
        assertEquals(listOf(listOf("John", "25"), listOf("Lisa", "32")), block.rows)
        val headersSource = block.headersSource as QuarkdownTableByRows.HeadersSource.Reference
        assertEquals("headers", headersSource.name)
    }

    @Test
    fun `parses the issue example with two-space indentation`() {
        val text = ".var {headers}\n" +
                "  - name\n" +
                "  - Age\n" +
                "  - City\n" +
                "\n" +
                ".tablebyrows {.headers}\n" +
                "  - - John\n" +
                "    - 25\n" +
                "    - New York\n" +
                "  - - Lisa\n" +
                "    - 32\n" +
                "    - Los Angeles\n"

        val block = QuarkdownTableByRows.findBlocks(text).single()
        assertTrue(block.isEditable)
        assertEquals(listOf("name", "Age", "City"), block.headerItems)
        assertEquals(
            listOf(listOf("John", "25", "New York"), listOf("Lisa", "32", "Los Angeles")),
            block.rows
        )
    }

    @Test
    fun `parses literal headers in braces followed by body`() {
        val text = ".tablebyrows {\n" +
                "    - Name\n" +
                "    - Age\n" +
                "}\n" +
                "    - - John\n" +
                "      - 25\n"

        val block = QuarkdownTableByRows.findBlocks(text).single()
        assertTrue(block.isEditable)
        assertEquals(listOf("Name", "Age"), block.headerItems)
        assertEquals(listOf(listOf("John", "25")), block.rows)
        assertTrue(block.headersSource is QuarkdownTableByRows.HeadersSource.Literal)
    }

    @Test
    fun `parses two brace arguments`() {
        val text = ".tablebyrows {\n" +
                "    - Name\n" +
                "} {\n" +
                "    - - John\n" +
                "      - 25\n" +
                "}\n"

        val block = QuarkdownTableByRows.findBlocks(text).single()
        assertTrue(block.isEditable)
        assertEquals(listOf("Name"), block.headerItems)
        assertEquals(listOf(listOf("John", "25")), block.rows)
    }

    @Test
    fun `parses headerless body-only call`() {
        val text = ".tablebyrows\n" +
                "    - - John\n" +
                "      - 25\n"

        val block = QuarkdownTableByRows.findBlocks(text).single()
        assertTrue(block.isEditable)
        assertTrue(block.headersSource is QuarkdownTableByRows.HeadersSource.Absent)
        assertEquals(emptyList<String>(), block.headerItems)
        assertEquals(listOf(listOf("John", "25")), block.rows)
    }

    @Test
    fun `parses named arguments`() {
        val text = ".tablebyrows headers:{\n" +
                "    - A\n" +
                "} rows:{\n" +
                "    - - 1\n" +
                "}\n"

        val block = QuarkdownTableByRows.findBlocks(text).single()
        assertTrue(block.isEditable)
        assertEquals(listOf("A"), block.headerItems)
        assertEquals(listOf(listOf("1")), block.rows)
    }

    @Test
    fun `unresolvable reference arguments are not editable`() {
        val text = ".tablebyrows {.x} {.y}\n" // dynamic/unresolvable args -> not editable
        val block = QuarkdownTableByRows.findBlocks(text).single()
        // `.x` / `.y` are unresolvable references/dynamic values.
        assertFalse(block.isEditable)
    }

    @Test
    fun `dynamic body with repeat is not editable`() {
        val text = ".tablebyrows\n" +
                "    .repeat {3}\n" +
                "        y:\n" +
                "        .repeat {3}\n" +
                "            x:\n" +
                "            Cell .x:.y\n"

        val block = QuarkdownTableByRows.findBlocks(text).single()
        assertFalse(block.isEditable)
        assertNull(block.rows)
    }

    @Test
    fun `unresolved variable headers make the table non-editable`() {
        val text = ".tablebyrows {.missing}\n" +
                "    - - John\n" +
                "      - 25\n"

        val block = QuarkdownTableByRows.findBlocks(text).single()
        val headersSource = block.headersSource as QuarkdownTableByRows.HeadersSource.Reference
        assertNull(headersSource.resolved)
        assertFalse(block.isEditable)
    }

    @Test
    fun `empty call has no rows and is editable`() {
        val text = ".tablebyrows\n\nNext paragraph"
        val block = QuarkdownTableByRows.findBlocks(text).single()
        assertTrue(block.isEditable)
        assertEquals(emptyList<List<String>>(), block.rows)
    }

    @Test
    fun `block stops at dedented paragraph`() {
        val text = ".tablebyrows\n" +
                "    - - John\n" +
                "      - 25\n" +
                "\n" +
                "Next paragraph\n"
        val block = QuarkdownTableByRows.findBlocks(text).single()
        assertEquals(listOf(listOf("John", "25")), block.rows)
        // endOffset stops right after the last body line, before its newline.
        assertEquals(text.indexOf("      - 25") + "      - 25".length, block.endOffset)
    }

    @Test
    fun `captures block line range`() {
        val text = "Intro\n" +
                ".tablebyrows\n" +
                "    - - John\n" +
                "      - 25\n" +
                "Outro"
        val block = QuarkdownTableByRows.findBlocks(text).single()
        assertEquals(text.indexOf(".tablebyrows"), block.startOffset)
        assertEquals(3, block.lines.size)
        assertEquals(".tablebyrows", block.lines[0])
        assertEquals("      - 25", block.lines[2])
        // endOffset stops right after the last body line, before the newline before "Outro".
        assertEquals(text.length - "Outro".length - 1, block.endOffset)
    }

    @Test
    fun `indented call keeps its indent`() {
        val text = ".row\n" +
                "    .tablebyrows\n" +
                "        - - John\n" +
                "          - 25\n"
        val block = QuarkdownTableByRows.findBlocks(text).single()
        assertTrue(block.isEditable)
        assertEquals("    ", block.indent)
        assertEquals(listOf(listOf("John", "25")), block.rows)
    }

    @Test
    fun `rows with single inline cell parse as one-cell rows`() {
        val text = ".tablebyrows\n" +
                "    - John\n" +
                "    - Lisa\n"
        val block = QuarkdownTableByRows.findBlocks(text).single()
        assertEquals(listOf(listOf("John"), listOf("Lisa")), block.rows)
    }

    @Test
    fun `unknown argument makes the table non-editable`() {
        val text = ".tablebyrows {\n" +
                "    - A\n" +
                "} style:{fancy}\n" +
                "    - - 1\n"
        val block = QuarkdownTableByRows.findBlocks(text).single()
        assertFalse(block.isEditable)
    }

    // ------------------------------------------------------------------
    // Variable resolution
    // ------------------------------------------------------------------

    @Test
    fun `resolves var with body list`() {
        val text = ".var {headers}\n" +
                "    - Name\n" +
                "    - Age\n"
        assertEquals(listOf("Name", "Age"), QuarkdownTableByRows.resolveVar(text, "headers"))
    }

    @Test
    fun `resolves var with brace value`() {
        val text = ".var {headers} {\n" +
                "    - Name\n" +
                "    - Age\n" +
                "}\n"
        assertEquals(listOf("Name", "Age"), QuarkdownTableByRows.resolveVar(text, "headers"))
    }

    @Test
    fun `returns null for missing or non-list variable`() {
        assertNull(QuarkdownTableByRows.resolveVar(".var {other}\n    - X\n", "headers"))
        assertNull(QuarkdownTableByRows.resolveVar(".var {headers} {plain text}\n", "headers"))
    }

    // ------------------------------------------------------------------
    // Building
    // ------------------------------------------------------------------

    @Test
    fun `builds literal headers and rows`() {
        val table = QuarkdownTableParser.Table(
            headers = listOf("Name", "Age"),
            rows = listOf(listOf("John", "25"), listOf("Lisa", "32")),
            alignments = emptyList()
        )
        val lines = QuarkdownTableByRows.build(table)
        assertEquals(
            listOf(
                ".tablebyrows {",
                "    - Name",
                "    - Age",
                "}",
                "    - - John",
                "      - 25",
                "    - - Lisa",
                "      - 32"
            ),
            lines
        )
    }

    @Test
    fun `builds variable reference headers`() {
        val table = QuarkdownTableParser.Table(
            headers = listOf("Name"),
            rows = listOf(listOf("John")),
            alignments = emptyList()
        )
        val lines = QuarkdownTableByRows.build(table, headersReference = "headers")
        assertEquals(".tablebyrows {.headers}", lines.first())
        assertEquals(listOf(".tablebyrows {.headers}", "    - - John"), lines)
    }

    @Test
    fun `builds headerless table`() {
        val table = QuarkdownTableParser.Table(
            headers = emptyList(),
            rows = listOf(listOf("John", "25")),
            alignments = emptyList()
        )
        val lines = QuarkdownTableByRows.build(table)
        assertEquals(listOf(".tablebyrows", "    - - John", "      - 25"), lines)
    }

    @Test
    fun `blank headers are dropped`() {
        val table = QuarkdownTableParser.Table(
            headers = listOf("", ""),
            rows = listOf(listOf("John")),
            alignments = emptyList()
        )
        val lines = QuarkdownTableByRows.build(table, headersReference = "headers")
        assertEquals(".tablebyrows", lines.first())
    }

    @Test
    fun `trailing blank cells are dropped from rows`() {
        val table = QuarkdownTableParser.Table(
            headers = listOf("A", "B", "C"),
            rows = listOf(listOf("x", "", "")),
            alignments = emptyList()
        )
        val lines = QuarkdownTableByRows.build(table)
        // The stdlib fills missing cells, so trailing blanks are simply omitted.
        assertTrue(lines.contains("    - - x"))
        assertFalse(lines.any { it.contains("<!-- -->") })
    }

    @Test
    fun `inner blank cells use comment placeholders`() {
        val table = QuarkdownTableParser.Table(
            headers = listOf("A", "B", "C"),
            rows = listOf(listOf("", "y", "")),
            alignments = emptyList()
        )
        val lines = QuarkdownTableByRows.build(table)
        assertTrue(lines.contains("    - - <!-- -->"))
        assertTrue(lines.contains("      - y"))

        // The placeholder round-trips back to an empty cell.
        val reparsed = QuarkdownTableByRows.findBlocks(lines.joinToString("\n")).single()
        assertEquals(listOf(listOf("", "y")), reparsed.rows)
    }

    @Test
    fun `fully blank row keeps a single placeholder cell`() {
        val table = QuarkdownTableParser.Table(
            headers = listOf("A", "B"),
            rows = listOf(listOf("", "")),
            alignments = emptyList()
        )
        val lines = QuarkdownTableByRows.build(table)
        assertTrue(lines.contains("    - - <!-- -->"))
    }

    @Test
    fun `build preserves indentation`() {
        val table = QuarkdownTableParser.Table(
            headers = listOf("A"),
            rows = listOf(listOf("1")),
            alignments = emptyList()
        )
        val lines = QuarkdownTableByRows.build(table, indent = "  ")
        assertEquals("  .tablebyrows {", lines[0])
        assertEquals("      - A", lines[1])
        assertEquals("  }", lines[2])
        assertEquals("      - - 1", lines[3])
    }

    @Test
    fun `build output re-parses to the same table`() {
        val table = QuarkdownTableParser.Table(
            headers = listOf("Name", "Age"),
            rows = listOf(listOf("John", "25"), listOf("", "32")),
            alignments = emptyList()
        )
        val rebuilt = QuarkdownTableByRows.build(table)
        val reparsed = QuarkdownTableByRows.findBlocks(rebuilt.joinToString("\n")).single()
        assertTrue(reparsed.isEditable)
        assertEquals(table.headers, reparsed.headerItems)
        // Blank cells survive the round-trip as blank strings.
        assertEquals(table.rows.map { row -> row.map { it.trim() } }, reparsed.rows)
    }

    @Test
    fun `markdown to tablebyrows conversion round-trip`() {
        val markdown = listOf(
            "| Name | Age |",
            "|:-----|----:|",
            "| John | 25  |",
            "| Lisa | 32  |"
        )
        val parsed = QuarkdownTableParser.parse(markdown)!!
        val source = QuarkdownTableByRows.build(
            QuarkdownTableParser.Table(parsed.headers, parsed.rows, emptyList())
        )
        val reparsed = QuarkdownTableByRows.findBlocks(source.joinToString("\n")).single()
        assertTrue(reparsed.isEditable)
        assertEquals(parsed.headers, reparsed.headerItems)
        assertEquals(parsed.rows, reparsed.rows)
    }

    @Test
    fun `toTable converts editable block`() {
        val text = ".tablebyrows {\n" +
                "    - A\n" +
                "}\n" +
                "    - - 1\n" +
                "      - 2\n"
        val block = QuarkdownTableByRows.findBlocks(text).single()
        val table = QuarkdownTableByRows.toTable(block)!!
        assertEquals(listOf("A"), table.headers)
        assertEquals(listOf(listOf("1", "2")), table.rows)
        assertNull(QuarkdownTableByRows.toTable(block.copy(rows = null)))
    }
}

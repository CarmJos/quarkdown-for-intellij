package cc.carm.plugin.intellij.quarkdown.lang.editor

import cc.carm.plugin.intellij.quarkdown.lang.editor.QuarkdownTableParser.Alignment
import cc.carm.plugin.intellij.quarkdown.lang.editor.QuarkdownTableParser.Table
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuarkdownTableParserTest {

    private val basicTable = listOf(
        "| Name | Value |",
        "|------|-------|",
        "| Alpha | 1 |",
        "| Beta | 2 |"
    )

    @Test
    fun `parses basic table`() {
        val table = QuarkdownTableParser.parse(basicTable)
        assertNotNull(table)
        assertEquals(listOf("Name", "Value"), table!!.headers)
        assertEquals(2, table.rowCount)
        assertEquals(listOf("Alpha", "1"), table.rows[0])
        assertEquals(listOf("Beta", "2"), table.rows[1])
    }

    @Test
    fun `parses alignment markers`() {
        val table = QuarkdownTableParser.parse(
            listOf(
                "| L | C | R | N |",
                "|:---|:---:|---:|---|",
                "| a | b | c | d |"
            )
        )
        assertNotNull(table)
        assertEquals(
            listOf(Alignment.LEFT, Alignment.CENTER, Alignment.RIGHT, Alignment.NONE),
            table!!.alignments
        )
    }

    @Test
    fun `handles no explicit alignment`() {
        val table = QuarkdownTableParser.parse(basicTable)
        assertEquals(listOf(Alignment.NONE, Alignment.NONE), table!!.alignments)
    }

    @Test
    fun `returns null when no separator row`() {
        assertNull(QuarkdownTableParser.parse(listOf("| a | b |", "| c | d |")))
    }

    @Test
    fun `returns null when header missing`() {
        assertNull(QuarkdownTableParser.parse(listOf("|------|", "| a | b |")))
    }

    @Test
    fun `handles outer pipes and whitespace`() {
        val table = QuarkdownTableParser.parse(
            listOf(
                "  |  H1  |  H2  |",
                "  |:-----|:-----|",
                "  |  x   |  y   |"
            )
        )
        assertNotNull(table)
        assertEquals(listOf("H1", "H2"), table!!.headers)
        assertEquals(listOf("x", "y"), table.rows[0])
    }

    @Test
    fun `pads rows to header width`() {
        val table = QuarkdownTableParser.parse(
            listOf(
                "| A | B | C |",
                "|---|---|---|",
                "| x | y |"
            )
        )
        assertNotNull(table)
        assertEquals(3, table!!.rows[0].size)
        assertEquals(listOf("x", "y", ""), table.rows[0])
    }

    @Test
    fun `round-trip build preserves content`() {
        val source = listOf(
            "| Name | Value |",
            "|:-----|:------|",
            "| Alpha | 1 |",
            "| Beta | 2 |"
        )
        val parsed = QuarkdownTableParser.parse(source)!!
        val rebuilt = QuarkdownTableParser.build(parsed)

        assertEquals(4, rebuilt.size)
        assertTrue(rebuilt[0].startsWith("| Name"))
        assertTrue(rebuilt[0].endsWith("|"))
        assertTrue(rebuilt[1].contains(":"))
        assertTrue(rebuilt[2].contains("Alpha"))
        assertTrue(rebuilt[3].contains("Beta"))

        // Re-parsing the rebuilt table must give the same data.
        val reparsed = QuarkdownTableParser.parse(rebuilt)!!
        assertEquals(parsed.headers, reparsed.headers)
        assertEquals(parsed.rows, reparsed.rows)
        assertEquals(parsed.alignments, reparsed.alignments)
    }

    @Test
    fun `build pads cells to column width`() {
        val table = Table(
            headers = listOf("H", "LongerHeader"),
            rows = listOf(listOf("abc", "x"), listOf("", "yy")),
            alignments = listOf(Alignment.NONE, Alignment.CENTER)
        )
        val lines = QuarkdownTableParser.build(table)

        // All lines should have equal-width cells for a given column.
        assertEquals(4, lines.size)
        assertTrue(lines.all { it.startsWith("| ") && it.endsWith(" |") })
        assertTrue(lines[1].contains(":---:"))
    }

    @Test
    fun `build handles empty cells`() {
        val table = Table(
            headers = listOf("A", "B"),
            rows = listOf(listOf("", "")),
            alignments = listOf(Alignment.NONE, Alignment.NONE)
        )
        val lines = QuarkdownTableParser.build(table)
        assertEquals(3, lines.size)
        val reparsed = QuarkdownTableParser.parse(lines)!!
        assertEquals(listOf("", ""), reparsed.rows[0])
    }

    @Test
    fun `single column table`() {
        val table = QuarkdownTableParser.parse(
            listOf("| H |", "|---|", "| a |")
        )
        assertNotNull(table)
        assertEquals(1, table!!.columnCount)
        assertEquals(listOf("H"), table.headers)
        assertEquals(listOf("a"), table.rows[0])
    }
}

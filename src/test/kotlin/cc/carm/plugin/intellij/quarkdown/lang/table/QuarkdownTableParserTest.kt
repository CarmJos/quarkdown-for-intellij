package cc.carm.plugin.intellij.quarkdown.lang.table

import cc.carm.plugin.intellij.quarkdown.lang.table.QuarkdownTableParser.Alignment
import cc.carm.plugin.intellij.quarkdown.lang.table.QuarkdownTableParser.Table
import org.junit.Assert.*
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
        // The centered column must be at least as wide as its marker (":---:" min 5).
        assertTrue(lines[1].contains(":"))
        // Re-parsing the rebuilt table must preserve the alignment markers.
        val reparsed = QuarkdownTableParser.parse(lines)!!
        assertEquals(listOf(Alignment.NONE, Alignment.CENTER), reparsed.alignments)
    }

    @Test
    fun `build keeps separator structurally valid and aligned`() {
        // Regression: a centered column wider than its marker used to render as
        // ":---:--------" (dashes only on the right), which re-parses as LEFT and
        // makes the separator cell wider than the header/data cells.
        val table = Table(
            headers = listOf("设备", "IP地址"),
            rows = listOf(listOf("Jason", "192.168.1.100")),
            alignments = listOf(Alignment.CENTER, Alignment.CENTER)
        )
        val lines = QuarkdownTableParser.build(table)

        // Every row must align on the same pipe positions (same column widths).
        val pipePositions = lines.map { line ->
            line.mapIndexedNotNull { i, c -> if (c == '|') i else null }
        }
        assertEquals("all rows must align on the same pipe positions", pipePositions[0], pipePositions[1])
        assertEquals("all rows must align on the same pipe positions", pipePositions[0], pipePositions[2])

        // Re-parsing must preserve the centered alignment (this failed before the fix).
        val reparsed = QuarkdownTableParser.parse(lines)!!
        assertEquals(listOf(Alignment.CENTER, Alignment.CENTER), reparsed.alignments)
    }

    @Test
    fun `build renders alignment markers across the full column width`() {
        // Regression: separators used to be `padEnd`-padded after the marker, so a
        // centered column of width 11 became `:---:-------` (colon in the middle),
        // which re-parses as LEFT and makes the separator wider than its cells.
        val table = Table(
            headers = listOf("设备", "IP地址"),
            rows = listOf(listOf("192.168.1.1", "网关")),
            alignments = listOf(Alignment.CENTER, Alignment.RIGHT)
        )
        val lines = QuarkdownTableParser.build(table)

        val sep = lines[1].trim().removePrefix("|").removeSuffix("|")
            .split("|").map { it.trim() }
        // Column 0 is 11 wide (the data cell), column 1 is 4 wide (its header).
        assertEquals(":" + "-".repeat(9) + ":", sep[0])
        assertEquals("-".repeat(3) + ":", sep[1])

        // All rows must align on the same pipe positions (same column widths).
        val pipePositions = lines.map { line ->
            line.mapIndexedNotNull { i, c -> if (c == '|') i else null }
        }
        assertEquals(pipePositions[0], pipePositions[1])
        assertEquals(pipePositions[0], pipePositions[2])

        // Re-parsing must preserve both the headers and the alignment markers.
        val reparsed = QuarkdownTableParser.parse(lines)!!
        assertEquals(listOf("设备", "IP地址"), reparsed.headers)
        assertEquals(listOf(Alignment.CENTER, Alignment.RIGHT), reparsed.alignments)
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

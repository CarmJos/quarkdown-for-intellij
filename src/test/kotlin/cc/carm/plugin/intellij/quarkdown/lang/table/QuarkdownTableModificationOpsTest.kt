package cc.carm.plugin.intellij.quarkdown.lang.table

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuarkdownTableModificationOpsTest {

    private fun blockOf(text: String): QuarkdownTableModificationUtils.TableBlock {
        return QuarkdownTableModificationUtils.findTableBlocks(text).first()
    }

    @Test
    fun `insert row adds empty row at index`() {
        val text = "| A |\n|---|\n| 1 |\n| 2 |"
        val block = blockOf(text)
        // data rows: ["1"], ["2"] → insert at 0 → new row before "1"
        val result = QuarkdownTableParser.build(
            QuarkdownTableParser.parse(
                QuarkdownTableModificationUtils.findTableBlocks(
                    "| A |\n|---|\n| 1 |\n| 2 |"
                ).first().lines
            )!!.let {
                // emulate insertRow transform
                val emptyRow = List(it.columnCount) { "" }
                it.copy(rows = it.rows.toMutableList().apply { add(0, emptyRow) })
            }
        )
        assertEquals(5, result.size)
    }

    @Test
    fun `delete row removes data row`() {
        val table = QuarkdownTableParser.parse(listOf("| A |", "|---|", "| 1 |", "| 2 |"))!!
        val rows = table.rows.toMutableList()
        rows.removeAt(0)
        val result = QuarkdownTableParser.build(table.copy(rows = rows))
        assertTrue(result.size == 3)
        assertTrue(result.last().contains("2"))
    }

    @Test
    fun `swap rows exchanges data`() {
        val table = QuarkdownTableParser.parse(listOf("| A |", "|---|", "| 1 |", "| 2 |"))!!
        val rows = table.rows.toMutableList()
        val tmp = rows[0]
        rows[0] = rows[1]
        rows[1] = tmp
        val result = QuarkdownTableParser.build(table.copy(rows = rows))
        assertTrue(result[2].contains("2"))
        assertTrue(result[3].contains("1"))
    }

    @Test
    fun `insert column adds header and cells`() {
        val table = QuarkdownTableParser.parse(listOf("| A | B |", "|---|---|", "| 1 | 2 |"))!!
        val idx = 1
        val headers = table.headers.toMutableList().apply { add(idx, "") }
        val alignments = table.alignments.toMutableList().apply { add(idx, QuarkdownTableParser.Alignment.NONE) }
        val rows = table.rows.map { row -> row.toMutableList().apply { add(idx, "") } }
        val result = QuarkdownTableParser.build(table.copy(headers = headers, alignments = alignments, rows = rows))
        assertEquals(3, result.size)
        val reparsed = QuarkdownTableParser.parse(result)!!
        assertEquals(3, reparsed.columnCount)
        assertEquals(listOf("A", "", "B"), reparsed.headers)
    }

    @Test
    fun `delete column removes cells`() {
        val table = QuarkdownTableParser.parse(listOf("| A | B |", "|---|---|", "| 1 | 2 |"))!!
        val result = QuarkdownTableParser.build(
            table.copy(
                headers = table.headers.filterIndexed { i, _ -> i != 0 },
                alignments = table.alignments.filterIndexed { i, _ -> i != 0 },
                rows = table.rows.map { row -> row.filterIndexed { i, _ -> i != 0 } }
            )
        )
        val reparsed = QuarkdownTableParser.parse(result)!!
        assertEquals(1, reparsed.columnCount)
        assertEquals(listOf("B"), reparsed.headers)
        assertEquals(listOf("2"), reparsed.rows[0])
    }

    @Test
    fun `swap columns exchanges content`() {
        val table = QuarkdownTableParser.parse(listOf("| A | B |", "|---|---|", "| 1 | 2 |"))!!
        fun <T> swap(list: List<T>): List<T> {
            val m = list.toMutableList()
            val tmp = m[0]
            m[0] = m[1]
            m[1] = tmp
            return m
        }

        val result = QuarkdownTableParser.build(
            table.copy(
                headers = swap(table.headers),
                alignments = swap(table.alignments),
                rows = table.rows.map { swap(it) })
        )
        val reparsed = QuarkdownTableParser.parse(result)!!
        assertEquals(listOf("B", "A"), reparsed.headers)
        assertEquals(listOf("2", "1"), reparsed.rows[0])
    }
}

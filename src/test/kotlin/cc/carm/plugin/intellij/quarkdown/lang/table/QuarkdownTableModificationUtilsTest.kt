package cc.carm.plugin.intellij.quarkdown.lang.table

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuarkdownTableModificationUtilsTest {

    @Test
    fun `finds a simple table block`() {
        val text = "Before\n" +
                "| H1 | H2 |\n" +
                "|----|----|\n" +
                "| a  | b  |\n" +
                "After"
        val blocks = QuarkdownTableModificationUtils.findTableBlocks(text)
        assertEquals(1, blocks.size)
        val block = blocks[0]
        assertEquals(3, block.lines.size)
        assertEquals("| H1 | H2 |", block.lines[0])
        assertEquals("| a  | b  |", block.lines[2])
    }

    @Test
    fun `finds multiple table blocks`() {
        val text = "| A |\n|---|\n| 1 |\n\n| B | C |\n|---|---|\n| 2 | 3 |"
        val blocks = QuarkdownTableModificationUtils.findTableBlocks(text)
        assertEquals(2, blocks.size)
    }

    @Test
    fun `ignores lines without separator`() {
        val text = "| a | b |\n| c | d |"
        val blocks = QuarkdownTableModificationUtils.findTableBlocks(text)
        assertTrue(blocks.isEmpty())
    }

    @Test
    fun `separator may be separated by blank line`() {
        val text = "| H |\n\n|---|\n| x |"
        val blocks = QuarkdownTableModificationUtils.findTableBlocks(text)
        assertEquals(1, blocks.size)
    }

    @Test
    fun `reports correct offsets`() {
        val text = "intro\n| H |\n|---|\n| x |\noutro"
        val blocks = QuarkdownTableModificationUtils.findTableBlocks(text)
        assertEquals(1, blocks.size)
        val block = blocks[0]
        assertEquals(6, block.startOffset) // after "intro\n"
        assertEquals(3, block.lineStarts.size)
        assertEquals(6, block.lineStarts[0])  // "| H |"
        assertEquals(12, block.lineStarts[1]) // "|---|"
        assertEquals(18, block.lineStarts[2]) // "| x |"
        // endOffset = start of last line + last line length
        assertEquals(18 + "| x |".length, block.endOffset)
    }

    @Test
    fun `findTableBlocks returns empty for empty text`() {
        assertTrue(QuarkdownTableModificationUtils.findTableBlocks("").isEmpty())
    }

    @Test
    fun `blank line inside table does not truncate the block`() {
        val text = "| H |\n|---|\n| a |\n\n| b |\n| c |"
        val blocks = QuarkdownTableModificationUtils.findTableBlocks(text)
        assertEquals(1, blocks.size)
        // header + separator + a + b + c (the accidental blank line is skipped).
        assertEquals(5, blocks[0].lines.size)
        assertEquals("| c |", blocks[0].lines.last())
    }

    @Test
    fun `whitespace-only or consecutive blank lines inside table do not truncate`() {
        val whitespace = "| H |\n|---|\n| a |\n \t \n| b |"
        val blocks1 = QuarkdownTableModificationUtils.findTableBlocks(whitespace)
        assertEquals(1, blocks1.size)
        assertEquals(4, blocks1[0].lines.size)

        val consecutive = "| H |\n|---|\n| a |\n\n\n| b |"
        val blocks2 = QuarkdownTableModificationUtils.findTableBlocks(consecutive)
        assertEquals(1, blocks2.size)
        assertEquals(4, blocks2[0].lines.size)
    }

    @Test
    fun `blank line before a new table keeps the blocks separate`() {
        val text = "| A |\n|---|\n| 1 |\n\n| B |\n|---|\n| 2 |"
        val blocks = QuarkdownTableModificationUtils.findTableBlocks(text)
        assertEquals(2, blocks.size)
        assertEquals(3, blocks[0].lines.size)
        assertEquals(3, blocks[1].lines.size)
    }
}

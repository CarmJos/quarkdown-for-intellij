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
}

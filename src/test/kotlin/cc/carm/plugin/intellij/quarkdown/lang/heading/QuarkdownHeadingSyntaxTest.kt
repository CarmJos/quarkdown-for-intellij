package cc.carm.plugin.intellij.quarkdown.lang.heading

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuarkdownHeadingSyntaxTest {

    // ------------------------------------------------------------------
    // Heading line parsing
    // ------------------------------------------------------------------

    @Test
    fun `parses heading line with id`() {
        val info = QuarkdownHeadingSyntax.parseHeadingLine("## Section {#sec}")!!
        assertEquals("", info.indent)
        assertEquals("##", info.marker)
        assertEquals(2, info.level)
        assertEquals("Section", info.content)
        assertEquals("sec", info.id)
    }

    @Test
    fun `parses heading line without id`() {
        val info = QuarkdownHeadingSyntax.parseHeadingLine("# Title")!!
        assertEquals(1, info.level)
        assertEquals("Title", info.content)
        assertEquals("", info.id)
    }

    @Test
    fun `parses level six heading`() {
        val info = QuarkdownHeadingSyntax.parseHeadingLine("###### Deep")!!
        assertEquals(6, info.level)
        assertEquals("Deep", info.content)
    }

    @Test
    fun `parses indented heading`() {
        val info = QuarkdownHeadingSyntax.parseHeadingLine("    ### Nested")!!
        assertEquals("    ", info.indent)
        assertEquals(3, info.level)
        assertEquals("Nested", info.content)
    }

    @Test
    fun `strips trailing atx closing markers`() {
        val info = QuarkdownHeadingSyntax.parseHeadingLine("## Title ##")!!
        assertEquals(2, info.level)
        assertEquals("Title", info.content)
        assertEquals("", info.id)
    }

    @Test
    fun `rejects non-heading lines`() {
        assertNull(QuarkdownHeadingSyntax.parseHeadingLine("Not a heading"))
        assertNull(QuarkdownHeadingSyntax.parseHeadingLine("#NoSpace"))
        assertNull(QuarkdownHeadingSyntax.parseHeadingLine("####### seven"))
        assertNull(QuarkdownHeadingSyntax.parseHeadingLine("##"))
        assertNull(QuarkdownHeadingSyntax.parseHeadingLine("plain # text"))
    }

    // ------------------------------------------------------------------
    // Heading line building
    // ------------------------------------------------------------------

    @Test
    fun `builds heading line updating level content and id`() {
        val original = "# Title"
        val built = QuarkdownHeadingSyntax.buildHeadingLine(original, 3, "New Title", "new-id")
        assertEquals("### New Title {#new-id}", built)
    }

    @Test
    fun `builds heading line removing id`() {
        val original = "## Section {#sec}"
        val built = QuarkdownHeadingSyntax.buildHeadingLine(original, 2, "Section", "")
        assertEquals("## Section", built)
    }

    @Test
    fun `builds heading line adding id`() {
        val original = "# Title"
        val built = QuarkdownHeadingSyntax.buildHeadingLine(original, 1, "Title", "main")
        assertEquals("# Title {#main}", built)
    }

    @Test
    fun `builds heading line preserving indent`() {
        val original = "    ## Section"
        val built = QuarkdownHeadingSyntax.buildHeadingLine(original, 4, "Section", "s4")
        assertEquals("    #### Section {#s4}", built)
    }

    @Test
    fun `builds heading line clamps level to range`() {
        val original = "# Title"
        assertEquals("###### Title", QuarkdownHeadingSyntax.buildHeadingLine(original, 99, "Title", ""))
        assertEquals("# Title", QuarkdownHeadingSyntax.buildHeadingLine(original, 0, "Title", ""))
    }

    // ------------------------------------------------------------------
    // Id extraction
    // ------------------------------------------------------------------

    @Test
    fun `extracts id from english content`() {
        assertEquals("hello-world", QuarkdownHeadingSyntax.extractIdFromContent("Hello, World!"))
    }

    @Test
    fun `extracts id preserving unicode letters`() {
        assertEquals("快速提取-示例", QuarkdownHeadingSyntax.extractIdFromContent("快速提取 示例"))
    }

    @Test
    fun `extracts empty id for empty content`() {
        assertTrue(QuarkdownHeadingSyntax.extractIdFromContent("   ").isEmpty())
    }
}

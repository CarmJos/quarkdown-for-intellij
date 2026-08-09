package cc.carm.plugin.intellij.quarkdown.lang.equation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuarkdownEquationSyntaxTest {

    // ------------------------------------------------------------------
    // Inline equation parsing
    // ------------------------------------------------------------------

    @Test
    fun `parses inline line with id`() {
        val info = QuarkdownEquationSyntax.parseInlineEquationLine("$ E = mc^2 $ {#energy}")!!
        assertEquals("", info.indent)
        assertEquals("$", info.open)
        assertEquals(" E = mc^2 ", info.rawContent)
        assertEquals("$", info.close)
        assertEquals("energy", info.id)
    }

    @Test
    fun `parses double dollar inline line without id`() {
        val info = QuarkdownEquationSyntax.parseInlineEquationLine("$$ E = mc^2 $$")!!
        assertEquals("$$", info.open)
        assertEquals("$$", info.close)
        assertEquals("", info.id)
    }

    @Test
    fun `parses indented inline line`() {
        val info = QuarkdownEquationSyntax.parseInlineEquationLine("    $ E = mc^2 $ {#energy}")!!
        assertEquals("    ", info.indent)
        assertEquals("energy", info.id)
    }

    @Test
    fun `rejects non-equation lines`() {
        assertNull(QuarkdownEquationSyntax.parseInlineEquationLine("text \$E=mc^2\$ more"))
        assertNull(QuarkdownEquationSyntax.parseInlineEquationLine("$$$ {#energy}"))
        assertNull(QuarkdownEquationSyntax.parseInlineEquationLine("$ x $$"))
        assertNull(QuarkdownEquationSyntax.parseInlineEquationLine("$ $"))
        assertNull(QuarkdownEquationSyntax.parseInlineEquationLine("E = mc^2"))
    }

    // ------------------------------------------------------------------
    // Fenced equation parsing
    // ------------------------------------------------------------------

    @Test
    fun `parses fenced line with id`() {
        val info = QuarkdownEquationSyntax.parseFenceEquationLine("$$$ {#energy}")!!
        assertEquals("", info.indent)
        assertEquals("$$$", info.fence)
        assertEquals("energy", info.id)
    }

    @Test
    fun `parses indented bare fence`() {
        val info = QuarkdownEquationSyntax.parseFenceEquationLine("    $$$")!!
        assertEquals("    ", info.indent)
        assertEquals("$$$", info.fence)
        assertEquals("", info.id)
    }

    @Test
    fun `rejects short or inline fences`() {
        assertNull(QuarkdownEquationSyntax.parseFenceEquationLine("$$"))
        assertNull(QuarkdownEquationSyntax.parseFenceEquationLine("$ x $"))
        assertNull(QuarkdownEquationSyntax.parseFenceEquationLine("text $$$"))
    }

    // ------------------------------------------------------------------
    // Inline building
    // ------------------------------------------------------------------

    @Test
    fun `builds inline line with new id`() {
        val original = "$ E = mc^2 $ {#energy}"
        assertEquals("$ E = mc^2 $ {#mass}", QuarkdownEquationSyntax.buildInlineLine(original, "mass"))
    }

    @Test
    fun `builds inline line removing id`() {
        val original = "$ E = mc^2 $ {#energy}"
        assertEquals("$ E = mc^2 $", QuarkdownEquationSyntax.buildInlineLine(original, ""))
    }

    @Test
    fun `builds inline line preserving indent and delimiters`() {
        val original = "    $$ E = mc^2 $$"
        assertEquals(
            "    $$ E = mc^2 $$ {#energy}",
            QuarkdownEquationSyntax.buildInlineLine(original, "energy")
        )
    }

    // ------------------------------------------------------------------
    // Fenced building
    // ------------------------------------------------------------------

    @Test
    fun `builds fenced line with new id`() {
        val original = "$$$ {#energy}"
        assertEquals("$$$ {#mass}", QuarkdownEquationSyntax.buildFenceLine(original, "mass"))
    }

    @Test
    fun `builds fenced line removing id`() {
        val original = "$$$ {#energy}"
        assertEquals("$$$", QuarkdownEquationSyntax.buildFenceLine(original, ""))
    }

    @Test
    fun `builds fenced line preserving indent`() {
        val original = "    $$$"
        assertEquals("    $$$ {#energy}", QuarkdownEquationSyntax.buildFenceLine(original, "energy"))
    }

    // ------------------------------------------------------------------
    // Fence open-offset detection
    // ------------------------------------------------------------------

    @Test
    fun `finds opening fences only`() {
        val text = "$$$\nE = mc^2\n$$$\n\n$$$\nE2 = x\n$$$\n"
        val offsets = QuarkdownEquationSyntax.findEquationFenceOpenOffsets(text)
        assertEquals(setOf(0, 18), offsets)
    }

    @Test
    fun `does not treat closing fence as opening`() {
        val text = "$$$\nE = mc^2\n$$$\n"
        val offsets = QuarkdownEquationSyntax.findEquationFenceOpenOffsets(text)
        assertEquals(setOf(0), offsets)
    }

    @Test
    fun `no offsets when no equations`() {
        assertTrue(QuarkdownEquationSyntax.findEquationFenceOpenOffsets("plain text\nE = mc^2\n").isEmpty())
    }
}

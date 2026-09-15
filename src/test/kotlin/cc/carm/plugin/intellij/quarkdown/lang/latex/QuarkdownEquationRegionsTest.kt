package cc.carm.plugin.intellij.quarkdown.lang.latex

import cc.carm.plugin.intellij.quarkdown.lang.latex.QuarkdownEquationRegions.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies [QuarkdownEquationRegions] follows Quarkdown's equation delimiter rules (wiki:
 * *TeX formulae*, and the `ONELINE_MATH` pattern of the reference implementation):
 * `$ ... $` inline / one-line blocks, `$$$` multiline blocks, the blank required right after
 * the opening `$`, and the end-of-text / whitespace / non-word character required right after
 * the closing `$`.
 */
class QuarkdownEquationRegionsTest {

    private fun find(text: String) = QuarkdownEquationRegions.find(text)

    private fun contentOf(text: String, index: Int = 0): String {
        val region = find(text).regions[index]
        return text.substring(region.contentStart, region.contentEnd)
    }

    // ------------------------------------------------------------------
    // Inline equations
    // ------------------------------------------------------------------

    @Test
    fun `detects the documented inline equation`() {
        val text = "Let \$ \\overline v = \\frac {\\Delta x} {\\Delta t} \$ be the **average velocity**."
        val result = find(text)
        assertEquals(1, result.regions.size)
        assertEquals(Kind.INLINE, result.regions[0].kind)
        assertEquals(" \\overline v = \\frac {\\Delta x} {\\Delta t} ", contentOf(text))
    }

    @Test
    fun `detects several inline equations in one paragraph`() {
        val text = "We have \$ a = b \$ and \$ c = d \$ here."
        assertEquals(2, find(text).regions.size)
    }

    // ------------------------------------------------------------------
    // The whitespace rule (no false positives on prices)
    // ------------------------------------------------------------------

    @Test
    fun `dollar amounts are not equations`() {
        val text = "It costs \$5 and \$10 today."
        val result = find(text)
        assertTrue("prices must stay plain text", result.regions.isEmpty())
        assertTrue("prices are not unclosed delimiters", result.unclosedDelimiters.isEmpty())
    }

    @Test
    fun `a lone dollar inside an inline code span is not an equation`() {
        val text = "Wrap the text between two `\$` symbols."
        val result = find(text)
        assertTrue(result.regions.isEmpty())
        assertTrue(result.unclosedDelimiters.isEmpty())
    }

    @Test
    fun `a delimiter without surrounding whitespace is ignored`() {
        val text = "Template \$x\$ and \$y\$ placeholders."
        assertTrue(find(text).regions.isEmpty())
    }

    // ------------------------------------------------------------------
    // Punctuation around the closing delimiter
    // ------------------------------------------------------------------

    @Test
    fun `a closing delimiter followed by a comma still closes the equation`() {
        val text = "For large values of \$ d_k \$, the dot products grow large."
        val result = find(text)
        assertEquals(1, result.regions.size)
        assertEquals(" d_k ", contentOf(text))
    }

    @Test
    fun `a closing delimiter followed by a period or a bracket still closes the equation`() {
        val text = "We use \$ \\mathbf{z} \$. Then \$ \\mathbf{z} = (z_1) \$, again."
        val result = find(text)
        assertEquals(2, result.regions.size)
        assertEquals(" \\mathbf{z} ", contentOf(text, 0))
        assertEquals(" \\mathbf{z} = (z_1) ", contentOf(text, 1))
    }

    @Test
    fun `punctuation after a closing delimiter does not swallow the following content`() {
        val text = "Each head operates on \$ d_k = d_v = \$ .dk dimensions.\n\n\$\$\$ {#eq}\nx = 1\n\$\$\$\n\n# Heading {#id}\n"
        val result = find(text)
        assertEquals(2, result.regions.size)
        assertEquals(" d_k = d_v = ", text.substring(result.regions[0].contentStart, result.regions[0].contentEnd))
        assertTrue("nothing must be left unclosed: ${result.unclosedDelimiters}", result.unclosedDelimiters.isEmpty())
    }

    @Test
    fun `a closing delimiter followed by a word character does not close the equation`() {
        val text = "Let \$ x \$y be given."
        val result = find(text)
        assertTrue("the word right after the dollar keeps it from closing: ${result.regions}", result.regions.isEmpty())
        assertEquals(1, result.unclosedDelimiters.size)
        assertEquals(4, result.unclosedDelimiters[0].first)
    }

    // ------------------------------------------------------------------
    // One-line blocks
    // ------------------------------------------------------------------

    @Test
    fun `detects an isolated one-line block`() {
        val text = "\$ F(u) = \\int f(x) dx \$\n"
        val result = find(text)
        assertEquals(1, result.regions.size)
        assertEquals(Kind.BLOCK, result.regions[0].kind)
        assertEquals(" F(u) = \\int f(x) dx ", contentOf(text))
    }

    @Test
    fun `a block sharing its line with prose is classified as inline`() {
        val text = "Formula: \$ x = 1 \$ ok.\n"
        assertEquals(Kind.INLINE, find(text).regions[0].kind)
    }

    // ------------------------------------------------------------------
    // Multiline blocks
    // ------------------------------------------------------------------

    @Test
    fun `detects a multiline block and keeps its line breaks`() {
        val text = "\$\$\$\nf(x) =\n\\begin{cases}\n0\n\\end{cases}\n\$\$\$\n"
        val result = find(text)
        assertEquals(1, result.regions.size)
        assertEquals(Kind.MULTILINE, result.regions[0].kind)
        assertEquals("f(x) =\n\\begin{cases}\n0\n\\end{cases}\n", contentOf(text))
    }

    @Test
    fun `accepts a multiline block with a cross-reference id`() {
        val text = "\$\$\$ {#fourier}\nx = 1\n\$\$\$\n"
        val result = find(text)
        assertEquals(1, result.regions.size)
        assertEquals("x = 1\n", contentOf(text))
    }

    @Test
    fun `an unclosed multiline fence is reported`() {
        val text = "\$\$\$\nx = 1\n"
        val result = find(text)
        assertTrue(result.regions.isEmpty())
        assertEquals(1, result.unclosedDelimiters.size)
        assertEquals(0, result.unclosedDelimiters[0].first)
    }

    @Test
    fun `an id tag on an inline line is not a fence`() {
        val text = "\$\$\$ {#id}\n"
        assertTrue(find(text).regions.isEmpty())
        assertEquals(1, find(text).unclosedDelimiters.size)
    }

    // ------------------------------------------------------------------
    // Fenced code blocks
    // ------------------------------------------------------------------

    @Test
    fun `equation delimiters inside a code block are ignored`() {
        val text = "```text\n\$ x = 1 \$\n\$\$\$\ny\n\$\$\$\n```\n"
        val result = find(text)
        assertTrue(result.regions.isEmpty())
        assertTrue(result.unclosedDelimiters.isEmpty())
    }

    // ------------------------------------------------------------------
    // Unclosed delimiters
    // ------------------------------------------------------------------

    @Test
    fun `an unclosed inline delimiter is reported`() {
        val text = "Let \$ x be the velocity."
        val result = find(text)
        assertTrue(result.regions.isEmpty())
        assertEquals(1, result.unclosedDelimiters.size)
        assertEquals(4, result.unclosedDelimiters[0].first)
    }

    @Test
    fun `an empty equation is not reported as a region`() {
        val text = "\$  \$\n"
        assertTrue(find(text).regions.isEmpty())
    }

    // ------------------------------------------------------------------
    // Regions are ordered and non-overlapping
    // ------------------------------------------------------------------

    @Test
    fun `regions are returned in document order and do not overlap`() {
        val text = "a \$ x \$ b\n\n\$\$\$\ny\n\$\$\$\n\nc \$ z \$ d\n"
        val regions = find(text).regions
        assertEquals(3, regions.size)
        for (i in 1 until regions.size) {
            assertTrue("regions must not overlap", regions[i].contentStart > regions[i - 1].contentEnd)
        }
        assertEquals(Kind.INLINE, regions[0].kind)
        assertEquals(Kind.MULTILINE, regions[1].kind)
        assertEquals(Kind.INLINE, regions[2].kind)
    }
}

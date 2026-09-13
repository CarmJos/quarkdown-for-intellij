package cc.carm.plugin.intellij.quarkdown.lang.latex

import cc.carm.plugin.intellij.quarkdown.lang.latex.QuarkdownEquationRegions.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies [QuarkdownEquationRegions] follows Quarkdown's documented equation delimiter
 * rules (wiki: *TeX formulae*): `$ ... $` inline / one-line blocks, `$$$` multiline blocks,
 * and the requirement that both `$` delimiters touch whitespace.
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

    // ------------------------------------------------------------------
    // `.math` calls
    // ------------------------------------------------------------------

    @Test
    fun `detects an inline math call`() {
        val text = "Inline: .math {2 + 2} done.\n"
        val regions = find(text).regions
        assertEquals(1, regions.size)
        assertEquals(Kind.MATH_CALL, regions[0].kind)
        assertEquals("2 + 2", contentOf(text))
    }

    @Test
    fun `detects a math call written with the named content argument`() {
        val text = ".math content:{E = mc^2}\n"
        assertEquals("E = mc^2", contentOf(text))
    }

    @Test
    fun `math styling arguments are not part of the content`() {
        val text = ".math {E = mc^2} block:{yes} ref:{einstein} fontsize:{larger}\n"
        val regions = find(text).regions
        assertEquals("only the content argument is TeX", 1, regions.size)
        assertEquals("E = mc^2", contentOf(text))
    }

    @Test
    fun `detects a math block body and stops at the first unindented line`() {
        val text = ".math\n    a^2 + b^2 = c^2\n\nAfter the block.\n"
        val regions = find(text).regions
        assertEquals(1, regions.size)
        assertEquals(Kind.MATH_CALL, regions[0].kind)
        // The body keeps its indentation; leading whitespace is plain TeX text.
        assertEquals("    a^2 + b^2 = c^2", contentOf(text))
    }

    @Test
    fun `a math block body keeps every indented line`() {
        val text = ".math\n    \\begin{cases}\n    0\n    \\end{cases}\n"
        assertEquals("    \\begin{cases}\n    0\n    \\end{cases}", contentOf(text))
    }

    @Test
    fun `math content excludes nested quarkdown calls`() {
        val text = ".math {f(.n) = 1}\n"
        val region = find(text).regions.single()
        val nested = region.excluded.single()
        assertEquals(".n", text.substring(nested.first, nested.last + 1))
        assertEquals("f(.n) = 1", text.substring(region.contentStart, region.contentEnd))
    }

    @Test
    fun `nested call chains are excluded as a whole`() {
        val text = ".math {g(.n::multiply {2})}\n"
        val nested = find(text).regions.single().excluded.single()
        assertEquals(".n::multiply {2}", text.substring(nested.first, nested.last + 1))
    }

    @Test
    fun `segments skip the excluded ranges`() {
        val text = ".math {f(.n) = 1}\n"
        val region = find(text).regions.single()
        assertEquals(
            listOf("f(", ") = 1"),
            region.segments().map { text.substring(it.first, it.last + 1) }
        )
    }

    @Test
    fun `a math call inside a code block is ignored`() {
        val text = "```\n.math {2 + 2}\n```\n"
        assertTrue(find(text).regions.isEmpty())
    }

    @Test
    fun `a dollar sign inside math content does not open an equation`() {
        val text = ".math {a \$ b}\n"
        val regions = find(text).regions
        assertEquals(1, regions.size)
        assertEquals(Kind.MATH_CALL, regions[0].kind)
    }

    // ------------------------------------------------------------------
    // `.texmacro` declarations
    // ------------------------------------------------------------------

    @Test
    fun `detects both the macro name and its body`() {
        val text = ".texmacro {\\gradient} {\\nabla}\n"
        val regions = find(text).regions
        assertEquals(2, regions.size)
        assertTrue(regions.all { it.kind == Kind.TEX_MACRO })
        // The declared name is a control sequence too, so it must be treated as TeX as well.
        assertEquals("\\gradient", text.substring(regions[0].contentStart, regions[0].contentEnd))
        assertEquals("\\nabla", text.substring(regions[1].contentStart, regions[1].contentEnd))
    }

    @Test
    fun `detects a texmacro block body`() {
        val text = ".texmacro {\\sumlim}\n    \\sum_{#1}^{#2}\n"
        val regions = find(text).regions
        assertEquals(2, regions.size)
        assertEquals(
            "    \\sum_{#1}^{#2}",
            text.substring(regions[1].contentStart, regions[1].contentEnd)
        )
    }

    @Test
    fun `detects named texmacro arguments`() {
        val text = ".texmacro name:{\\R} macro:{\\mathbb{R}}\n"
        val regions = find(text).regions
        assertEquals(2, regions.size)
        assertEquals("\\R", text.substring(regions[0].contentStart, regions[0].contentEnd))
        assertEquals("\\mathbb{R}", text.substring(regions[1].contentStart, regions[1].contentEnd))
    }

    // ------------------------------------------------------------------
    // The reported `\begin{aligned}` case
    // ------------------------------------------------------------------

    @Test
    fun `a real math block yields one unsplit command stream`() {
        val text =
            ".math\n    \\begin{aligned}&\\min\\left[\\sum_{t\\in U_\\tau}\\widehat P_tq_t\\right]\\end{aligned}\n"
        val region = find(text).regions.single()
        val content = text.substring(region.contentStart, region.contentEnd)

        // `\begin` must stay a single token: the lexer splitting it into `\b` + `egin` is what
        // made it look mis-highlighted and made the spell checker report "egin".
        val environments = QuarkdownLatexSyntax.tokenize(content)
            .filter { it.kind == QuarkdownLatexSyntax.TokenKind.ENVIRONMENT }
            .map { content.substring(it.start, it.end) }
        assertEquals(listOf("\\begin{aligned}", "\\end{aligned}"), environments)

        assertTrue(
            "a balanced formula must not report problems",
            QuarkdownLatexSyntax.check(content).isEmpty()
        )
    }

    @Test
    fun `latex ranges cover math calls for the spell checker`() {
        val text = "Text.\n.math\n    \\begin{aligned}\n    x\n    \\end{aligned}\n"
        val covered = QuarkdownEquationRegions.latexRanges(text)
            .joinToString("") { text.substring(it.first, it.last + 1) }
        assertTrue(
            "the TeX must be excluded from spell checking, got: '$covered'",
            covered.contains("\\begin{aligned}")
        )
    }

    @Test
    fun `latex ranges leave nested calls and prose out`() {
        val text = ".math {f(.n) = 1}\nProse stays checked.\n"
        val ranges = QuarkdownEquationRegions.latexRanges(text)
        val covered = ranges.joinToString("") { text.substring(it.first, it.last + 1) }
        assertTrue("nested calls must keep their own handling", !covered.contains(".n"))
        assertTrue("prose must stay spell-checked", !covered.contains("Prose"))
    }
}

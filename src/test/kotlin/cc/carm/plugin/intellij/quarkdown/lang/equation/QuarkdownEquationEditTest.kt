package cc.carm.plugin.intellij.quarkdown.lang.equation

import cc.carm.plugin.intellij.quarkdown.lang.equation.QuarkdownEquationEdit.Form
import cc.carm.plugin.intellij.quarkdown.lang.latex.QuarkdownEquationRegions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies [QuarkdownEquationEdit]: it reads an equation occurrence out of the document (content,
 * id, syntax and the span that must be replaced) and renders it back, including conversions
 * between the `$ … $` and `.math` syntaxes.
 */
class QuarkdownEquationEditTest {

    private fun occurrence(text: String, index: Int = 0): QuarkdownEquationEdit.Occurrence? {
        val region = QuarkdownEquationRegions.find(text).regions[index]
        return QuarkdownEquationEdit.occurrence(text, region)
    }

    private fun spanText(text: String, index: Int = 0): String {
        val region = QuarkdownEquationRegions.find(text).regions[index]
        return text.substring(region.spanStart, region.spanEnd)
    }

    // ------------------------------------------------------------------
    // Reading an occurrence
    // ------------------------------------------------------------------

    @Test
    fun `reads an inline equation`() {
        val occurrence = occurrence("Let \$ x = 1 \$ be it.\n")!!
        assertEquals(Form.DOLLAR, occurrence.form)
        assertEquals("x = 1", occurrence.content)
        assertEquals("", occurrence.id)
        assertFalse("an inline equation is not a fence", occurrence.fence)
        assertFalse("an inline equation is not standalone", occurrence.standalone)
    }

    @Test
    fun `reads an inline equation with an id and spans the tag`() {
        val text = "Let \$ x = 1 \$ {#energy} be it.\n"
        val occurrence = occurrence(text)!!
        assertEquals("energy", occurrence.id)
        assertEquals("\$ x = 1 \$ {#energy}", spanText(text))
    }

    @Test
    fun `reads a fenced block with id and indentation`() {
        val text = "Text.\n\n  \$\$\$ {#energy}\n  x = 1\n  \$\$\$\n"
        val occurrence = occurrence(text)!!
        assertEquals(Form.DOLLAR, occurrence.form)
        assertTrue("a fenced block is a fence", occurrence.fence)
        assertEquals("energy", occurrence.id)
        assertEquals("x = 1", occurrence.content)
        assertEquals("  ", occurrence.indent)
        assertTrue(occurrence.standalone)
        // The occurrence starts at the line start, so the indentation is part of the span.
        assertEquals("  \$\$\$ {#energy}\n  x = 1\n  \$\$\$", spanText(text))
    }

    @Test
    fun `reads a math call written with braces`() {
        val text = "Inline: .math {2 + 2} here.\n"
        val occurrence = occurrence(text)!!
        assertEquals(Form.MATH, occurrence.form)
        assertEquals("2 + 2", occurrence.content)
        assertFalse(occurrence.standalone)
        assertEquals(".math {2 + 2}", spanText(text))
    }

    @Test
    fun `reads a math call with ref and a block body`() {
        val text = ".math ref:{energy}\n    x = 1\n    y = 2\n"
        val occurrence = occurrence(text)!!
        assertEquals(Form.MATH, occurrence.form)
        assertEquals("energy", occurrence.id)
        assertEquals("x = 1\ny = 2", occurrence.content)
        assertTrue(occurrence.standalone)
        assertEquals(".math ref:{energy}\n    x = 1\n    y = 2", spanText(text))
    }

    @Test
    fun `a texmacro is not an editable equation`() {
        val text = ".texmacro {\\gradient} {\\nabla}\n"
        assertNull(
            "a macro name is not an equation",
            occurrence(text, index = 0)
        )
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    @Test
    fun `renders an inline equation without an id`() {
        assertEquals(
            "\$ x = 1 \$",
            QuarkdownEquationEdit.render(Form.DOLLAR, "x = 1", "", fence = false, indent = "", standalone = false),
        )
    }

    @Test
    fun `renders an inline equation with an id`() {
        assertEquals(
            "\$ x = 1 \$ {#e}",
            QuarkdownEquationEdit.render(Form.DOLLAR, "x = 1", "e", fence = false, indent = "", standalone = false),
        )
    }

    @Test
    fun `renders a fenced block`() {
        assertEquals(
            "\$\$\$ {#e}\nx = 1\n\$\$\$",
            QuarkdownEquationEdit.render(Form.DOLLAR, "x = 1", "e", fence = true, indent = "", standalone = true),
        )
    }

    @Test
    fun `renders a standalone equation with its indentation`() {
        assertEquals(
            "  \$ x = 1 \$",
            QuarkdownEquationEdit.render(Form.DOLLAR, "x = 1", "", fence = false, indent = "  ", standalone = true),
        )
    }

    @Test
    fun `an inline equation never gains indentation`() {
        assertEquals(
            "\$ x = 1 \$",
            QuarkdownEquationEdit.render(Form.DOLLAR, "x = 1", "", fence = false, indent = "  ", standalone = false),
        )
    }

    @Test
    fun `renders a single-line math call with ref`() {
        assertEquals(
            ".math {x = 1} ref:{e}",
            QuarkdownEquationEdit.render(Form.MATH, "x = 1", "e", fence = false, indent = "", standalone = false),
        )
    }

    @Test
    fun `renders a multi-line math call as an indented body`() {
        assertEquals(
            ".math ref:{e}\n    x = 1\n    \\begin{cases}\n    0\n    \\end{cases}",
            QuarkdownEquationEdit.render(
                Form.MATH,
                "x = 1\n\\begin{cases}\n0\n\\end{cases}",
                "e",
                fence = false,
                indent = "",
                standalone = true,
            ),
        )
    }

    @Test
    fun `renders an empty math call with empty braces`() {
        assertEquals(
            ".math {}",
            QuarkdownEquationEdit.render(Form.MATH, "", "", fence = false, indent = "", standalone = false),
        )
    }

    // ------------------------------------------------------------------
    // Conversion
    // ------------------------------------------------------------------

    @Test
    fun `converting a multi-line content to dollars uses a fence`() {
        val occurrence = occurrence("\$ x = 1 \$ be it.\n")!!
        assertTrue(
            QuarkdownEquationEdit.fenceAfterConversion(occurrence, Form.DOLLAR, "a\nb")
        )
    }

    @Test
    fun `converting a single-line content to dollars keeps the current fence choice`() {
        val inline = occurrence("\$ x = 1 \$ be it.\n")!!
        assertFalse(QuarkdownEquationEdit.fenceAfterConversion(inline, Form.DOLLAR, "a + b"))

        val fenced = occurrence("\$\$\$\nx = 1\n\$\$\$\n")!!
        assertTrue(QuarkdownEquationEdit.fenceAfterConversion(fenced, Form.DOLLAR, "a + b"))
    }

    @Test
    fun `converting to math never uses a fence`() {
        val fenced = occurrence("\$\$\$\nx = 1\n\$\$\$\n")!!
        assertFalse(QuarkdownEquationEdit.fenceAfterConversion(fenced, Form.MATH, "a\nb"))
    }

    // ------------------------------------------------------------------
    // Round trips
    // ------------------------------------------------------------------

    @Test
    fun `an occurrence re-renders to an equivalent equation`() {
        // Re-rendering normalises indentation, so the invariant is that the equation keeps its
        // form, content, id and fence choice rather than being byte-identical.
        val samples = listOf(
            "Let \$ x = 1 \$ {#e} be it.\n",
            ".math {2 + 2}\n",
            ".math ref:{e}\n    a\n    b\n",
        )
        for (text in samples) {
            val region = QuarkdownEquationRegions.find(text).regions.first()
            val first = QuarkdownEquationEdit.occurrence(text, region)!!
            val rendered = QuarkdownEquationEdit.render(
                first.form, first.content, first.id, first.fence, first.indent, first.standalone,
            )
            // Re-read the rendered text: form/content/id must survive the round trip.
            val roundTripped = QuarkdownEquationRegions.find(rendered)
            assertTrue("re-render must still be LaTeX: $rendered", roundTripped.regions.isNotEmpty())
            val second = QuarkdownEquationEdit.occurrence(rendered, roundTripped.regions.first())!!
            assertEquals(first.form, second.form)
            assertEquals(first.content, second.content)
            assertEquals(first.id, second.id)
            assertEquals(first.fence, second.fence)
        }
    }

    @Test
    fun `a fenced block round trip keeps its id and content`() {
        val text = "\$\$\$ {#e}\n  a\n  b\n\$\$\$\n"
        val region = QuarkdownEquationRegions.find(text).regions.first()
        val occurrence = QuarkdownEquationEdit.occurrence(text, region)!!
        assertEquals("e", occurrence.id)
        assertEquals("a\n  b", occurrence.content)
        // This sample's fence is not indented, so there is no indentation to preserve.
        assertEquals("", occurrence.indent)
    }
}

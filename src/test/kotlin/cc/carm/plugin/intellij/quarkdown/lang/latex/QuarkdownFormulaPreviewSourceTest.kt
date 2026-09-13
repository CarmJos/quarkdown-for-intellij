package cc.carm.plugin.intellij.quarkdown.lang.latex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies [QuarkdownFormulaPreviewSource] turns a LaTeX region into a throwaway Quarkdown
 * document that renders the same formula, carrying over the declarations the document needs.
 */
class QuarkdownFormulaPreviewSourceTest {

    private fun build(text: String, index: Int = 0): String {
        val region = QuarkdownEquationRegions.find(text).regions[index]
        return QuarkdownFormulaPreviewSource.build(text, region)
    }

    // ------------------------------------------------------------------
    // Formula emission
    // ------------------------------------------------------------------

    @Test
    fun `an inline equation is emitted with dollar delimiters`() {
        val built = build("Let \$ x = 1 \$ be the answer.\n")
        assertTrue(built.startsWith(".doctype { plain }"))
        assertTrue("expected the equation, got:\n$built", built.endsWith("\$ x = 1 \$"))
    }

    @Test
    fun `a multiline block keeps its fenced form`() {
        val built = build("\$\$\$\na^2 + b^2 = c^2\n\$\$\$\n")
        assertTrue("expected a fenced block, got:\n$built", built.endsWith("\$\$\$\na^2 + b^2 = c^2\n\$\$\$"))
    }

    @Test
    fun `a math call keeps being a math call so nested calls stay evaluable`() {
        val text = ".math {f(.n) = 1}\n"
        val built = build(text)
        // `.math` content is evaluated by Quarkdown, so it must not be turned into `$ … $`.
        assertTrue("expected a .math block, got:\n$built", built.endsWith(".math\n    f(.n) = 1"))
    }

    @Test
    fun `a math block body keeps its line structure`() {
        val text = ".math\n    \\begin{cases}\n    0\n    \\end{cases}\n"
        val built = build(text)
        assertTrue(
            "expected the indented body, got:\n$built",
            built.endsWith(".math\n    \\begin{cases}\n    0\n    \\end{cases}")
        )
    }

    @Test
    fun `a texmacro body is rendered as a fenced block`() {
        val text = ".texmacro {\\gradient} {\\nabla}\n"
        // Region 0 is the macro name, region 1 the body.
        val built = build(text, index = 1)
        assertTrue("expected a fenced block, got:\n$built", built.endsWith("\$\$\$\n\\nabla\n\$\$\$"))
    }

    // ------------------------------------------------------------------
    // Carried-over declarations
    // ------------------------------------------------------------------

    @Test
    fun `texmacro declarations of the document are copied`() {
        val text = ".texmacro {\\gradient} {\\nabla}\n\n\$ \\gradient f \$\n"
        val built = build(text, index = 2)
        assertTrue("the macro must be re-declared, got:\n$built", built.contains(".texmacro {\\gradient} {\\nabla}"))
    }

    @Test
    fun `a macro is declared once even when redefined`() {
        val text = ".texmacro {\\R} {\\mathbb{R}}\n\n.texmacro {\\R} {\\mathbf{R}}\n\n\$ \\R \$\n"
        val built = build(text, index = 4)
        assertEquals("only the first declaration is kept", 1, Regex("\\.texmacro \\{\\\\R\\}").findAll(built).count())
    }

    @Test
    fun `var declarations of the document are copied`() {
        val text = ".var {n} {5}\n\n.math\n    f(.n)\n"
        val built = build(text)
        assertTrue("the variable must be re-declared, got:\n$built", built.contains(".var {n} {5}"))
        assertTrue("the nested reference must be kept", built.contains("f(.n)"))
    }

    @Test
    fun `a document without declarations adds no extra lines`() {
        val built = build("\$ x = 1 \$\n")
        val lines = built.lines()
        assertEquals(".doctype { plain }", lines[0])
        assertEquals("", lines[1])
        assertEquals("\$ x = 1 \$", lines[2])
        assertFalse(built.contains(".texmacro"))
        assertFalse(built.contains(".var"))
    }
}


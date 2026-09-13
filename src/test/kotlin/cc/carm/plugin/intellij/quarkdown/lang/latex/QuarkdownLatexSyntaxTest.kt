package cc.carm.plugin.intellij.quarkdown.lang.latex

import cc.carm.plugin.intellij.quarkdown.lang.latex.QuarkdownLatexSyntax.ProblemKind
import cc.carm.plugin.intellij.quarkdown.lang.latex.QuarkdownLatexSyntax.TokenKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies [QuarkdownLatexSyntax] tokenizes TeX content and reports its structural
 * problems without needing a TeX command database.
 */
class QuarkdownLatexSyntaxTest {

    private fun kinds(text: String) = QuarkdownLatexSyntax.tokenize(text).map { it.kind }
    private fun problems(text: String) = QuarkdownLatexSyntax.check(text)

    // ------------------------------------------------------------------
    // Tokenizer
    // ------------------------------------------------------------------

    @Test
    fun `tokenizes a command with grouped arguments`() {
        assertEquals(
            listOf(
                TokenKind.COMMAND, TokenKind.TEXT, TokenKind.BRACE, TokenKind.TEXT, TokenKind.BRACE,
                TokenKind.TEXT, TokenKind.BRACE, TokenKind.TEXT, TokenKind.BRACE,
            ),
            kinds("\\frac {a} {b}")
        )
    }

    @Test
    fun `tokenizes superscripts, subscripts, numbers and operators`() {
        // Consecutive plain characters (a space and a letter) share a single TEXT token.
        assertEquals(
            listOf(
                TokenKind.TEXT, TokenKind.SUPERSCRIPT, TokenKind.NUMBER,
                TokenKind.TEXT, TokenKind.OPERATOR,
                TokenKind.TEXT, TokenKind.SUBSCRIPT, TokenKind.NUMBER,
            ),
            kinds("x^2 + y_1")
        )
    }

    @Test
    fun `tokenizes an environment as a single token`() {
        val text = "\\begin{cases} a \\end{cases}"
        val tokens = QuarkdownLatexSyntax.tokenize(text)
        assertEquals(TokenKind.ENVIRONMENT, tokens.first().kind)
        assertEquals("\\begin{cases}", text.substring(tokens.first().start, tokens.first().end))
        assertEquals(TokenKind.ENVIRONMENT, tokens.last().kind)
        assertEquals("\\end{cases}", text.substring(tokens.last().start, tokens.last().end))
    }

    @Test
    fun `tokenizes a macro parameter marker`() {
        val tokens = QuarkdownLatexSyntax.tokenize("\\sum_{#1}^{#2}")
        assertTrue(tokens.any { it.kind == TokenKind.PARAMETER })
    }

    @Test
    fun `treats a percent sign as a comment to the end of the line`() {
        val text = "a % not math\nb"
        val comment = QuarkdownLatexSyntax.tokenize(text).single { it.kind == TokenKind.COMMENT }
        assertEquals("% not math", text.substring(comment.start, comment.end))
    }

    @Test
    fun `a control symbol is a single command token`() {
        assertEquals(listOf(TokenKind.COMMAND, TokenKind.TEXT), kinds("\\{ "))
    }

    @Test
    fun `tokenizing covers the whole input contiguously`() {
        val text = "f(x) = \\frac {\\Delta x} {\\Delta t}"
        val tokens = QuarkdownLatexSyntax.tokenize(text)
        var expected = 0
        for (token in tokens) {
            assertEquals("tokens must be contiguous", expected, token.start)
            assertTrue("tokens must not be empty", token.end > token.start)
            expected = token.end
        }
        assertEquals("tokens must cover the whole input", text.length, expected)
    }

    // ------------------------------------------------------------------
    // Structural check — valid input
    // ------------------------------------------------------------------

    @Test
    fun `a well-formed equation has no problems`() {
        assertTrue(
            problems("\\frac {\\Delta x} {\\Delta t} = \\overline {v} % comment").isEmpty()
        )
    }

    @Test
    fun `a balanced environment pair has no problems`() {
        assertTrue(problems("\\begin{cases} 0 & \\text{if } x = 0 \\end{cases}").isEmpty())
    }

    @Test
    fun `nested environments have no problems`() {
        assertTrue(
            problems("\\begin{a} \\begin{b} x \\end{b} \\end{a}").isEmpty()
        )
    }

    // ------------------------------------------------------------------
    // Structural check — braces
    // ------------------------------------------------------------------

    @Test
    fun `reports an unclosed brace at its opening position`() {
        val found = problems("\\frac {a")
        assertEquals(1, found.size)
        assertEquals(ProblemKind.UNCLOSED_BRACE, found[0].kind)
        assertEquals(6, found[0].start)
    }

    @Test
    fun `reports a closing brace without an opening one`() {
        val found = problems("a}")
        assertEquals(1, found.size)
        assertEquals(ProblemKind.UNEXPECTED_BRACE, found[0].kind)
        assertEquals(1, found[0].start)
    }

    @Test
    fun `environment braces are consumed by the environment itself`() {
        // `\begin{cases}` must not leave the brace stack unbalanced.
        assertTrue(problems("\\begin{cases}").none { it.kind == ProblemKind.UNCLOSED_BRACE })
    }

    // ------------------------------------------------------------------
    // Structural check — environments
    // ------------------------------------------------------------------

    @Test
    fun `reports an environment that is never closed`() {
        val found = problems("\\begin{cases} x")
        assertEquals(1, found.size)
        assertEquals(ProblemKind.MISSING_END, found[0].kind)
        assertEquals("cases", found[0].name)
        assertEquals(0, found[0].start)
    }

    @Test
    fun `reports an end without a begin`() {
        val found = problems("x \\end{cases}")
        assertEquals(1, found.size)
        assertEquals(ProblemKind.UNEXPECTED_END, found[0].kind)
        assertEquals("cases", found[0].name)
    }

    @Test
    fun `reports mismatched environment names`() {
        // Both the mismatched `\end` and the environment it leaves open are reported.
        val found = problems("\\begin{a} x \\end{b}")
        assertEquals(
            listOf(ProblemKind.MISSING_END, ProblemKind.UNEXPECTED_END),
            found.map { it.kind }
        )
        assertEquals("a", found[0].name)
        assertEquals("b", found[1].name)
    }

    @Test
    fun `reports an empty environment name`() {
        val found = problems("\\begin{}")
        assertEquals(1, found.size)
        assertEquals(ProblemKind.EMPTY_ENVIRONMENT, found[0].kind)
    }

    // ------------------------------------------------------------------
    // Structural check — commands and parameters
    // ------------------------------------------------------------------

    @Test
    fun `reports a dangling backslash`() {
        val found = problems("x \\")
        assertEquals(1, found.size)
        assertEquals(ProblemKind.DANGLING_COMMAND, found[0].kind)
    }

    @Test
    fun `reports a parameter marker without a number`() {
        val found = problems("#x")
        assertEquals(1, found.size)
        assertEquals(ProblemKind.EMPTY_PARAMETER, found[0].kind)
    }

    @Test
    fun `a numbered parameter is accepted`() {
        assertTrue(problems("#1 + #2").isEmpty())
    }

    @Test
    fun `problems are returned in document order`() {
        val found = problems("{a} } \\begin{x}")
        assertEquals(
            listOf(ProblemKind.UNEXPECTED_BRACE, ProblemKind.MISSING_END),
            found.map { it.kind }
        )
    }
}


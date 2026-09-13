package cc.carm.plugin.intellij.quarkdown.lang.latex

import com.intellij.lexer.Lexer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the lexer behind the equation editor's TeX coloring.
 *
 * It reuses [QuarkdownLatexSyntax], the same tokenizer the annotator uses for equations inside a
 * document, so what is checked here is the *lexer contract* the platform relies on: tokens must
 * cover the text without gaps, offsets must be absolute, and the walk must end cleanly.
 */
class QuarkdownLatexLexerTest {

    private fun tokenize(text: String): List<Pair<String, String>> {
        val lexer: Lexer = QuarkdownLatexLexer()
        lexer.start(text, 0, text.length, 0)
        val result = mutableListOf<Pair<String, String>>()
        while (lexer.tokenType != null) {
            result.add(lexer.tokenType!!.toString() to text.substring(lexer.tokenStart, lexer.tokenEnd))
            lexer.advance()
        }
        return result
    }

    private fun types(text: String): List<String> = tokenize(text).map { it.first }

    @Test
    fun `a command, its braces and its arguments are separate tokens`() {
        assertEquals(
            listOf(
                "QUARKDOWN_LATEX_COMMAND",
                "QUARKDOWN_LATEX_BRACE",
                "QUARKDOWN_LATEX_TEXT",
                "QUARKDOWN_LATEX_BRACE",
            ),
            types("\\frac{a}"),
        )
    }

    @Test
    fun `an environment is a single token`() {
        assertEquals(
            listOf("QUARKDOWN_LATEX_ENVIRONMENT", "QUARKDOWN_LATEX_TEXT"),
            types("\\begin{aligned} x"),
        )
    }

    @Test
    fun `superscripts, numbers and operators get their own tokens`() {
        assertEquals(
            listOf(
                "QUARKDOWN_LATEX_TEXT",
                "QUARKDOWN_LATEX_SUPERSCRIPT",
                "QUARKDOWN_LATEX_NUMBER",
                "QUARKDOWN_LATEX_OPERATOR",
                "QUARKDOWN_LATEX_NUMBER",
            ),
            types("x^2+3"),
        )
    }

    @Test
    fun `a comment runs to the end of its line`() {
        val tokens = tokenize("x % note")
        assertEquals(
            listOf("QUARKDOWN_LATEX_TEXT", "QUARKDOWN_LATEX_COMMENT"),
            tokens.map { it.first },
        )
        assertEquals("% note", tokens[1].second)
    }

    @Test
    fun `the tokens cover the whole text without gaps`() {
        val text = "\\begin{aligned} \\sum_{i=1}^{n} x_i &= 2 \\\\ % why not\n\\end{aligned}"
        val lexer: Lexer = QuarkdownLatexLexer()
        lexer.start(text, 0, text.length, 0)

        var expected = 0
        while (lexer.tokenType != null) {
            assertEquals("a token must start where the previous one ended", expected, lexer.tokenStart)
            assertTrue("a token must not be empty", lexer.tokenEnd > lexer.tokenStart)
            expected = lexer.tokenEnd
            lexer.advance()
        }
        assertEquals("the tokens must reach the end of the text", text.length, expected)
    }

    @Test
    fun `the lexer ends at the buffer end`() {
        val lexer: Lexer = QuarkdownLatexLexer()
        lexer.start("x", 0, 1, 0)
        lexer.advance()

        assertNull("no token type is left at the end", lexer.tokenType)
        assertEquals("the end position is the buffer end", 1, lexer.tokenStart)
        assertEquals("the end position is the buffer end", 1, lexer.tokenEnd)
    }

    @Test
    fun `offsets are absolute when the lexer starts inside the buffer`() {
        val lexer: Lexer = QuarkdownLatexLexer()
        lexer.start("  \\frac", 2, 7, 0)

        assertEquals("QUARKDOWN_LATEX_COMMAND", lexer.tokenType.toString())
        assertEquals("the offset must be absolute, not relative to the start", 2, lexer.tokenStart)
        assertEquals(7, lexer.tokenEnd)
    }

    @Test
    fun `every element type carries the id of the dialog language`() {
        // The platform resolves the highlighter by the language of the element types it is given.
        assertEquals(QuarkdownLatexLanguage.ID, QuarkdownLatexTokenTypes.COMMAND.language.getID())
    }
}

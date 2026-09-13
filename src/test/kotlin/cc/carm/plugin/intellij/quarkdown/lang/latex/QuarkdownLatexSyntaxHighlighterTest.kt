package cc.carm.plugin.intellij.quarkdown.lang.latex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies that the dialog's TeX input is colored with the *same* keys as equations inside a
 * document, which is what keeps a formula looking identical in both places (and keeps its colors
 * customizable in a single place).
 */
class QuarkdownLatexSyntaxHighlighterTest {

    private val highlighter = QuarkdownLatexSyntaxHighlighter()

    @Test
    fun `commands are colored like they are in a document`() {
        val keys = highlighter.getTokenHighlights(QuarkdownLatexTokenTypes.COMMAND)
        assertEquals(1, keys.size)
        assertSame(QuarkdownLatexHighlighting.COMMAND, keys[0])
    }

    @Test
    fun `every token kind except plain text is colored`() {
        for (kind in QuarkdownLatexSyntax.TokenKind.entries) {
            val keys = highlighter.getTokenHighlights(QuarkdownLatexTokenTypes.of(kind))
            if (kind == QuarkdownLatexSyntax.TokenKind.TEXT) {
                assertEquals("plain text must keep the editor's own color", 0, keys.size)
            } else {
                assertEquals("$kind must be colored", 1, keys.size)
                assertSame(QuarkdownLatexHighlighting.keyFor(kind), keys[0])
            }
        }
    }

    @Test
    fun `the highlighting lexer is the shared TeX tokenizer`() {
        val lexer = highlighter.highlightingLexer
        lexer.start("\\alpha", 0, 6, 0)
        assertTrue("the lexer must come from the shared tokenizer", lexer.tokenType != null)
    }
}

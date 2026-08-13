package cc.carm.plugin.intellij.quarkdown.lang

import cc.carm.plugin.intellij.quarkdown.lang.lexer.QuarkdownTokenTypes
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [QuarkdownBraceMatcher] — pure configuration checks.
 */
class QuarkdownBraceMatcherTest {

    private val matcher = QuarkdownBraceMatcher()

    @Test
    fun `pairs cover braces brackets and parens`() {
        val left = matcher.pairs.map { it.leftBraceType }
        assertTrue(left.contains(QuarkdownTokenTypes.FUNCTION_BRACE_OPEN))
        assertTrue(left.contains(QuarkdownTokenTypes.BRACE_OPEN))
        assertTrue(left.contains(QuarkdownTokenTypes.BRACKET_OPEN))
        assertTrue(left.contains(QuarkdownTokenTypes.PAREN_OPEN))
    }

    @Test
    fun `all pairs are structural`() {
        assertTrue(matcher.pairs.all { it.isStructural })
    }

    @Test
    fun `closing braces are allowed before any token`() {
        assertTrue(matcher.isPairedBracesAllowedBeforeType(QuarkdownTokenTypes.BRACE_CLOSE, QuarkdownTokenTypes.TEXT))
    }
}

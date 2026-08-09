package cc.carm.plugin.intellij.quarkdown.lang

import cc.carm.plugin.intellij.quarkdown.lang.lexer.QuarkdownTokenTypes.BRACE_CLOSE
import cc.carm.plugin.intellij.quarkdown.lang.lexer.QuarkdownTokenTypes.BRACE_OPEN
import cc.carm.plugin.intellij.quarkdown.lang.lexer.QuarkdownTokenTypes.BRACKET_CLOSE
import cc.carm.plugin.intellij.quarkdown.lang.lexer.QuarkdownTokenTypes.BRACKET_OPEN
import cc.carm.plugin.intellij.quarkdown.lang.lexer.QuarkdownTokenTypes.FUNCTION_BRACE_CLOSE
import cc.carm.plugin.intellij.quarkdown.lang.lexer.QuarkdownTokenTypes.FUNCTION_BRACE_OPEN
import cc.carm.plugin.intellij.quarkdown.lang.lexer.QuarkdownTokenTypes.PAREN_CLOSE
import cc.carm.plugin.intellij.quarkdown.lang.lexer.QuarkdownTokenTypes.PAREN_OPEN
import com.intellij.lang.BracePair
import com.intellij.lang.PairedBraceMatcher
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType

/**
 * Brace matcher for Quarkdown language.
 *
 * Handles auto-closing, brace matching highlighting, and brace navigation
 * (Ctrl+[, Ctrl+]) for:
 * - `{ }` — function call parameter blocks (both `FUNCTION_BRACE_*` and plain `BRACE_*`)
 * - `[ ]` — link/image references and table cells
 * - `( )` — link/image URLs
 */
class QuarkdownBraceMatcher : PairedBraceMatcher {

    override fun getPairs(): Array<BracePair> = arrayOf(
        BracePair(FUNCTION_BRACE_OPEN, FUNCTION_BRACE_CLOSE, true),
        BracePair(BRACE_OPEN, BRACE_CLOSE, true),
        BracePair(BRACKET_OPEN, BRACKET_CLOSE, true),
        BracePair(PAREN_OPEN, PAREN_CLOSE, true),
    )

    /**
     * Allow closing braces before most token types in Quarkdown.
     */
    override fun isPairedBracesAllowedBeforeType(
        lispBlockCommentType: IElementType,
        tokenType: IElementType?
    ): Boolean = true

    /**
     * Return the opening brace offset directly as the code construct start.
     */
    override fun getCodeConstructStart(file: PsiFile, openingBraceOffset: Int): Int = openingBraceOffset
}

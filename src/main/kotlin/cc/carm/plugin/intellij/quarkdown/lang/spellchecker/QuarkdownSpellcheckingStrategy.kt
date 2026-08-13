package cc.carm.plugin.intellij.quarkdown.lang.spellchecker

import cc.carm.plugin.intellij.quarkdown.lang.lexer.QuarkdownTokenTypes
import com.intellij.psi.PsiElement
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy
import com.intellij.spellchecker.tokenizer.Tokenizer
import com.intellij.psi.tree.IElementType

/**
 * Spell-checking strategy for Quarkdown (.qd) documents.
 *
 * Only plain-text tokens (`TEXT`, `HEADING_CONTENT`) are checked. Everything else —
 * function names, parameter lists, code blocks, link/image URLs, ID tags, table
 * syntax and markers — is returned as-is so the spell checker never flags
 * Quarkdown-specific syntax or paths.
 */
class QuarkdownSpellcheckingStrategy : SpellcheckingStrategy() {

    override fun getTokenizer(element: PsiElement): Tokenizer<*> {
        val tokenType = element.node?.elementType ?: return EMPTY_TOKENIZER
        return if (tokenType in SPELLCHECKED_TOKENS) TEXT_TOKENIZER else EMPTY_TOKENIZER
    }

    companion object {
        private val SPELLCHECKED_TOKENS: Set<IElementType> = setOf(
            QuarkdownTokenTypes.TEXT,
            QuarkdownTokenTypes.HEADING_CONTENT,
        )
    }
}

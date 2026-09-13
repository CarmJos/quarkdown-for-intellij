package cc.carm.plugin.intellij.quarkdown.lang.spellchecker

import cc.carm.plugin.intellij.quarkdown.lang.latex.QuarkdownEquationRegions
import cc.carm.plugin.intellij.quarkdown.lang.lexer.QuarkdownTokenTypes
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy
import com.intellij.spellchecker.tokenizer.Tokenizer

/**
 * Spell-checking strategy for Quarkdown (.qd) documents.
 *
 * Only plain-text tokens (`TEXT`, `HEADING_CONTENT`) inside prose are checked. Everything
 * else — function names, parameter lists, code blocks, link/image URLs, ID tags, table
 * syntax and markers — is returned as-is so the spell checker never flags Quarkdown-specific
 * syntax or paths.
 *
 * TeX/LaTeX content (equations, `.math` and `.texmacro`) is skipped as well. That is not just
 * cosmetic: the Quarkdown lexer deliberately splits a Markdown escape such as `\b` into its
 * own token, so a control sequence like `\begin` arrives here as `\b` + `egin` — the latter
 * being checked as a word would report "egin" as a misspelling on every equation.
 */
class QuarkdownSpellcheckingStrategy : SpellcheckingStrategy() {

    override fun getTokenizer(element: PsiElement): Tokenizer<*> {
        val tokenType = element.node?.elementType ?: return EMPTY_TOKENIZER
        if (tokenType !in SPELLCHECKED_TOKENS) return EMPTY_TOKENIZER
        if (isInsideLatex(element)) return EMPTY_TOKENIZER
        return TEXT_TOKENIZER
    }

    /** True when [element] overlaps any TeX/LaTeX region of its file. */
    private fun isInsideLatex(element: PsiElement): Boolean {
        val file = element.containingFile ?: return false
        val range = element.textRange
        return LatexRangesCache.rangesOf(file)
            .any { it.first < range.endOffset && it.last >= range.startOffset }
    }

    companion object {
        private val SPELLCHECKED_TOKENS: Set<IElementType> = setOf(
            QuarkdownTokenTypes.TEXT,
            QuarkdownTokenTypes.HEADING_CONTENT,
        )
    }
}

/**
 * Per-file cache of the TeX/LaTeX ranges, keyed by the document's modification stamp.
 *
 * `getTokenizer` is called once per plain-text token, so recomputing the ranges — a full
 * document scan — on every call would make spell checking quadratic in the file size. The
 * stamp makes the cache self-invalidating: any edit produces a new stamp and the ranges are
 * recomputed on the next pass.
 */
private object LatexRangesCache {

    private val KEY = Key.create<Pair<Long, List<IntRange>>>("quarkdown.latex.spellchecking.ranges")

    fun rangesOf(file: PsiFile): List<IntRange> {
        // Non-physical files (e.g. in tests or light virtual files) may have no document;
        // they are rare, so they are simply recomputed each time.
        val document = PsiDocumentManager.getInstance(file.project).getDocument(file)
            ?: return QuarkdownEquationRegions.latexRanges(file.text)

        val stamp = document.modificationStamp
        file.getUserData(KEY)?.let { (cachedStamp, ranges) ->
            if (cachedStamp == stamp) return ranges
        }

        val ranges = QuarkdownEquationRegions.latexRanges(file.text)
        file.putUserData(KEY, stamp to ranges)
        return ranges
    }
}

package cc.carm.plugin.intellij.quarkdown.lang.annotator

import cc.carm.plugin.intellij.quarkdown.QuarkdownBundle
import cc.carm.plugin.intellij.quarkdown.lang.latex.QuarkdownEquationRegions
import cc.carm.plugin.intellij.quarkdown.lang.latex.QuarkdownLatexHighlighting
import cc.carm.plugin.intellij.quarkdown.lang.latex.QuarkdownLatexSyntax
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

/**
 * Highlights and checks the TeX/LaTeX content of Quarkdown: `$ ... $`, `$$ ... $$` and
 * `$$$ … $$$` equations, the expression of `.math` calls, and the TeX of `.texmacro`.
 *
 * Two things happen per region:
 *
 *  - **Highlighting** — the content is tokenized by [QuarkdownLatexSyntax] and every token
 *    gets its color from [QuarkdownLatexHighlighting] (commands, environments, braces,
 *    `^`/`_`, `#1` parameters, numbers, operators, `%` comments).
 *  - **Syntax checking** — [QuarkdownLatexSyntax.check] reports structural mistakes:
 *    unbalanced braces, `\begin` / `\end` that do not pair up, empty environment names and
 *    dangling commands. An equation whose delimiter is never closed is reported too.
 *
 * The regions come from [QuarkdownEquationRegions]. They are also excluded from spell
 * checking (see `QuarkdownSpellcheckingStrategy`) — that is what used to flag `egin` in
 * `\begin`, because the Quarkdown lexer splits that command into an `ESCAPE` token plus
 * plain text.
 *
 * Only *structural* problems are reported. Command names are never validated, because TeX
 * ships thousands of primitives, KaTeX implements a large subset, and a document can define
 * its own commands with `.texmacro` — an "unknown command" check would be mostly noise.
 *
 * Colors are applied through the annotator rather than the Quarkdown lexer: equation content
 * is a plain text leaf to the lexer, and embedding a TeX lexer into it would couple two
 * unrelated grammars. The trade-off is that the attributes are recomputed per annotation
 * pass, so the pass bails out cheaply when the file cannot contain any TeX.
 */
class QuarkdownLatexAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element !is PsiFile) return
        val text = element.text
        if (text.isEmpty()) return
        // Cheap pre-check: `.math` / `.texmacro` content is not introduced by a `$`.
        if (!text.contains('$') && !text.contains(MATH_CALL) && !text.contains(TEX_MACRO)) return

        val result = QuarkdownEquationRegions.find(text)
        for (region in result.regions) {
            annotateRegion(text, region, holder)
        }
        for (range in result.unclosedDelimiters) {
            holder.newAnnotation(
                HighlightSeverity.WARNING,
                QuarkdownBundle.message("quarkdown.latex.problem.unclosed.delimiter")
            )
                .range(TextRange(range.first, range.last + 1))
                .create()
        }
    }

    /** Highlights one equation's tokens and reports its structural problems. */
    private fun annotateRegion(
        text: CharSequence,
        region: QuarkdownEquationRegions.Region,
        holder: AnnotationHolder,
    ) {
        val content = maskedContent(text, region)

        for (token in QuarkdownLatexSyntax.tokenize(content)) {
            // Plain text keeps the default Quarkdown color: skipping it avoids an
            // annotation per word while still coloring everything that carries meaning.
            if (token.kind == QuarkdownLatexSyntax.TokenKind.TEXT) continue
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(region.rangeOf(token.start, token.end))
                .textAttributes(QuarkdownLatexHighlighting.keyFor(token.kind))
                .create()
        }

        for (problem in QuarkdownLatexSyntax.check(content)) {
            holder.newAnnotation(HighlightSeverity.ERROR, messageFor(problem))
                .range(region.rangeOf(problem.start, problem.end))
                .create()
        }
    }

    /**
     * The region's content with its excluded ranges blanked out.
     *
     * `.math` content is evaluated as Quarkdown, so nested function calls (e.g.
     * `f(.n) = .n::multiply {2}`) are not TeX and must not be re-colored or re-checked. They
     * are replaced by spaces rather than cut out: that keeps every offset aligned with the
     * document *and* keeps the brace balance of the surrounding TeX intact, so the checker
     * cannot report bogus "unclosed brace" errors around them.
     */
    private fun maskedContent(text: CharSequence, region: QuarkdownEquationRegions.Region): String {
        val content = StringBuilder(text.substring(region.contentStart, region.contentEnd))
        for (range in region.excluded) {
            for (offset in range.first..range.last) {
                val index = offset - region.contentStart
                if (index in content.indices) content.setCharAt(index, ' ')
            }
        }
        return content.toString()
    }

    /** Translates a problem into the localized message shown in the editor. */
    private fun messageFor(problem: QuarkdownLatexSyntax.Problem): String =
        when (problem.kind) {
            QuarkdownLatexSyntax.ProblemKind.UNCLOSED_BRACE ->
                QuarkdownBundle.message("quarkdown.latex.problem.unclosed.brace")

            QuarkdownLatexSyntax.ProblemKind.UNEXPECTED_BRACE ->
                QuarkdownBundle.message("quarkdown.latex.problem.unexpected.brace")

            QuarkdownLatexSyntax.ProblemKind.MISSING_END ->
                QuarkdownBundle.message("quarkdown.latex.problem.missing.end", problem.name)

            QuarkdownLatexSyntax.ProblemKind.UNEXPECTED_END ->
                QuarkdownBundle.message("quarkdown.latex.problem.unexpected.end", problem.name)

            QuarkdownLatexSyntax.ProblemKind.EMPTY_ENVIRONMENT ->
                QuarkdownBundle.message("quarkdown.latex.problem.empty.environment")

            QuarkdownLatexSyntax.ProblemKind.DANGLING_COMMAND ->
                QuarkdownBundle.message("quarkdown.latex.problem.dangling.command")

            QuarkdownLatexSyntax.ProblemKind.EMPTY_PARAMETER ->
                QuarkdownBundle.message("quarkdown.latex.problem.empty.parameter")
        }

    /** Absolute file range of the `[start, end)` offsets inside an equation's content. */
    private fun QuarkdownEquationRegions.Region.rangeOf(start: Int, end: Int): TextRange =
        TextRange(contentStart + start, contentStart + end)

    private companion object {
        /** Call names whose content is TeX, used for the cheap "nothing to do" pre-check. */
        const val MATH_CALL = ".math"
        const val TEX_MACRO = ".texmacro"
    }
}

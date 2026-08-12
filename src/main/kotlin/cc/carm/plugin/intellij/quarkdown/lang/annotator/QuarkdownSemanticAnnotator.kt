package cc.carm.plugin.intellij.quarkdown.lang.annotator

import cc.carm.plugin.intellij.quarkdown.lang.function.FunctionRegistry
import cc.carm.plugin.intellij.quarkdown.lang.function.QuarkdownCallParser
import cc.carm.plugin.intellij.quarkdown.lang.lsp.QuarkdownLspSupport
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

/**
 * Semantic-level syntax highlighting for Quarkdown documents, layered on top of the
 * lexer-level [cc.carm.plugin.intellij.quarkdown.lang.highlighter.QuarkdownSyntaxHighlighter].
 *
 * The lexer can only see tokens; it cannot know whether `.foo` is a stdlib function, a
 * declared variable, or an error. This annotator resolves each function call against the
 * stdlib metadata (see [FunctionRegistry]) and applies the semantic attributes computed
 * by [QuarkdownSemanticRanges]:
 *
 *  - known stdlib functions → `SEMANTIC_KNOWN_FUNCTION`
 *  - `.name` references resolving to a declared `.var` → `SEMANTIC_VARIABLE_REF`
 *  - argument values that are valid enum members → `SEMANTIC_VALID_ENUM`
 *  - named-argument parameter names → `SEMANTIC_PARAMETER`
 *
 * Unknown functions / invalid values are left to [QuarkdownAnnotator], which reports them
 * as errors. Both annotators are registered for the Quarkdown language in `plugin.xml`.
 *
 * When the official Quarkdown Language Server is running
 * ([QuarkdownLspSupport.isServerRunning]), the semantic tokens supplied by LSP replace
 * this annotator.
 */
class QuarkdownSemanticAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element !is PsiFile) return
        if (QuarkdownLspSupport.isServerRunning(element.project)) return

        val text = element.text
        val functions = FunctionRegistry.getInstance(element.project).getFunctions()
        val variables = QuarkdownCallParser.findVarDeclarations(text)

        for (highlight in QuarkdownSemanticRanges.compute(text, functions, variables)) {
            if (highlight.end > text.length) continue
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(TextRange(highlight.start, highlight.end))
                .textAttributes(highlight.key)
                .create()
        }
    }
}

package cc.carm.plugin.intellij.quarkdown.lang.annotator

import cc.carm.plugin.intellij.quarkdown.QuarkdownBundle
import cc.carm.plugin.intellij.quarkdown.lang.function.FunctionRegistry
import cc.carm.plugin.intellij.quarkdown.lang.function.QuarkdownCallParser
import cc.carm.plugin.intellij.quarkdown.lang.function.QuarkdownCallValidator
import cc.carm.plugin.intellij.quarkdown.lang.lsp.QuarkdownLspSupport
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

/**
 * Validates Quarkdown function calls against the standard library metadata, using the
 * real Quarkdown grammar (see [QuarkdownCallParser]):
 *
 *   .doctype {paged}                      positional argument
 *   .pagemargin position:{bottomcenter}   named argument
 *   .multiply {6} by:{3}                  mixed positional + named
 *
 * Reported problems (matching the Quarkdown compiler):
 *  - unknown function / parameter names
 *  - invalid enum values (`bottomcenter` is valid, `bottom_center` is not)
 *  - positional argument following a named one
 *  - missing required arguments
 *
 * When the official Quarkdown Language Server is running ([QuarkdownLspSupport.isServerRunning]),
 * this annotator defers to the LSP diagnostics to avoid duplicated problem markers.
 */
class QuarkdownAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element !is PsiFile) return
        if (QuarkdownLspSupport.isServerRunning(element.project)) return

        val text = element.text
        val registry = FunctionRegistry.getInstance(element.project)
        val functions = registry.getFunctions()
        if (functions.isEmpty()) return

        // `.var {name} {value}` declarations: `.name` afterwards is a variable reference.
        val variables = QuarkdownCallParser.findVarDeclarations(text).keys

        for (dotStart in QuarkdownCallParser.findAllCallStarts(text)) {
            val call = QuarkdownCallParser.parseCall(text, dotStart) ?: continue
            for (issue in QuarkdownCallValidator.validate(call, functions, variables)) {
                if (issue.end > text.length) continue
                val severity = when (issue.severity) {
                    QuarkdownCallValidator.Severity.ERROR -> HighlightSeverity.ERROR
                    QuarkdownCallValidator.Severity.WARNING -> HighlightSeverity.WARNING
                }
                val message = issue.messageKey?.let {
                    QuarkdownBundle.message(it, *issue.messageArgs.toTypedArray())
                } ?: issue.message
                holder.newAnnotation(severity, message)
                    .range(TextRange(issue.start, issue.end))
                    .create()
            }
        }
    }
}

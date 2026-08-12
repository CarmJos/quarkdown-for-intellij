package cc.carm.plugin.intellij.quarkdown.lang.lsp

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.Alarm
import org.eclipse.lsp4j.CompletionItem
import com.intellij.platform.lsp.api.customization.LspCompletionSupport

/**
 * Customizes the official LSP completion integration for Quarkdown.
 *
 * After a function completion item is inserted, the parameter-info popup (Ctrl+P) is
 * automatically triggered so the user can fill in the arguments.
 *
 * Two kinds of completions are relevant here:
 *  - **function names** (e.g. `pageformat`), whose insert text is just `name ` — the
 *    caret lands in the pending-argument position, where the LSP offers the parameter
 *    names and [QuarkdownParameterNameCompletionContributor] stays quiet;
 *  - **parameter-name snippets** (e.g. `side:{${1:left|right}}`), whose LSP snippet
 *    places the caret inside the argument's value braces.
 *
 * The underlying insertion (snippet/template handling) is performed by the platform's
 * LSP insert handler; this class only wraps the lookup element to schedule the
 * completion + parameter-info popup once the insertion has settled.
 */
class QuarkdownLspCompletionSupport : LspCompletionSupport() {

    override fun createLookupElement(
        parameters: CompletionParameters,
        item: CompletionItem
    ): LookupElement {
        val base = super.createLookupElement(parameters, item) ?: return LookupElementBuilder.create(item.label ?: "")
        val builder = base as? LookupElementBuilder ?: return base
        val insertText = item.insertText
        // Plain value completions have a null insert text (the platform inserts the
        // label); triggering the popup after those would only re-show the same values.
        val takesArguments = insertText != null
        // For function-name completions (no braces in the insert text) the signature
        // should be prefetched so the parameter-name completion has data to offer.
        val functionName = if (takesArguments && insertText.contains('{') != true) {
            item.label?.trim()
        } else {
            null
        }
        // Wrap it so the parameter-info popup is triggered after insertion.
        return builder.withInsertHandler { context, _ ->
            if (takesArguments) {
                triggerParameterInfo(context.editor, context.project, functionName)
            }
        } ?: builder
    }

    private fun triggerParameterInfo(
        editor: com.intellij.openapi.editor.Editor,
        project: com.intellij.openapi.project.Project,
        functionName: String?
    ) {
        // The LSP snippet handler converts the insert text to a *template* whose first
        // tab-stop places the caret inside the argument braces (when the snippet has
        // any). Firing immediately (or on the next EDT tick) runs before the caret has
        // settled, so use a short delayed request.
        val disposable = Disposer.newDisposable("QuarkdownCompletionParameterInfo")
        val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, disposable)
        alarm.addRequest({
            if (project.isDisposed || editor.isDisposed) {
                Disposer.dispose(disposable)
                return@addRequest
            }
            val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
            // Best-effort: warm the signature cache for the completed function so the
            // parameter-name contributor (which reads the cache synchronously) has data
            // for this or the next completion pass. The popup itself never depends on it:
            // the LSP provides the parameter names/values at the caret position.
            if (functionName != null && file != null) {
                val cache = QuarkdownLspFunctionSignatureCache.getInstance(project)
                if (cache.getSignature(functionName) == null) {
                    cache.requestSignature(functionName, file) { /* warm only */ }
                }
            }
            val controller = AutoPopupController.getInstance(project)
            // Show the completion popup (remaining parameter names / the current
            // parameter's allowed values) and the parameter-info popup with the
            // function signature.
            controller.scheduleAutoPopup(editor)
            val element = file?.findElementAt(editor.caretModel.offset) ?: file
            if (element != null) {
                controller.autoPopupParameterInfo(editor, element)
            }
            Disposer.dispose(disposable)
        }, PARAMETER_INFO_DELAY_MS)
    }

    companion object {
        /** Delay (ms) to let the LSP snippet/template place the caret before showing parameter info. */
        private const val PARAMETER_INFO_DELAY_MS = 200L
    }
}

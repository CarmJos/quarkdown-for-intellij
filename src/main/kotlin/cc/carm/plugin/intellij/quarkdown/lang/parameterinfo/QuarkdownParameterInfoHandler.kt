package cc.carm.plugin.intellij.quarkdown.lang.parameterinfo

import cc.carm.plugin.intellij.quarkdown.QuarkdownFileType
import cc.carm.plugin.intellij.quarkdown.lang.function.QuarkdownCallParser
import cc.carm.plugin.intellij.quarkdown.lang.lsp.QuarkdownFunctionSignature
import cc.carm.plugin.intellij.quarkdown.lang.lsp.QuarkdownLspFunctionSignatureCache
import com.intellij.codeInsight.AutoPopupController
import com.intellij.lang.parameterInfo.CreateParameterInfoContext
import com.intellij.lang.parameterInfo.ParameterInfoHandler
import com.intellij.lang.parameterInfo.ParameterInfoUIContext
import com.intellij.lang.parameterInfo.UpdateParameterInfoContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

/**
 * Ctrl+P parameter-info for Quarkdown function calls.
 *
 * When the caret is inside a function call — `.multiply {6} by:{3}` — the popup shows
 * the function signature (from the official `quarkdown language-server` via
 * [QuarkdownLspFunctionSignatureCache]) and highlights the parameter the caret is
 * currently filling.
 *
 * Positional arguments map to parameters by their running index among the user-facing
 * parameters; named arguments (`name:{...}`) resolve by name. Chained calls (`::b`)
 * reserve slot 0 for the chained value.
 */
class QuarkdownParameterInfoHandler :
    ParameterInfoHandler<PsiElement, QuarkdownFunctionSignature> {

    override fun findElementForParameterInfo(context: CreateParameterInfoContext): PsiElement? {
        val file = context.file
        if (file.fileType != QuarkdownFileType.INSTANCE) return null
        val call = callAt(file, context.offset) ?: return null
        return file.findElementAt(call.start) ?: file
    }

    override fun showParameterInfo(element: PsiElement, context: CreateParameterInfoContext) {
        val call = callAt(context.file, context.offset) ?: return
        val cache = QuarkdownLspFunctionSignatureCache.getInstance(context.project)
        val cached = cache.getSignature(call.name)
        if (cached != null) {
            context.itemsToShow = arrayOf(cached)
            context.showHint(element, call.nameEnd, this)
            return
        }
        // Signature not cached yet (e.g. user typed quickly and pressed Ctrl+P before the
        // inlay pass fetched it). Fetch on-demand, then re-trigger the popup so it appears
        // with the freshly cached signature.
        cache.requestSignature(call.name, context.file) { _ ->
            val editor = context.editor
            if (context.project.isDisposed || editor == null || editor.isDisposed) return@requestSignature
            ApplicationManager.getApplication().invokeLater {
                if (editor.isDisposed) return@invokeLater
                AutoPopupController.getInstance(context.project).autoPopupParameterInfo(editor, element)
            }
        }
    }

    override fun findElementForUpdatingParameterInfo(context: UpdateParameterInfoContext): PsiElement? {
        val file = context.file
        if (file.fileType != QuarkdownFileType.INSTANCE) return null
        val call = callAt(file, context.offset) ?: return null
        return file.findElementAt(call.start) ?: file
    }

    override fun updateParameterInfo(owner: PsiElement, context: UpdateParameterInfoContext) {
        val call = callAt(context.file, context.offset) ?: return
        val signature = QuarkdownLspFunctionSignatureCache.getInstance(context.project).getSignature(call.name) ?: return
        context.setCurrentParameter(parameterIndexAtCaret(call, signature, context.offset))
    }

    override fun updateUI(signature: QuarkdownFunctionSignature, context: ParameterInfoUIContext) {
        // Render the signature with the current parameter highlighted.
        val current = context.currentParameterIndex
        val text = normalizeSignature(signature)
        val (start, end) = if (current in 0 until signature.parameterNames.size) {
            parameterRangeInText(text, signature.parameterNames[current]) ?: (-1 to -1)
        } else {
            -1 to -1
        }
        val highlighted = current in 0 until signature.parameterNames.size
        context.setupUIComponentPresentation(
            text,
            start,
            end,
            highlighted,
            false,
            false,
            context.defaultParameterColor
        )
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Finds the function call containing [offset], if the caret is within its argument region. */
    internal fun callAt(file: PsiFile, offset: Int): QuarkdownCallParser.Call? {
        if (file.fileType != QuarkdownFileType.INSTANCE) return null
        val text = file.text
        val start = QuarkdownCallParser.findCallStart(text, offset)
        if (start < 0) return null
        val call = QuarkdownCallParser.parseCall(text, start) ?: return null
        // The caret is "in the call" when it is at/after the name and within the argument
        // region — or in the whitespace that immediately follows it, i.e. the position
        // where the next argument would be typed. This covers a freshly completed
        // function name (`.pageformat `) where no argument exists yet.
        if (offset < call.nameEnd) return null
        val regionEnd = call.args.lastOrNull()?.braceEnd ?: call.nameEnd
        val pendingEnd = trailingWhitespaceEnd(text, regionEnd)
        return call.takeIf { offset <= pendingEnd }
    }

    /** End of the run of spaces/tabs starting at [from] (used for the pending-argument zone). */
    private fun trailingWhitespaceEnd(text: String, from: Int): Int {
        var i = from
        while (i < text.length && (text[i] == ' ' || text[i] == '\t')) i++
        return i
    }

    /**
     * Computes the index of the parameter the caret is filling.
     *
     * Positional arguments advance a running index (starting at 0, or 1 for chained
     * calls whose chained value occupies slot 0). Named arguments resolve by name.
     */
    internal fun parameterIndexAtCaret(
        call: QuarkdownCallParser.Call,
        signature: QuarkdownFunctionSignature,
        caret: Int
    ): Int {
        val paramNames = signature.parameterNames

        // If the caret is inside a specific argument's braces, use that argument.
        for (arg in call.args) {
            if (caret in arg.braceStart..arg.braceEnd) {
                if (arg.isNamed) {
                    val idx = paramNames.indexOf(arg.paramName)
                    return if (idx >= 0) idx else paramNames.size
                }
                // Positional: its index among positional args (chained offset included).
                var positional = if (call.isChained) 1 else 0
                for (a in call.args) {
                    if (a === arg) return positional.coerceIn(0, paramNames.size)
                    if (!a.isNamed) positional++
                }
            }
        }

        // Not inside a specific arg: highlight the next parameter to be filled.
        var positional = if (call.isChained) 1 else 0
        var maxNamed = -1
        for (arg in call.args) {
            if (caret < arg.braceStart) {
                // Caret before this argument → the parameter this argument would fill.
                return if (arg.isNamed) {
                    paramNames.indexOf(arg.paramName).let { if (it >= 0) it else positional }
                } else {
                    positional.coerceIn(0, paramNames.size)
                }
            }
            // This argument is fully before the caret → consume it.
            if (arg.isNamed) {
                val idx = paramNames.indexOf(arg.paramName)
                if (idx > maxNamed) maxNamed = idx
            } else {
                positional++
            }
        }
        // Caret after all arguments → the parameter after the last consumed one.
        return maxOf(positional, maxNamed + 1).coerceIn(0, paramNames.size)
    }

    /** Collapses `\` line-continuations and excess whitespace for a single-line display. */
    private fun normalizeSignature(signature: QuarkdownFunctionSignature): String =
        signature.signatureText
            .replace("\\\r\n", " ")
            .replace("\\\n", " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    /** Finds the [start, end) of the `name:{...}` segment for [paramName] in [text]. */
    private fun parameterRangeInText(text: String, paramName: String): Pair<Int, Int>? {
        val regex = Regex("""\b${Regex.escape(paramName)}\s*:\s*\{""")
        val m = regex.find(text) ?: return null
        // Highlight the parameter name up to (and including) the colon.
        val colon = text.indexOf(':', m.range.first)
        if (colon < 0) return null
        return m.range.first to colon
    }
}

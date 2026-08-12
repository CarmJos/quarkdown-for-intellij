package cc.carm.plugin.intellij.quarkdown.lang.completion

import cc.carm.plugin.intellij.quarkdown.QuarkdownBundle
import cc.carm.plugin.intellij.quarkdown.QuarkdownFileType
import cc.carm.plugin.intellij.quarkdown.lang.function.QuarkdownCallParser
import cc.carm.plugin.intellij.quarkdown.lang.lsp.QuarkdownLspFunctionSignatureCache
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext

/**
 * Offers the **not-yet-used parameter names** of a Quarkdown function call as completion
 * items when the caret is inside the call's argument region.
 *
 * The official `quarkdown language-server` only completes parameter names at the
 * "next argument" position (e.g. right after a space), and inside `{...}` it completes
 * the current parameter's allowed *values*. This contributor fills the gap: after
 * completing a function (whose snippet places the caret inside the first `{}`), or when
 * typing inside a call, the popup lists the remaining parameters as `name:` items so the
 * user can quickly switch to a named argument.
 *
 * Parameter order / names come from [QuarkdownLspFunctionSignatureCache] (LSP hover
 * signatures). Named arguments already written are skipped; positional arguments
 * consume a parameter slot.
 */
class QuarkdownParameterNameCompletionContributor : CompletionContributor() {

    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(),
            QuarkdownParameterNameProvider()
        )
    }

    class QuarkdownParameterNameProvider : CompletionProvider<CompletionParameters>() {

        override fun addCompletions(
            parameters: CompletionParameters,
            context: ProcessingContext,
            result: CompletionResultSet
        ) {
            val file = parameters.originalFile
            if (file.fileType != QuarkdownFileType.INSTANCE) return

            // Use the live caret offset (parameters.offset may be stale in the direct
            // contributor invocation; the path contributor relies on the editor caret too).
            val offset = parameters.editor?.caretModel?.offset ?: return
            if (offset <= 0) return
            val text = file.text
            if (offset > text.length) return

            val start = QuarkdownCallParser.findCallStart(text, offset)
            if (start < 0) return
            val call = QuarkdownCallParser.parseCall(text, start) ?: return
            if (offset < call.nameEnd) return

            // Only complement the LSP at the spot where a *positional* argument's value is
            // being filled — e.g. the `{}` the LSP snippet leaves after completing a
            // function. Inside a named argument's value braces (`name:{...}`) the LSP
            // completes that parameter's allowed values, and in the gaps between
            // arguments it completes the parameter names. Offering the sibling
            // parameters there would pollute the popup (e.g. `pageformat`'s parameters
            // shown while filling `size:{...}`).
            if (call.args.none { !it.isNamed && it.containsValueOffset(offset) }) return

            val signature = QuarkdownLspFunctionSignatureCache.getInstance(file.project).getSignature(call.name)
                ?: return
            val remaining = remainingParameters(call, signature.parameterNames)
            if (remaining.isEmpty()) return

            for (paramName in remaining) {
                result.addElement(
                    LookupElementBuilder.create(paramName)
                        .withTypeText(QuarkdownBundle.message("quarkdown.completion.type.parameter"), true)
                        .withTailText(
                            "  ${QuarkdownBundle.message("quarkdown.completion.tail.named.argument")}",
                            true
                        )
                        .withInsertHandler { insertion, _ ->
                            val ed = insertion.editor
                            val doc = ed.document
                            val caret = ed.caretModel.offset
                            doc.insertString(caret, "$paramName:{}")
                            ed.caretModel.moveToOffset(caret + paramName.length + 2)
                        }
                )
            }
        }

        /**
         * Computes the parameters not yet provided by the call.
         *
         * Named arguments map directly; positional arguments consume the next available
         * parameter slot (chained calls reserve slot 0 for the chained value).
         */
        private fun remainingParameters(
            call: QuarkdownCallParser.Call,
            paramNames: List<String>
        ): List<String> {
            val used = mutableSetOf<String>()
            var positional = if (call.isChained) 1 else 0
            for (arg in call.args) {
                if (arg.isNamed) {
                    paramNames.find { it == arg.paramName }?.let { used.add(it) }
                } else {
                    paramNames.getOrNull(positional)?.let { used.add(it) }
                    positional++
                }
            }
            return paramNames.filter { it !in used }
        }
    }
}

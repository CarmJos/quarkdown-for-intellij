package cc.carm.plugin.intellij.quarkdown.lang.completion

import cc.carm.plugin.intellij.quarkdown.lang.function.FunctionRegistry
import cc.carm.plugin.intellij.quarkdown.lang.function.FunctionMetadata
import cc.carm.plugin.intellij.quarkdown.QuarkdownFileType
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext

class QuarkdownCompletionContributor : CompletionContributor() {

    init {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(),
            QuarkdownProvider())
    }

    class QuarkdownProvider : CompletionProvider<CompletionParameters>() {

        override fun addCompletions(
            parameters: CompletionParameters,
            context: ProcessingContext,
            result: CompletionResultSet
        ) {
            val file = parameters.originalFile
            if (file.fileType != QuarkdownFileType.INSTANCE) return

            val editor = parameters.editor
            val offset = editor.caretModel.offset
            val text = editor.document.charsSequence

            val registry = FunctionRegistry.getInstance(file.project)
            val functions = registry.getFunctions()
            if (functions.isEmpty()) return

            val ctx = FunctionCallTokenizer.parseContext(text, offset)

            when {
                ctx.functionName.isEmpty() && !ctx.insideBraces -> {
                    suggestFunctionNames(ctx.prefix, functions, result)
                }
                ctx.insideBraces && ctx.functionName.isNotEmpty() -> {
                    suggestInBraces(ctx, functions, result)
                }
            }
        }

        private fun suggestFunctionNames(
            prefix: String,
            functions: List<FunctionMetadata>,
            result: CompletionResultSet
        ) {
            val lower = prefix.lowercase()
            val matching = if (lower.isEmpty()) functions
                else functions.filter { it.name.startsWith(lower) }

            for (fn in matching) {
                result.addElement(buildFunctionLookup(fn))
            }
        }

        private fun suggestInBraces(
            ctx: FunctionCallTokenizer.FunctionCallContext,
            functions: List<FunctionMetadata>,
            result: CompletionResultSet
        ) {
            val fn = functions.find { it.name == ctx.functionName.lowercase() } ?: return
            val prefix = ctx.paramPrefix.lowercase()

            if (ctx.afterColon) {
                // After "paramName: " — suggest value options
                suggestParamValues(fn, prefix, result)
            } else {
                // Before colon: could be a param name OR a positional value
                // If the text doesn't match any param name, suggest values for the first param
                val visibleParams = fn.parameters.filter { !it.isInjected }
                val matchingParam = visibleParams.find { it.name.startsWith(prefix) }

                if (matchingParam != null) {
                    // Typing a param name — suggest param names
                    suggestParamNames(fn, prefix, result)
                }

                // Always suggest values for the first positional param
                // (Quarkdown supports .func { value } as shorthand for .func { firstParam: value })
                if (visibleParams.isNotEmpty()) {
                    val firstParam = visibleParams.first()
                    if (firstParam.allowedValues != null) {
                        for (value in firstParam.allowedValues.filter { it.startsWith(prefix) }) {
                            result.addElement(
                                LookupElementBuilder.create(value)
                                    .withTypeText(firstParam.type, true)
                                    .withTailText(" (${firstParam.name})", true)
                            )
                        }
                    }
                }
            }
        }

        private fun suggestParamNames(
            fn: FunctionMetadata,
            prefix: String,
            result: CompletionResultSet
        ) {
            val visibleParams = fn.parameters.filter { !it.isInjected }
            if (visibleParams.isEmpty()) return

            for (param in visibleParams.filter { it.name.startsWith(prefix) }) {
                result.addElement(
                    LookupElementBuilder.create(param.name)
                        .withTypeText(param.type, true)
                        .withInsertHandler { insCtx, _ ->
                            val ed = insCtx.editor
                            val pos = ed.caretModel.offset
                            if (param.allowedValues != null) {
                                ed.document.insertString(pos, ": ")
                                ed.caretModel.moveToOffset(pos + 2)
                            }
                        }
                )
            }
        }

        private fun suggestParamValues(
            fn: FunctionMetadata,
            prefix: String,
            result: CompletionResultSet
        ) {
            // prefix at this point is "{paramName}: {partialValue}" or just "{partialValue}"
            val parts = prefix.split(":", limit = 2)
            val paramName = parts[0].trim()
            val valuePrefix = if (parts.size > 1) parts[1].trim().lowercase() else ""

            // First, try to find the named param
            val param = fn.parameters.find { it.name == paramName.lowercase() }
            if (param != null && param.allowedValues != null) {
                for (value in param.allowedValues.filter { it.startsWith(valuePrefix) }) {
                    result.addElement(LookupElementBuilder.create(value).withTypeText(param.type, true))
                }
            } else {
                // No named param matched — suggest values for first positional param
                val visibleParams = fn.parameters.filter { !it.isInjected }
                if (visibleParams.isNotEmpty()) {
                    val firstParam = visibleParams.first()
                    if (firstParam.allowedValues != null) {
                        for (value in firstParam.allowedValues.filter { it.startsWith(valuePrefix) }) {
                            result.addElement(
                                LookupElementBuilder.create(value)
                                    .withTypeText(firstParam.type, true)
                                    .withTailText(" (${firstParam.name})", true)
                            )
                        }
                    }
                }
            }
        }

        private fun buildFunctionLookup(fn: FunctionMetadata): LookupElementBuilder {
            val visible = fn.parameters.filter { !it.isInjected }
            val tailText = if (visible.isNotEmpty())
                "(${visible.joinToString(", ") { "${it.name}: ${it.type}" }})"
            else ""

            return LookupElementBuilder.create(fn.name)
                .withTypeText(tailText, true)
                .withInsertHandler { ctx, _ ->
                    val ed = ctx.editor
                    val pos = ed.caretModel.offset
                    if (visible.isEmpty()) {
                        ed.document.insertString(pos, " ")
                    } else {
                        ed.document.insertString(pos, " {}")
                        ed.caretModel.moveToOffset(pos + 2)
                    }
                }
        }
    }
}

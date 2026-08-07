package cc.carm.plugin.intellij.quarkdown.lang.completion

import cc.carm.plugin.intellij.quarkdown.QuarkdownFileType
import cc.carm.plugin.intellij.quarkdown.lang.function.FunctionMetadata
import cc.carm.plugin.intellij.quarkdown.lang.function.FunctionRegistry
import cc.carm.plugin.intellij.quarkdown.lang.function.ParameterMetadata
import cc.carm.plugin.intellij.quarkdown.lang.function.QuarkdownCallParser.Arg
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext

/**
 * Context-aware completion for Quarkdown function calls, following the real grammar:
 *
 *   .doctype {paged}                      positional argument
 *   .pagemargin position:{bottomcenter}   named argument
 *
 * Supports:
 *  - function-name completion after `.`
 *  - next-argument hints after a complete function name or written arguments
 *  - enum value completion inside `{…}` and right after `name:`
 */
class QuarkdownCompletionContributor : CompletionContributor() {

    init {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), QuarkdownProvider())
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
            if (!ctx.hasCall) return

            when {
                ctx.inFunctionName -> suggestFunctionNames(ctx.namePrefix, functions, result)

                ctx.currentArg != null -> {
                    val fn = functions.find { it.name == ctx.functionName } ?: return
                    val param = resolveParam(fn, ctx.currentArg, ctx.allArgs, ctx.call?.isChained == true)
                    if (param?.allowedValues != null) {
                        suggestValues(param, ctx.valuePrefix, result, wrapInBraces = false)
                    }
                }

                ctx.afterNamedColon -> {
                    val fn = functions.find { it.name == ctx.functionName } ?: return
                    val param = fn.parameters.find { !it.isInjected && it.name == ctx.pendingNamedParam }
                    if (param?.allowedValues != null) {
                        suggestValues(param, "", result, wrapInBraces = true)
                    }
                }

                else -> suggestNextArguments(ctx, functions, result)
            }
        }

        // ------------------------------------------------------------------
        // Function name completion
        // ------------------------------------------------------------------

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

        private fun buildFunctionLookup(fn: FunctionMetadata): LookupElementBuilder {
            val visible = fn.parameters.filter { !it.isInjected }
            // Concise type text: parameter names only, so the popup is not crammed.
            val paramsText = if (visible.isNotEmpty()) visible.joinToString(", ") { it.name } else ""

            return LookupElementBuilder.create(fn, fn.name)
                .withTypeText(paramsText, true)
                .withTailText("  ${fn.module.ifEmpty { "stdlib" }}", true)
                .withInsertHandler { ctx, _ ->
                    val ed = ctx.editor
                    val pos = ed.caretModel.offset
                    if (visible.isEmpty()) {
                        // No arguments → insert a trailing space: `.currentpage `.
                        ed.document.insertString(pos, " ")
                        ed.caretModel.moveToOffset(pos + 1)
                    } else {
                        // Functions take arguments in `{...}` — insert `.background {}`
                        // with the caret inside the braces, ready to type the first value.
                        // This also prevents the next-argument popup from immediately
                        // offering `name:{}` after the name.
                        ed.document.insertString(pos, " {}")
                        ed.caretModel.moveToOffset(pos + 2)
                    }
                }
        }

        // ------------------------------------------------------------------
        // Next-argument completion (after `.name ` or after written arguments)
        // ------------------------------------------------------------------

        private fun suggestNextArguments(
            ctx: FunctionCallTokenizer.FunctionCallContext,
            functions: List<FunctionMetadata>,
            result: CompletionResultSet
        ) {
            val fn = functions.find { it.name == ctx.functionName } ?: return
            val visible = fn.parameters.filter { !it.isInjected }
            if (visible.isEmpty()) return

            val consumed = consumedParams(fn, ctx.allArgs, ctx.call?.isChained == true)
            val remaining = visible.filter { it.name !in consumed }
            if (remaining.isEmpty()) return

            // Suggest the remaining parameters as named arguments: `name:{…}`.
            // Positional shorthand values (`{bottomcenter}`) are suggested once the
            // caret is inside the argument's braces.
            for (param in remaining) {
                result.addElement(buildNamedArgLookup(param))
            }
        }

        private fun buildNamedArgLookup(param: ParameterMetadata): LookupElementBuilder {
            return LookupElementBuilder.create(param.name)
                .withTypeText(param.type, true)
                .withTailText("  named argument", true)
                .withInsertHandler { ctx, _ ->
                    val ed = ctx.editor
                    val start = ctx.startOffset
                    val end = ed.caretModel.offset
                    ed.document.replaceString(start, end, "${param.name}:{}")
                    ed.caretModel.moveToOffset(start + param.name.length + 2)
                }
        }

        private fun consumedParams(fn: FunctionMetadata, args: List<Arg>, chained: Boolean): Set<String> {
            val visible = fn.parameters.filter { !it.isInjected }
            val consumed = mutableSetOf<String>()
            // For chained calls the chained value is the implicit first positional argument.
            var pos = if (chained) 1 else 0
            for (arg in args) {
                if (arg.isNamed) {
                    fn.parameters.find { !it.isInjected && it.name == arg.paramName }
                        ?.let { consumed.add(it.name) }
                } else {
                    visible.getOrNull(pos)?.let { consumed.add(it.name) }
                    pos++
                }
            }
            return consumed
        }

        // ------------------------------------------------------------------
        // Enum / constrained value completion
        // ------------------------------------------------------------------

        /**
         * Resolves the parameter an argument refers to: named → by name,
         * positional → by its position among the non-injected parameters.
         */
        private fun resolveParam(
            fn: FunctionMetadata,
            arg: Arg,
            allArgs: List<Arg>,
            chained: Boolean
        ): ParameterMetadata? {
            if (arg.isNamed) {
                return fn.parameters.find { !it.isInjected && it.name == arg.paramName }
            }
            val visible = fn.parameters.filter { !it.isInjected }
            // For chained calls the chained value occupies the first positional slot.
            var pos = if (chained) 1 else 0
            for (a in allArgs) {
                if (a === arg) return visible.getOrNull(pos)
                if (!a.isNamed) pos++
            }
            return null
        }

        private fun suggestValues(
            param: ParameterMetadata,
            prefix: String,
            result: CompletionResultSet,
            wrapInBraces: Boolean
        ) {
            val values = param.allowedValues ?: return
            val lower = prefix.lowercase()
            for (value in values.filter { it.startsWith(lower) }) {
                val lookup = LookupElementBuilder.create(value)
                    .withTypeText(param.type, true)
                    .withTailText("  ${param.name}", true)
                if (wrapInBraces) {
                    // Inserting after `name:` — wrap the value in braces: `name:{value}`
                    lookup.withInsertHandler { ctx, _ ->
                        val ed = ctx.editor
                        val start = ctx.startOffset
                        val end = ed.caretModel.offset
                        ed.document.replaceString(start, end, "{$value}")
                        ed.caretModel.moveToOffset(start + value.length + 2)
                    }
                }
                result.addElement(lookup)
            }
        }
    }
}

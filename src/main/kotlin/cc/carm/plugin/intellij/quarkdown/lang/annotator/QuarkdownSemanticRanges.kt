package cc.carm.plugin.intellij.quarkdown.lang.annotator

import cc.carm.plugin.intellij.quarkdown.lang.function.FunctionMetadata
import cc.carm.plugin.intellij.quarkdown.lang.function.QuarkdownCallParser
import cc.carm.plugin.intellij.quarkdown.lang.function.QuarkdownCallValidator
import cc.carm.plugin.intellij.quarkdown.lang.highlighter.QuarkdownSyntaxHighlighter
import com.intellij.openapi.editor.colors.TextAttributesKey

/**
 * Pure computation of semantic-highlight ranges for a Quarkdown document (no IntelliJ
 * dependencies), so the annotator stays thin and the logic can be unit-tested.
 *
 * Resolves every function call against the stdlib metadata and yields the semantic
 * attributes layered on top of the lexer-level highlighter:
 *  - known stdlib function names → [QuarkdownSyntaxHighlighter.SEMANTIC_KNOWN_FUNCTION]
 *  - `.name` references resolving to a declared variable → [QuarkdownSyntaxHighlighter.SEMANTIC_VARIABLE_REF]
 *  - argument values that are valid enum members → [QuarkdownSyntaxHighlighter.SEMANTIC_VALID_ENUM]
 *  - named-argument parameter names → [QuarkdownSyntaxHighlighter.SEMANTIC_PARAMETER]
 */
object QuarkdownSemanticRanges {

    data class Highlight(val start: Int, val end: Int, val key: TextAttributesKey)

    fun compute(
        text: String,
        functions: List<FunctionMetadata>,
        variables: Map<String, Int>
    ): List<Highlight> {
        val result = mutableListOf<Highlight>()

        for (dotStart in QuarkdownCallParser.findAllCallStarts(text)) {
            val call = QuarkdownCallParser.parseCall(text, dotStart) ?: continue
            if (call.nameEnd > text.length) continue

            // `.name` that matches a declared variable → variable reference.
            if (call.name in variables) {
                result += Highlight(call.nameStart, call.nameEnd, QuarkdownSyntaxHighlighter.SEMANTIC_VARIABLE_REF)
                continue
            }

            if (functions.isEmpty()) continue
            val fn = QuarkdownCallValidator.resolveFunction(call, functions) ?: continue

            // Known stdlib function name.
            result += Highlight(call.nameStart, call.nameEnd, QuarkdownSyntaxHighlighter.SEMANTIC_KNOWN_FUNCTION)

            val (resolved, _) = QuarkdownCallValidator.resolveArgs(call, fn)
            for (r in resolved) {
                val param = r.param ?: continue
                val arg = r.arg

                // Named-argument parameter name (`position:` in `position:{top}`).
                // The parsed `nameEnd` includes the trailing `:` (the regex consumes it),
                // so the highlight covers only the name itself; the colon has its own
                // lexer-level attribute (FUNCTION_PARAMETER_COLON).
                if (arg.isNamed && arg.nameStart >= 0) {
                    val nameEnd = arg.nameStart + (arg.paramName?.length ?: 0)
                    if (nameEnd <= text.length && nameEnd > arg.nameStart) {
                        result += Highlight(arg.nameStart, nameEnd, QuarkdownSyntaxHighlighter.SEMANTIC_PARAMETER)
                    }
                }

                // Valid enum / constrained values inside the braces.
                val allowed = param.allowedValues
                if (allowed != null) {
                    val value = QuarkdownCallValidator.normalizeValue(arg.raw)
                    if (value.isNotEmpty() && value in allowed) {
                        val trim = arg.raw.indexOfFirst { !it.isWhitespace() }
                        val trimEnd = arg.raw.indexOfLast { !it.isWhitespace() }
                        val start = if (trim >= 0) arg.rawStart + trim else arg.rawStart
                        val end = if (trimEnd >= 0) arg.rawStart + trimEnd + 1 else arg.rawEnd
                        if (end <= text.length && end > start) {
                            result += Highlight(start, end, QuarkdownSyntaxHighlighter.SEMANTIC_VALID_ENUM)
                        }
                    }
                }
            }
        }
        return result
    }
}

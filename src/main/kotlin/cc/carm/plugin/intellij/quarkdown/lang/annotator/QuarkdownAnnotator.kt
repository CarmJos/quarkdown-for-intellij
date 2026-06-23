package cc.carm.plugin.intellij.quarkdown.lang.annotator

import cc.carm.plugin.intellij.quarkdown.lang.function.FunctionRegistry
import cc.carm.plugin.intellij.quarkdown.lang.function.ParameterMetadata
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

/**
 * Validates Quarkdown function calls:
 *   .funcName { param1: val1, param2: val2 }
 *
 * Quarkdown supports "unnamed" (positional) syntax: the bare value inside
 * braces is treated as the value for the first non-injected parameter:
 *   .ref { myId }           →  ref.id = "myId"
 *   .doctype { paged }      →  doctype.type = "paged"
 *
 * Named params override: .ref { id: myId } also works.
 */
class QuarkdownAnnotator : Annotator {

    private val functionCallPattern = Regex("""\.([a-zA-Z][a-zA-Z0-9]*)\s*\{([^}]*)}""")
    private val namedParamPattern = Regex("""([a-zA-Z][a-zA-Z0-9]*)\s*:\s*(\S+)""")
    // Positional value: anything that is NOT a named-param pair
    private val positionalValuePattern = Regex("""\s*([^,:]+)\s*""")

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element !is PsiFile) return

        val text = element.text
        val registry = FunctionRegistry.getInstance(element.project)
        val functions = registry.getFunctions()
        if (functions.isEmpty()) return

        for (match in functionCallPattern.findAll(text)) {
            val funcName = match.groupValues[1].lowercase()
            val rawParams = match.groupValues[2]

            val funcDef = functions.find { it.name == funcName }

            if (funcDef == null) {
                annotateUnknownFunction(holder, match, funcName)
                continue
            }

            validateParams(holder, funcDef, rawParams, match.range.first)
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun annotateUnknownFunction(holder: AnnotationHolder, match: MatchResult, funcName: String) {
        val text = match.value
        val dotIdx = text.indexOf('.')
        if (dotIdx < 0) return
        val range = TextRange(match.range.first + dotIdx + 1, match.range.first + dotIdx + 1 + funcName.length)
        holder.newAnnotation(HighlightSeverity.ERROR, "Unknown function '$funcName'")
            .range(range)
            .create()
    }

    private fun validateParams(
        holder: AnnotationHolder,
        funcDef: cc.carm.plugin.intellij.quarkdown.lang.function.FunctionMetadata,
        rawParams: String,
        funcStart: Int
    ) {
        if (rawParams.isBlank()) return

        val visibleParams = funcDef.parameters.filter { !it.isInjected }
        if (visibleParams.isEmpty()) return

        // Collect named params
        val namedParams = mutableMapOf<String, String>()
        for (np in namedParamPattern.findAll(rawParams)) {
            namedParams[np.groupValues[1].lowercase()] = np.groupValues[2]
        }

        // Check named params for validity
        for (np in namedParamPattern.findAll(rawParams)) {
            val paramName = np.groupValues[1].lowercase()
            val paramValue = np.groupValues[2]
            val paramDef = funcDef.parameters.find { it.name == paramName }

            if (paramDef == null) {
                val offset = funcStart + rawParams.indexOf(np.groupValues[1])
                holder.newAnnotation(HighlightSeverity.ERROR, "Unknown parameter '$paramName' for '${funcDef.name}'")
                    .range(TextRange(offset, offset + paramName.length))
                    .create()
                continue
            }

            if (paramDef.allowedValues != null && paramValue !in paramDef.allowedValues) {
                val colonIdx = rawParams.indexOf(":", np.range.first)
                if (colonIdx >= 0) {
                    val valueIdx = rawParams.indexOf(paramValue, colonIdx)
                    if (valueIdx >= 0) {
                        val offset = funcStart + valueIdx
                        holder.newAnnotation(HighlightSeverity.ERROR,
                            "Invalid value '$paramValue' for '${paramDef.name}'. Expected: ${paramDef.allowedValues.joinToString(", ")}")
                            .range(TextRange(offset, offset + paramValue.length))
                            .create()
                    }
                }
            }
        }

        // Handle positional (unnamed) value — text inside braces that isn't part of a named pair
        if (namedParams.isEmpty()) {
            // Everything in braces is a positional value for the first non-injected param
            val value = rawParams.trim().removeSurrounding("\"").removeSurrounding("'")
            if (value.isNotEmpty()) {
                val firstParam = visibleParams.first()
                if (firstParam.allowedValues != null && value !in firstParam.allowedValues) {
                    val valueOffset = funcStart + rawParams.indexOf(value)
                    holder.newAnnotation(HighlightSeverity.ERROR,
                        "Invalid value '$value' for '${firstParam.name}'. Expected: ${firstParam.allowedValues.joinToString(", ")}")
                        .range(TextRange(valueOffset, valueOffset + value.length))
                        .create()
                }
            }
        }
    }
}

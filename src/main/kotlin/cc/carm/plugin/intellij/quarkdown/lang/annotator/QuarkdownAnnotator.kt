package cc.carm.plugin.intellij.quarkdown.lang.annotator

import cc.carm.plugin.intellij.quarkdown.lang.function.FunctionRegistry
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

class QuarkdownAnnotator : Annotator {

    private val functionCallPattern = Regex("""\.([a-zA-Z][a-zA-Z0-9]*)\s*\{([^}]*)}""")
    private val paramPattern = Regex("""([a-zA-Z][a-zA-Z0-9]*)(\s*:\s*(\S+))?""")

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
                val range = match.range
                val start = range.first + 1
                val end = start + funcName.length
                if (start < text.length && end <= text.length) {
                    val textRange = com.intellij.openapi.util.TextRange(start, end)
                    holder.newAnnotation(HighlightSeverity.ERROR, "Unknown function '$funcName'")
                        .range(textRange)
                        .create()
                }
                continue
            }

            // Validate parameters
            validateParameters(holder, funcDef, rawParams, match.range.first, text)
        }
    }

    private fun validateParameters(
        holder: AnnotationHolder,
        funcDef: cc.carm.plugin.intellij.quarkdown.lang.function.FunctionMetadata,
        rawParams: String,
        matchStart: Int,
        text: String
    ) {
        if (rawParams.isBlank()) return

        for (paramMatch in paramPattern.findAll(rawParams)) {
            val paramName = paramMatch.groupValues[1].lowercase()

            val paramDef = funcDef.parameters.find { it.name == paramName }
            val paramStartInText = matchStart + text.substring(matchStart).indexOf(paramName)

            if (paramDef == null) {
                val paramRange = com.intellij.openapi.util.TextRange(
                    paramStartInText, paramStartInText + paramName.length
                )
                holder.newAnnotation(HighlightSeverity.ERROR,
                    "Unknown parameter '$paramName' for '${funcDef.name}'")
                    .range(paramRange)
                    .create()
            } else if (paramDef.allowedValues != null && paramMatch.groupValues[3].isNotEmpty()) {
                val value = paramMatch.groupValues[3].lowercase()
                if (!paramDef.allowedValues.contains(value)) {
                    val colonIdx = rawParams.indexOf(":", paramMatch.range.first) + 1
                    val valueStart = matchStart + colonIdx + rawParams.substring(colonIdx).indexOf(value)
                    val valueRange = com.intellij.openapi.util.TextRange(
                        valueStart, valueStart + value.length
                    )
                    holder.newAnnotation(HighlightSeverity.ERROR,
                        "Invalid value '$value' for '${paramDef.name}'. Expected: ${paramDef.allowedValues.joinToString(", ")}")
                        .range(valueRange)
                        .create()
                }
            }
        }
    }
}

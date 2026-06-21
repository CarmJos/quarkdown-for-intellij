package cc.carm.plugin.intellij.quarkdown.lang.completion

object FunctionCallTokenizer {

    data class FunctionCallContext(
        val prefix: String = "",          // partial function name being typed (after .)
        val functionName: String = "",    // resolved function name (before {)
        val insideBraces: Boolean = false,
        val paramPrefix: String = "",     // partial text in braces before cursor
        val partialParamName: String = "", // param name being typed after comma
        val afterColon: Boolean = false,  // cursor after a named param colon
        val afterChaining: Boolean = false // cursor after ::
    )

    fun parseContext(text: CharSequence, offset: Int): FunctionCallContext {
        if (offset <= 0) return FunctionCallContext()

        val before = text.substring(0, offset)

        val dotIdx = findLastDot(before)
        if (dotIdx < 0) return FunctionCallContext()

        val afterDot = before.substring(dotIdx + 1)

        val braceOpen = afterDot.indexOf('{')
        val braceClose = afterDot.lastIndexOf('}')

        if (braceOpen < 0) {
            return FunctionCallContext(prefix = afterDot.trim())
        }

        val funcName = afterDot.substring(0, braceOpen).trim()
        val afterBrace = afterDot.substring(afterDot.lastIndexOf('}') + 1)

        if (braceClose >= 0) {
            return FunctionCallContext(
                functionName = funcName,
                prefix = afterBrace.trim()
            )
        }

        val inside = afterDot.substring(braceOpen + 1)
        val lastComma = inside.lastIndexOf(',')
        val lastColon = inside.lastIndexOf(':')

        val partial = if (lastComma >= 0) {
            inside.substring(lastComma + 1).trim()
        } else {
            inside.trim()
        }

        val afterColon = lastColon > lastComma

        return FunctionCallContext(
            functionName = funcName,
            insideBraces = true,
            paramPrefix = partial,
            afterColon = afterColon,
            afterChaining = afterDot.contains("::")
        )
    }

    private fun findLastDot(text: String): Int {
        var idx = text.lastIndexOf('.')
        while (idx >= 0) {
            if (idx == 0 || text[idx - 1] != ':') {
                val c = if (idx > 0) text[idx - 1] else ' '
                if (!c.isLetterOrDigit() && c != '_') return idx
            }
            idx = text.lastIndexOf('.', idx - 1)
        }
        return -1
    }
}

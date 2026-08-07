package cc.carm.plugin.intellij.quarkdown.lang.completion

import cc.carm.plugin.intellij.quarkdown.lang.function.QuarkdownCallParser
import cc.carm.plugin.intellij.quarkdown.lang.function.QuarkdownCallParser.Arg
import cc.carm.plugin.intellij.quarkdown.lang.function.QuarkdownCallParser.Call

/**
 * Builds a rich, completion-oriented description of the function call around the caret,
 * based on [QuarkdownCallParser].
 *
 * The returned [FunctionCallContext] answers questions the completion contributor needs:
 *  - is the user typing a function name after `.`?
 *  - is the caret inside the value braces of an argument?
 *  - is the caret right after a `name:` (named argument, value braces not yet typed)?
 *  - which arguments have already been written (for "suggest the next parameter")?
 */
object FunctionCallTokenizer {

    data class FunctionCallContext(
        /** True when a function call was detected before/at the caret. */
        val hasCall: Boolean = false,
        /** True when the caret is inside the function name (typing `.page…`). */
        val inFunctionName: Boolean = false,
        /** Partial function name typed after `.` (used for name completion). */
        val namePrefix: String = "",
        /** Resolved function name (lowercase); may be partial when [inFunctionName]. */
        val functionName: String = "",
        /** The argument whose value braces contain the caret, if any. */
        val currentArg: Arg? = null,
        /** Partial value typed inside the current argument's braces. */
        val valuePrefix: String = "",
        /** True when the caret is right after a `name:` with the value braces missing. */
        val afterNamedColon: Boolean = false,
        /** Parameter name of the pending named argument (when [afterNamedColon]). */
        val pendingNamedParam: String? = null,
        /** All arguments parsed for the call (including the incomplete current one). */
        val allArgs: List<Arg> = emptyList(),
        /** Arguments fully written before the caret. */
        val writtenArgs: List<Arg> = emptyList(),
        /** Absolute offset where parsing of the call's arguments stopped. */
        val callEnd: Int = -1,
        /** The underlying parsed call. */
        val call: Call? = null
    )

    fun parseContext(text: CharSequence, offset: Int): FunctionCallContext {
        val source = text.toString()
        if (offset <= 0) return FunctionCallContext()

        val dot = QuarkdownCallParser.findCallStart(source, offset)
        if (dot < 0) return FunctionCallContext()

        // Bare `.` (just typed, no function name yet) → function-name completion.
        // This also covers a `.` typed at the start of a fresh line after another
        // function call: findCallStart only returns dots within the current logical
        // statement, so a new-line dot is a brand-new function call.
        val nameStart = dot + 1
        if (nameStart >= source.length || dot == offset - 1) {
            val prefix = source.substring(nameStart, offset.coerceIn(nameStart, source.length))
            return FunctionCallContext(
                hasCall = true,
                inFunctionName = true,
                namePrefix = prefix,
                functionName = prefix,
                callEnd = offset,
                call = null
            )
        }

        // A dot on a fresh statement: the caret is right after `.`, possibly with the
        // start of a name (`.<new call>`). Treat as a new function name.
        // Only when the caret is still within the (possibly partial) name or right after
        // the dot — not when the call already has arguments or trailing spaces.
        if (isNewStatementDot(source, dot, offset)) {
            val partialName = source.substring(nameStart, offset.coerceIn(nameStart, source.length))
            // If there is no partial name typed yet (or it's a plain identifier with no
            // args/braces), treat as function-name completion.
            val isFreshName = partialName.isNotEmpty() && partialName.all { it.isLetterOrDigit() }
            if (isFreshName || nameStart >= offset) {
                return FunctionCallContext(
                    hasCall = true,
                    inFunctionName = true,
                    namePrefix = partialName,
                    functionName = partialName,
                    callEnd = offset,
                    call = null
                )
            }
        }

        val call = QuarkdownCallParser.parseCall(source, dot) ?: return FunctionCallContext()

        // 1) Typing the function name itself (caret at/inside the name).
        if (offset <= call.nameEnd) {
            val prefix = source.substring(call.nameStart, offset.coerceIn(call.nameStart, call.nameEnd))
            return FunctionCallContext(
                hasCall = true,
                inFunctionName = true,
                namePrefix = prefix,
                functionName = call.name,
                allArgs = call.args,
                writtenArgs = call.args.filter { it.braceEnd <= offset },
                callEnd = call.end,
                call = call
            )
        }

        // 2) Caret inside the value braces of an argument.
        call.args.firstOrNull { it.containsValueOffset(offset) }?.let { arg ->
            val valuePrefix = source.substring(arg.rawStart, offset.coerceIn(arg.rawStart, arg.rawEnd))
            return FunctionCallContext(
                hasCall = true,
                functionName = call.name,
                currentArg = arg,
                valuePrefix = valuePrefix,
                allArgs = call.args,
                writtenArgs = call.args.filter { it.braceEnd <= offset },
                callEnd = call.end,
                call = call
            )
        }

        // 3) Caret right after `name:` with the value braces not yet typed.
        val pendingNamed = detectPendingNamedArg(source, call, offset)
        if (pendingNamed != null) {
            return FunctionCallContext(
                hasCall = true,
                functionName = call.name,
                afterNamedColon = true,
                pendingNamedParam = pendingNamed,
                allArgs = call.args,
                writtenArgs = call.args.filter { it.braceEnd <= offset },
                callEnd = call.end,
                call = call
            )
        }

        // 4) Otherwise: after the function name / after complete arguments.
        return FunctionCallContext(
            hasCall = true,
            functionName = call.name,
            allArgs = call.args,
            writtenArgs = call.args.filter { it.braceEnd <= offset },
            callEnd = call.end,
            call = call
        )
    }

    /**
     * Detects a `name:` typed at the caret whose value braces are still missing.
     * Returns the lowercase parameter name, or `null`.
     */
    private fun detectPendingNamedArg(text: String, call: Call, offset: Int): String? {
        val segmentStart = call.nameEnd
        if (offset <= segmentStart) return null
        val segment = text.substring(segmentStart, offset)
        // the last `name:` in the segment with nothing but whitespace after the colon
        val matches = Regex("""([a-zA-Z][a-zA-Z0-9]*)\s*:""").findAll(segment)
        val last = matches.lastOrNull() ?: return null
        val afterColon = segment.substring(last.range.last + 1)
        if (afterColon.any { it != ' ' && it != '\t' }) return null
        return last.groupValues[1].lowercase()
    }

    /**
     * True when the dot at [dot] begins a brand-new statement rather than belonging to
     * an earlier call via `\`-continuation lines.
     *
     * A dot is a new statement when:
     *  - no `\`-continued line separates [dot] from [offset], AND
     *  - the only things before it on its line are whitespace (a fresh line).
     */
    private fun isNewStatementDot(text: String, dot: Int, offset: Int): Boolean {
        // If any line between the dot and the caret ends with `\`, the caret is inside
        // the parent call's continuation → not a new statement.
        var i = dot
        while (i < offset) {
            if (text[i] == '\n') {
                if (QuarkdownCallParser.isContinuationLine(text, i)) return false
            }
            i++
        }
        // Otherwise: new statement when the dot is the first non-space char of its line.
        var j = dot - 1
        while (j >= 0 && (text[j] == ' ' || text[j] == '\t')) j--
        if (j < 0) return true
        return text[j] == '\n' || text[j] == '\r'
    }
}

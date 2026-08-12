package cc.carm.plugin.intellij.quarkdown.lang.function

/**
 * A small, dependency-free parser for the Quarkdown function-call grammar:
 *
 * ```
 * .func {arg1} {arg2}              positional arguments (each wrapped in braces)
 * .func name:{arg} other:{arg}     named arguments
 * .multiply {6} by:{3}             positional then named (named must come last)
 * .a {x}::b {y}                    chaining: `.a::b` == `.b {.a}`
 * ```
 *
 * Arguments may span multiple lines and contain nested function calls (the parser
 * tracks brace depth and skips quoted strings when matching the closing brace).
 *
 * The parser is deliberately free of IntelliJ dependencies so it can be unit-tested
 * and reused by the annotator, the completion contributor and the documentation
 * provider.
 */
object QuarkdownCallParser {

    private val functionNameRegex = Regex("[a-zA-Z][a-zA-Z0-9]*")
    private val namedArgRegex = Regex("""([a-zA-Z][a-zA-Z0-9]*)\s*:""")

    /** One parsed argument of a call. */
    data class Arg(
        /** Lowercase parameter name for named arguments, `null` for positional ones. */
        val paramName: String?,
        /** Raw text between the braces (without the braces). */
        val raw: String,
        /** Absolute offset of the raw value text (first char after `{`). */
        val rawStart: Int,
        /** Absolute offset just after the raw value text (position of `}`). */
        val rawEnd: Int,
        /** Absolute offset of the opening brace. */
        val braceStart: Int,
        /** Absolute offset just after the closing brace. */
        val braceEnd: Int,
        /** Absolute offset of the parameter name for named arguments, `-1` otherwise. */
        val nameStart: Int = -1,
        /** Absolute offset just after the parameter name for named arguments, `-1` otherwise. */
        val nameEnd: Int = -1
    ) {
        /** True when the arg is `name:` prefixed. */
        val isNamed: Boolean get() = paramName != null

        /** Offset range of the whole argument (name + `:` + braces). */
        val fullStart: Int get() = if (isNamed) nameStart else braceStart
        val fullEnd: Int get() = braceEnd

        /** Whether the given absolute offset lies inside the value braces (before the closing `}`). */
        fun containsValueOffset(offset: Int): Boolean = offset in rawStart until braceEnd
    }

    /** A parsed function call. */
    data class Call(
        /** Lowercase function name. */
        val name: String,
        /** Absolute offset of the call start (the `.` or the first `:` of `::`). */
        val start: Int,
        /** Absolute offset of the first character of the name. */
        val nameStart: Int,
        /** Absolute offset just after the name. */
        val nameEnd: Int,
        val args: List<Arg>,
        /** Absolute offset where parsing of arguments stopped. */
        val end: Int,
        /** True when the call is followed by an indented body argument (see wiki). */
        val hasBodyArgument: Boolean = false,
        /** True when this call was reached through `::` chaining. */
        val isChained: Boolean = false,
        /** Name of the function the chain started from (when [isChained]). */
        val chainRoot: String? = null
    )

    /**
     * Finds the nearest function-call start at or before [offset], for completion/editing
     * contexts.
     *
     * A call can start with `.name`, with a bare `.` (no name typed yet — the user just
     * pressed `.` and auto-popup should fire), or with `::name` for a chained call.
     * Returns the index of the `.` or of the first `:` of the `::`, or `-1` when none.
     *
     * The scan is limited to the current logical statement: a call on a previous line
     * only "owns" the caret when its line (or a chain of lines) ends with `\` (line
     * continuation, e.g. `.tableofcontents \`). Otherwise a `.` typed on a fresh line
     * starts a NEW function call and must not be treated as an argument of an earlier
     * call (this previously caused `.pageformat`'s parameters to be suggested after
     * `.pageformat` when a new `.` was typed below it).
     */
    fun findCallStart(text: String, offset: Int): Int {
        val pos = offset.coerceIn(0, text.length - 1)

        // Walk backwards across `\`-continued lines to find the first line of the
        // current logical statement. The caret must be within this statement.
        var lineStart = pos
        var scanEnd = pos
        while (true) {
            val nl = text.lastIndexOf('\n', scanEnd - 1)
            if (nl < 0) {
                lineStart = 0
                break
            }
            if (isContinuationLine(text, nl)) {
                // The line ending at `nl` continues into the next line → merge upward.
                lineStart = nl + 1
                scanEnd = nl
            } else {
                lineStart = nl + 1
                break
            }
        }

        // Scan within the statement for the nearest call start.
        var i = pos
        while (i >= lineStart) {
            if (isCallStartAt(text, i, allowBareDot = true)) return i
            i--
        }
        return -1
    }

    /**
     * True when the line ending at [newlineIndex] is a continuation: its content
     * (ignoring trailing whitespace) ends with `\`.
     */
    fun isContinuationLine(text: String, newlineIndex: Int): Boolean {
        var i = newlineIndex - 1
        if (i < 0) return false
        while (i >= 0 && (text[i] == ' ' || text[i] == '\t')) i--
        return i >= 0 && text[i] == '\\'
    }

    /**
     * Finds all function-call starts in the whole [text] (used by the annotator).
     *
     * Only `.name` starts are reported here: bare `::name` chains (e.g. `Foo::bar`
     * in prose) must not be validated independently to avoid false positives.
     */
    fun findAllCallStarts(text: String): List<Int> {
        val result = mutableListOf<Int>()
        var i = 0
        while (i < text.length) {
            if (text[i] == '.' && isFunctionStartDot(text, i)) {
                val m = functionNameRegex.find(text, i + 1)
                if (m != null && m.range.first == i + 1) {
                    result.add(i)
                    i = m.range.last + 1
                    continue
                }
            }
            i++
        }
        return result
    }

    /** True when a call (`.name`, bare `.`, or `::name`) starts at [i]. */
    fun isCallStartAt(text: String, i: Int, allowBareDot: Boolean = false): Boolean {
        if (i < 0 || i >= text.length) return false
        if (text[i] == '.') {
            // Bare `.` at the end of the input (just typed, name not yet written).
            if (allowBareDot && i + 1 >= text.length) {
                return dotPrecededByNonWord(text, i)
            }
            return isFunctionStartDot(text, i)
        }
        if (text[i] == ':' && text.getOrNull(i + 1) == ':' && text.getOrNull(i + 2)?.isLetter() == true) {
            // `::name` chain segment
            return true
        }
        return false
    }

    /** True when [i] is a `.` not preceded by a word character (so `3.14`, `foo.bar`, `..` are excluded). */
    private fun dotPrecededByNonWord(text: String, i: Int): Boolean {
        if (i == 0) return true
        val prev = text[i - 1]
        return !prev.isLetterOrDigit() && prev != '_'
    }

    private fun isNameAt(text: String, idx: Int): Boolean {
        return text.getOrNull(idx)?.isLetter() == true
    }

    /**
     * A dot is a function-call start when it is followed by a letter and not preceded
     * by a word character (so `3.14`, `foo.bar` and `..` are excluded).
     */
    fun isFunctionStartDot(text: String, dotIdx: Int): Boolean {
        if (text[dotIdx] != '.') return false
        if (!isNameAt(text, dotIdx + 1)) return false
        if (dotIdx == 0) return true
        val prev = text[dotIdx - 1]
        return !prev.isLetterOrDigit() && prev != '_'
    }

    /** Parses the call starting at [start] (either `.` or the first `:` of `::`). Returns `null` when invalid. */
    fun parseCall(text: String, start: Int): Call? {
        if (start < 0 || start >= text.length) return null
        val chained = text[start] == ':'
        if (!chained && text[start] != '.') return null

        val nameStart = if (chained) start + 2 else start + 1
        val nameM = functionNameRegex.find(text, nameStart) ?: return null
        if (nameM.range.first != nameStart) return null

        val nameEnd = nameM.range.last + 1
        val name = nameM.value.lowercase()

        val (args, pos) = parseArguments(text, nameEnd)

        // Detect an indented body argument following the call: right after the last
        // argument's closing brace (or the name when there are no args), a newline
        // followed by a line indented by at least two spaces or one tab marks a body.
        val afterArgs = args.lastOrNull()?.braceEnd ?: nameEnd
        val hasBodyArgument = detectBodyArgument(text, afterArgs)

        // Determine chain root: the previous call whose end is immediately followed by `::`
        val chainRoot = detectChainRoot(text, start, chained)

        return Call(
            name = name,
            start = start,
            nameStart = nameStart,
            nameEnd = nameEnd,
            args = args,
            end = pos,
            hasBodyArgument = hasBodyArgument,
            isChained = chained,
            chainRoot = chainRoot
        )
    }

    /** Parses the argument list of a call, returning the parsed args and the position after the list. */
    private fun parseArguments(text: String, nameEnd: Int): Pair<List<Arg>, Int> {
        val args = mutableListOf<Arg>()
        var pos = nameEnd
        val n = text.length

        while (pos < n) {
            val c = text[pos]
            when {
                c.isWhitespace() -> pos++

                c == '\\' -> {
                    // line continuation: consume `\` and the newline
                    pos++
                    if (pos < n && text[pos] == '\n') pos++
                }

                c == '{' -> {
                    val close = findClosingBrace(text, pos)
                    args.add(
                        Arg(
                            paramName = null,
                            raw = text.substring(pos + 1, close),
                            rawStart = pos + 1,
                            rawEnd = close,
                            braceStart = pos,
                            braceEnd = close + 1
                        )
                    )
                    pos = close + 1
                }

                else -> {
                    // maybe a named argument `name:{...}`
                    val named = parseNamedArgument(text, pos) ?: break
                    args.add(named)
                    pos = named.braceEnd
                }
            }
        }
        return args to pos
    }

    /** Parses a named argument `name:{...}` at [pos]; returns the [Arg], or `null` when [pos] is not one. */
    private fun parseNamedArgument(text: String, pos: Int): Arg? {
        val nm = namedArgRegex.find(text, pos) ?: return null
        if (nm.range.first != pos) return null
        var afterColon = nm.range.last + 1
        while (afterColon < text.length && text[afterColon].isWhitespace()) afterColon++
        if (afterColon >= text.length || text[afterColon] != '{') return null
        val close = findClosingBrace(text, afterColon)
        return Arg(
            paramName = nm.groupValues[1].lowercase(),
            raw = text.substring(afterColon + 1, close),
            rawStart = afterColon + 1,
            rawEnd = close,
            braceStart = afterColon,
            braceEnd = close + 1,
            nameStart = nm.range.first,
            nameEnd = nm.range.last + 1
        )
    }

    /**
     * Detects an indented body argument following the call: right after the last
     * argument's closing brace (or the name when there are no args), a newline
     * followed by a line indented by at least two spaces or one tab marks a body.
     */
    private fun detectBodyArgument(text: String, afterArgs: Int): Boolean {
        if (afterArgs >= text.length) return false
        var scan = afterArgs
        while (scan < text.length && (text[scan] == ' ' || text[scan] == '\t')) scan++
        if (scan >= text.length || text[scan] != '\n') return false
        var lineStart = scan + 1
        if (lineStart < text.length && text[lineStart] == '\r') lineStart++
        var indent = 0
        var j = lineStart
        while (j < text.length && (text[j] == ' ' || text[j] == '\t')) {
            indent++
            j++
        }
        return j < text.length && text[j] != '\n' && indent >= 2
    }

    /** Determines the chain root: the previous call whose end is immediately followed by `::`. */
    private fun detectChainRoot(text: String, start: Int, chained: Boolean): String? {
        if (!chained) return null
        val prevStart = findCallStart(text, start - 1)
        if (prevStart < 0) return null
        val prevCall = parseCall(text, prevStart)
        return if (prevCall != null && prevCall.end == start) prevCall.name else null
    }

    /**
     * Collects document-level variable declarations (`.var {name} {value}`) and returns
     * a map of lowercase variable name → offset of the name's raw text.
     *
     * Quarkdown resolves `.name` to a declared variable when one exists (falling back to
     * a function call otherwise), so callers can use this to skip variable references.
     */
    fun findVarDeclarations(text: String): Map<String, Int> {
        val result = LinkedHashMap<String, Int>()
        for ((name, _, nameOffset) in findVarDeclarationEntries(text)) {
            result.putIfAbsent(name, nameOffset)
        }
        return result
    }

    /**
     * Collects document-level variable declarations (`.var {name} {value}`) and returns
     * a map of lowercase variable name → assigned value.
     *
     * Used by the completion contributor (variable-name suggestions) and the folding
     * builder (variable-reference preview folds). Declarations without a value argument
     * are skipped — there is nothing meaningful to preview or suggest.
     */
    fun findVarValues(text: String): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        for ((name, value, _) in findVarDeclarationEntries(text)) {
            if (value.isNotEmpty()) result.putIfAbsent(name, value)
        }
        return result
    }

    /** A parsed `.var` declaration: lowercase name, trimmed value and the name's raw offset. */
    private data class VarEntry(val name: String, val value: String, val nameOffset: Int)

    /**
     * Parses every `.var {name} {value}` declaration, yielding one [VarEntry] per
     * declaration. Unlike [findVarDeclarations], the value argument is optional here so
     * name-only declarations are still indexed for reference/navigation purposes.
     */
    private fun findVarDeclarationEntries(text: String): List<VarEntry> {
        val entries = mutableListOf<VarEntry>()
        for (start in findAllCallStarts(text)) {
            val call = parseCall(text, start) ?: continue
            if (call.name != "var") continue
            val nameArg = call.args.firstOrNull { !it.isNamed }
                ?: call.args.firstOrNull { it.paramName == "name" }
                ?: continue
            val valueArg = call.args.firstOrNull { it.paramName == "value" }
                ?: call.args.firstOrNull { it !== nameArg && !it.isNamed }
            val rawName = nameArg.raw.trim()
            val name = rawName.removeSurrounding("\"").removeSurrounding("'").trim()
            if (name.isNotEmpty()) {
                entries.add(VarEntry(name.lowercase(), valueArg?.raw?.trim().orEmpty(), nameArg.rawStart))
            }
        }
        return entries
    }

    /** Finds the index of the `}` closing the brace opened at [openIdx]. */
    fun findClosingBrace(text: String, openIdx: Int): Int {
        var depth = 0
        var quote: Char? = null
        var i = openIdx
        while (i < text.length) {
            val c = text[i]
            if (quote != null) {
                if (c == quote) quote = null
                if (c == '\\') i++ // skip escaped char
            } else when (c) {
                '"', '\'' -> quote = c
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
            i++
        }
        return text.length
    }
}

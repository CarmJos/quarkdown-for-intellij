package cc.carm.plugin.intellij.quarkdown.lang.latex

/**
 * Pure (no IntelliJ dependencies) lexer and structural checker for TeX/LaTeX content
 * written inside Quarkdown equations (`$ ... $` and `$$$ ... $$$`, see
 * [QuarkdownEquationRegions]).
 *
 * Quarkdown renders equations with KaTeX (see the *TeX formulae* wiki page), so only the
 * structural side of TeX is analysed here — the token kinds exist for syntax *coloring*,
 * while [check] reports the errors that are unambiguously wrong regardless of which
 * commands are used (`\begin` / `\end` pairing, brace balance, dangling commands).
 *
 * Unknown-command detection is deliberately **not** attempted: TeX ships thousands of
 * primitives, KaTeX implements a large subset with extensions, and Quarkdown documents can
 * declare their own commands via `.texmacro` — flagging "unknown" commands would produce
 * mostly false positives.
 *
 * Kept dependency-free so the logic can be unit-tested and reused by the editor annotator.
 */
object QuarkdownLatexSyntax {

    /** Semantic class of a TeX token, mapped to a color on the editor side. */
    enum class TokenKind {
        /** A control sequence: `\frac`, `\alpha`, `\\`. */
        COMMAND,

        /** A `\begin{name}` / `\end{name}` environment delimiter (braces and name included). */
        ENVIRONMENT,

        /** A grouping brace `{` or `}`. */
        BRACE,

        /** An optional-argument bracket `[` or `]`. */
        BRACKET,

        /** A superscript marker `^`. */
        SUPERSCRIPT,

        /** A subscript marker `_`. */
        SUBSCRIPT,

        /** A macro parameter marker such as `#1` (used by `.texmacro` definitions). */
        PARAMETER,

        /** A numeric literal such as `3.14`. */
        NUMBER,

        /** A math operator or punctuation character, e.g. `+` or `=`. */
        OPERATOR,

        /** A `%` comment running to the end of the line. */
        COMMENT,

        /** Anything else (identifiers, spaces, …). */
        TEXT
    }

    /** A single TeX token as an absolute `[start, end)` range inside the analysed text. */
    data class Token(val kind: TokenKind, val start: Int, val end: Int)

    /** A structural problem found by [check]. */
    enum class ProblemKind {
        /** A `{` that is never closed. */
        UNCLOSED_BRACE,

        /** A `}` with no matching `{`. */
        UNEXPECTED_BRACE,

        /** A `\begin{name}` environment that is never closed. */
        MISSING_END,

        /** A `\end{name}` with no matching `\begin`, or closing a different environment. */
        UNEXPECTED_END,

        /** `\begin{}` / `\end{}` with a blank environment name. */
        EMPTY_ENVIRONMENT,

        /** A trailing `\` with no control word after it. */
        DANGLING_COMMAND,

        /** A `#` macro parameter marker with no digits after it. */
        EMPTY_PARAMETER
    }

    /**
     * A structural problem as an absolute `[start, end)` range plus the offending name
     * (the environment name for [ProblemKind.MISSING_END] / [ProblemKind.UNEXPECTED_END],
     * empty otherwise).
     */
    data class Problem(val kind: ProblemKind, val start: Int, val end: Int, val name: String = "")

    // ------------------------------------------------------------------
    // Tokenizer
    // ------------------------------------------------------------------

    /** Characters treated as operators/punctuation for coloring purposes. */
    private const val OPERATORS = "+-=*/<>|,;:!?()&'\"@."

    /**
     * Splits [text] (the raw content of one equation, delimiters excluded) into tokens.
     * The scan is linear and always covers the whole input, so consecutive tokens are
     * contiguous and non-overlapping.
     */
    fun tokenize(text: CharSequence): List<Token> {
        val tokens = mutableListOf<Token>()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                // Comment: `%` runs to the end of the line (unless escaped).
                c == '%' -> {
                    val end = lineEndIndex(text, i)
                    tokens += Token(TokenKind.COMMENT, i, end)
                    i = end
                }

                // Control sequence: `\command`, `\\`, or a control symbol like `\{`.
                c == '\\' -> {
                    val end = controlSequenceEnd(text, i)
                    val word = text.substring(i + 1, end)
                    if ((word == "begin" || word == "end") && end > i + 1) {
                        // `\begin{name}` / `\end{name}` is emitted as one token (braces and
                        // name included) so the whole environment delimiter shares a color.
                        val envEnd = environmentEnd(text, end)
                        tokens += Token(TokenKind.ENVIRONMENT, i, envEnd)
                        i = envEnd
                    } else {
                        tokens += Token(TokenKind.COMMAND, i, end)
                        i = end
                    }
                }

                c == '{' || c == '}' -> {
                    tokens += Token(TokenKind.BRACE, i, i + 1)
                    i++
                }

                c == '[' || c == ']' -> {
                    tokens += Token(TokenKind.BRACKET, i, i + 1)
                    i++
                }

                c == '^' -> {
                    tokens += Token(TokenKind.SUPERSCRIPT, i, i + 1)
                    i++
                }

                c == '_' -> {
                    tokens += Token(TokenKind.SUBSCRIPT, i, i + 1)
                    i++
                }

                // Macro parameter marker `#1`, `#2`, … used inside `.texmacro` content.
                c == '#' -> {
                    var end = i + 1
                    while (end < text.length && text[end].isDigit()) end++
                    tokens += Token(TokenKind.PARAMETER, i, end)
                    i = end
                }

                c.isDigit() -> {
                    var end = i
                    while (end < text.length && (text[end].isDigit() || text[end] == '.')) end++
                    tokens += Token(TokenKind.NUMBER, i, end)
                    i = end
                }

                c in OPERATORS -> {
                    tokens += Token(TokenKind.OPERATOR, i, i + 1)
                    i++
                }

                else -> {
                    // Plain run of text (letters, spaces, unicode symbols, …).
                    var end = i
                    while (end < text.length && isPlainTextChar(text[end])) end++
                    // Always make progress, even for a character the class above missed.
                    if (end == i) end = i + 1
                    tokens += Token(TokenKind.TEXT, i, end)
                    i = end
                }
            }
        }
        return tokens
    }

    /** True when [c] does not start any of the special token kinds handled above. */
    private fun isPlainTextChar(c: Char): Boolean =
        c != '%' && c != '\\' && c != '{' && c != '}' && c != '[' && c != ']' &&
                c != '^' && c != '_' && c != '#' && !c.isDigit() && c !in OPERATORS

    /** End (exclusive) of the comment starting at [from]: the next `\n`, or the text end. */
    private fun lineEndIndex(text: CharSequence, from: Int): Int {
        var i = from
        while (i < text.length && text[i] != '\n') i++
        return i
    }

    /**
     * End (exclusive) of the control sequence starting at [start] (which is a `\`):
     * either a multi-letter control word (`\frac` → `\frac`) or a single-character
     * control symbol (`\{` → `\{`, `\\` → `\\`).
     */
    private fun controlSequenceEnd(text: CharSequence, start: Int): Int {
        var i = start + 1
        if (i >= text.length) return i // dangling backslash, reported by check()
        if (!text[i].isLetter()) return i + 1 // control symbol: one character
        while (i < text.length && text[i].isLetter()) i++
        return i
    }

    /** End (exclusive) of the environment `{name}` that follows `\begin` or `\end`. */
    private fun environmentEnd(text: CharSequence, afterCommand: Int): Int {
        var i = afterCommand
        while (i < text.length && text[i].isWhitespace()) i++
        if (i >= text.length || text[i] != '{') return i // malformed; brace check reports it

        val close = text.indexOf('}', i + 1)
        if (close < 0) return i + 1

        return close + 1
    }

    // ------------------------------------------------------------------
    // Structural check
    // ------------------------------------------------------------------

    /**
     * Reports the structural problems of [text] (one equation's content, delimiters
     * excluded). The check walks the input once with two stacks — grouping braces and
     * `\begin` / `\end` environments — and never inspects command *names*, so it stays
     * correct without a TeX command database.
     */
    fun check(text: CharSequence): List<Problem> {
        val problems = mutableListOf<Problem>()
        val braceStack = ArrayDeque<Int>()
        val environmentStack = ArrayDeque<Pair<String, Int>>() // name to `\begin` offset

        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '%' -> i = lineEndIndex(text, i)

                c == '\\' -> {
                    val end = controlSequenceEnd(text, i)
                    if (end == i + 1 && i + 1 >= text.length) {
                        // Trailing backslash with nothing after it.
                        problems += Problem(ProblemKind.DANGLING_COMMAND, i, end)
                        i++
                        continue
                    }
                    val name = text.substring(i + 1, end)
                    if (name == "begin" || name == "end") {
                        i = checkEnvironment(text, i, end, name, environmentStack, problems)
                        continue
                    }
                    i = end
                }

                c == '{' -> {
                    braceStack.addLast(i)
                    i++
                }

                c == '}' -> {
                    if (braceStack.isEmpty()) {
                        problems += Problem(ProblemKind.UNEXPECTED_BRACE, i, i + 1)
                    } else {
                        braceStack.removeLast()
                    }
                    i++
                }

                c == '#' -> {
                    val end = i + 1
                    var digits = end
                    while (digits < text.length && text[digits].isDigit()) digits++
                    if (digits == end) problems += Problem(ProblemKind.EMPTY_PARAMETER, i, end)
                    i = digits
                }

                else -> i++
            }
        }

        // Unclosed groups point at their opening brace.
        for (offset in braceStack) problems += Problem(ProblemKind.UNCLOSED_BRACE, offset, offset + 1)
        // Environments left open at the end of the equation.
        for ((name, offset) in environmentStack) {
            problems += Problem(ProblemKind.MISSING_END, offset, offset + 6 + name.length + 2, name)
        }
        return problems.sortedBy { it.start }
    }

    /**
     * Handles a `\begin` / `\end` control word: reads the `{name}` group that follows,
     * pushes/pops [environmentStack] and reports mismatches. Returns the scan offset to
     * continue from.
     */
    private fun checkEnvironment(
        text: CharSequence,
        start: Int,
        afterCommand: Int,
        command: String,
        environmentStack: ArrayDeque<Pair<String, Int>>,
        problems: MutableList<Problem>,
    ): Int {
        var i = afterCommand
        while (i < text.length && text[i].isWhitespace()) i++
        if (i >= text.length || text[i] != '{') return i // malformed; brace check reports it

        val close = text.indexOf('}', i + 1)
        if (close < 0) return i + 1

        val name = text.substring(i + 1, close).trim()
        val end = close + 1
        if (name.isEmpty()) {
            problems += Problem(ProblemKind.EMPTY_ENVIRONMENT, start, end)
            return end
        }

        if (command == "begin") {
            environmentStack.addLast(name to start)
        } else if (environmentStack.isEmpty() || environmentStack.last().first != name) {
            problems += Problem(ProblemKind.UNEXPECTED_END, start, end, name)
        } else {
            environmentStack.removeLast()
        }
        return end
    }
}


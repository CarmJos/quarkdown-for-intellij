package cc.carm.plugin.intellij.quarkdown.lang.latex

import cc.carm.plugin.intellij.quarkdown.lang.codeblock.QuarkdownCodeBlockSyntax
import cc.carm.plugin.intellij.quarkdown.lang.equation.QuarkdownEquationSyntax
import cc.carm.plugin.intellij.quarkdown.lang.function.QuarkdownCallParser

/**
 * Pure (no IntelliJ dependencies) locator for the TeX/LaTeX content of Quarkdown.
 *
 * Three sources of LaTeX are recognised:
 *
 *  1. **Equations** — the delimiter rules documented by Quarkdown (wiki: *TeX formulae*):
 *     ```
 *     INLINE      Let $ \overline v = \frac {\Delta x} {\Delta t} $ be the average velocity.
 *     BLOCK       $ F(u) = \int^{+\infty}_{-\infty} f(x) e^{-i 2\pi x} dx $
 *     MULTILINE   $$$
 *                 f(x) = \begin{cases} 0 & \text{if } x = 0 \\ 1 & \text{otherwise} \end{cases}
 *                 $$$
 *     ```
 *     The rule comes from Quarkdown's `ONELINE_MATH` pattern (`\$[ \t]` … `(?<![ \t])[ \t]\$`,
 *     wrapped in `(?<=^|\s|\W)` … `(?=$|\s|\W)`): the opening `$` must be preceded by the start
 *     of the text or a blank and followed by a blank, while the closing `$` must be preceded by
 *     a blank and followed by the end of the text, a blank or a non-word character. The blank
 *     after the opening `$` is what makes prose like `it costs $5 and $10` stay plain text, so
 *     it is implemented literally here instead of the laxer "any `$...$`" heuristic; the laxer
 *     *trailing* check is what makes `$ d_k $,` an equation, since punctuation may follow the
 *     closing `$`. The non-word character allowed *before* an opening `$` is not implemented:
 *     it is nearly always an inline code span delimiter (`` `$ x $` ``), which Quarkdown lexes
 *     as code because the span starts earlier. A `$$$` (three or more) run only counts as a
 *     delimiter when it sits alone on its line, optionally followed by the `{#id}` tag.
 *  2. **`.math`** — the function backing both syntaxes above. Its `content` argument (or its
 *     indented block body) is a TeX expression.
 *  3. **`.texmacro`** — its `name` argument is the declared command (e.g. `\gradient`) and its
 *     `macro` argument (or indented block body) is the TeX code the command expands to.
 *
 * Without 2 and 3 the Quarkdown lexer would split a command such as `\begin` into an `ESCAPE`
 * token (`\b`) plus plain text (`egin`), which both mis-colors it and makes the spell checker
 * flag `egin` as a misspelled word.
 *
 * Content inside fenced code blocks is never treated as LaTeX.
 *
 * Known limitation: a `$ ... $` pair written *inside an inline code span with spaces around
 * the dollars* (`` ` $ x $ ` ``) is still treated as an equation. The common documentation
 * form — ``two `$` symbols`` — is safe because a backtick is not whitespace.
 *
 * Kept dependency-free so the logic can be unit-tested and reused by the editor annotator and
 * the spell-checking strategy.
 */
object QuarkdownEquationRegions {

    /** Which construct produced a region. */
    enum class Kind {
        /** `$ ... $` sharing its line with other prose. */
        INLINE,

        /** `$ ... $` isolated on its own line. */
        BLOCK,

        /** `$$$` fenced block, possibly spanning several lines. */
        MULTILINE,

        /** The TeX expression argument (or block body) of a `.math` call. */
        MATH_CALL,

        /** A `.texmacro` name or macro body argument (or block body). */
        TEX_MACRO
    }

    /**
     * The LaTeX content of one region. [contentStart] points at the first character of the
     * content and [contentEnd] just past its last character, so
     * `substring(contentStart, contentEnd)` is exactly the text to analyse.
     *
     * [excluded] holds absolute ranges inside the content that must **not** be analysed as
     * LaTeX. `.math` content is evaluated as Quarkdown, so it may contain nested function
     * calls (e.g. `f(.n) = .n::multiply {2}`); those keep their own Quarkdown highlighting and
     * must not be re-colored or re-checked as TeX.
     */
    data class Region(
        val kind: Kind,
        val contentStart: Int,
        val contentEnd: Int,
        /**
         * Absolute range of the **whole occurrence** — delimiters and the optional `{#id}` tag
         * included — so an editor can replace the equation in one go. Defaults to the content
         * range for regions whose content is the whole construct.
         */
        val spanStart: Int = contentStart,
        val spanEnd: Int = contentEnd,
        val excluded: List<IntRange> = emptyList(),
    ) {
        /** The whole occurrence as a range. */
        val span: IntRange get() = spanStart until spanEnd

        val isEmpty: Boolean get() = contentEnd <= contentStart

        /**
         * The analyzable parts of the content: [contentStart]..[contentEnd] minus [excluded].
         * Each range is absolute and non-empty.
         */
        fun segments(): List<IntRange> {
            if (isEmpty) return emptyList()
            val cuts = excluded
                .filter { it.first < contentEnd && it.last >= contentStart }
                .map { it.first.coerceAtLeast(contentStart)..it.last.coerceAtMost(contentEnd - 1) }
                .sortedBy { it.first }
            if (cuts.isEmpty()) return listOf(contentStart..contentEnd - 1)

            val segments = mutableListOf<IntRange>()
            var cursor = contentStart
            for (cut in cuts) {
                if (cut.first > cursor) segments += cursor..cut.first - 1
                cursor = maxOf(cursor, cut.last + 1)
            }
            if (cursor <= contentEnd - 1) segments += cursor..contentEnd - 1
            return segments
        }
    }

    /** Result of a [find] pass. */
    data class Result(
        /** All equations found, in document order. */
        val regions: List<Region>,
        /** Opening delimiters that are never closed (the `$` run or the `$$$` fence line). */
        val unclosedDelimiters: List<IntRange>
    )

    /** Locates every LaTeX content region in [text]. */
    fun find(text: CharSequence): Result {
        val codeFences = QuarkdownCodeBlockSyntax.findFenceRanges(text)
        val regions = mutableListOf<Region>()
        val unclosed = mutableListOf<IntRange>()
        // Whole `$$$` blocks (delimiter lines included) are skipped by the inline pass below.
        val blockRanges = mutableListOf<IntRange>()

        findMultilineBlocks(text, codeFences, regions, blockRanges, unclosed)

        // `.math` / `.texmacro` content is not delimited by `$`, so it is located separately
        // and then excluded from the `$` scan (a `$` inside a math expression is plain TeX).
        val callRegions = findCallRegions(text, codeFences)

        val skip = codeFences + blockRanges + callRegions.map { it.contentStart until it.contentEnd }
        var i = 0
        while (i < text.length) {
            val skipped = skip.firstOrNull { i in it }
            if (skipped != null) {
                i = skipped.last + 1
                continue
            }
            if (text[i] != '$') {
                i++
                continue
            }

            val run = countDollarRun(text, i)
            val openEnd = i + run
            if (run > 2) {
                // A `$$$`-or-longer run that is not a fence line (handled above) is plain text.
                i = openEnd
                continue
            }
            if (!isOpeningDelimiter(text, i, run)) {
                i = openEnd
                continue
            }

            val closeStart = findClosingDelimiter(text, openEnd, run, skip)
            if (closeStart < 0) {
                unclosed += i until openEnd
                i = openEnd
                continue
            }
            if (text.substring(openEnd, closeStart).isNotBlank()) {
                val kind = if (isLineStartOnly(text, i) && isLineEndOnly(text, closeStart + run)) {
                    Kind.BLOCK
                } else {
                    Kind.INLINE
                }
                // The occurrence spans the `$` delimiters and a trailing `{#id}` tag as well.
                val closeEnd = closeStart + run
                regions += Region(kind, openEnd, closeStart, i, idTagEnd(text, closeEnd))
            }
            i = closeStart + run
        }

        regions += callRegions
        return Result(regions.sortedBy { it.contentStart }, unclosed.sortedBy { it.first })
    }

    /**
     * Every absolute range that must be treated as LaTeX by other language features — the
     * analyzable segments of all regions, with nested Quarkdown calls inside `.math` content
     * left out. Used by the spell checker to leave TeX alone.
     */
    fun latexRanges(text: CharSequence): List<IntRange> =
        find(text).regions.flatMap { it.segments() }

    // ------------------------------------------------------------------
    // `.math` / `.texmacro` content
    // ------------------------------------------------------------------

    /**
     * Locates the TeX content of `.math` and `.texmacro` calls, both for brace arguments
     * (`.math {E = mc^2}`, `.texmacro {\R} {\mathbb{R}}`) and for indented block bodies:
     *
     * ```
     * .math
     *     E = mc^2
     *
     * .texmacro {\gradient}
     *     \nabla
     * ```
     *
     * For `.texmacro` the macro *name* is TeX too (`\gradient` is a control sequence), so it is
     * returned as a region as well — otherwise the Quarkdown lexer would split it into `\g` plus
     * `radient`.
     */
    private fun findCallRegions(text: CharSequence, codeFences: List<IntRange>): List<Region> {
        val source = text.toString()
        val regions = mutableListOf<Region>()

        for (start in QuarkdownCallParser.findAllCallStarts(source)) {
            if (codeFences.any { start in it }) continue
            val call = QuarkdownCallParser.parseCall(source, start) ?: continue
            when (call.name) {
                "math" -> {
                    val content = call.args.firstOrNull { it.paramName == "content" }
                        ?: call.args.firstOrNull { !it.isNamed }
                    // The occurrence is the whole `.math` call, body argument included, without
                    // the whitespace that separates it from what follows on the line.
                    val spanStart = start
                    val spanEnd = trimTrailingWhitespace(source, callSpanEnd(source, call))

                    if (content != null) {
                        regions += Region(
                            Kind.MATH_CALL, content.rawStart, content.rawEnd,
                            spanStart, spanEnd,
                            nestedCallRanges(source, content.rawStart, content.rawEnd),
                        )
                    } else {
                        blockBodyRange(source, call)?.let {
                            regions += Region(Kind.MATH_CALL, it.first, it.last + 1, spanStart, spanEnd)
                        }
                    }
                }

                "texmacro" -> {
                    val nameArg = call.args.firstOrNull { it.paramName == "name" }
                        ?: call.args.firstOrNull { !it.isNamed }
                    val macroArg = call.args.firstOrNull { it.paramName == "macro" }
                        ?: call.args.firstOrNull { it !== nameArg && !it.isNamed }
                    // The declared command name is a control sequence such as `\gradient`.
                    if (nameArg != null) {
                        regions += Region(
                            Kind.TEX_MACRO, nameArg.rawStart, nameArg.rawEnd,
                            nameArg.braceStart, nameArg.braceEnd,
                        )
                    }
                    if (macroArg != null) {
                        regions += Region(
                            Kind.TEX_MACRO, macroArg.rawStart, macroArg.rawEnd,
                            macroArg.braceStart, macroArg.braceEnd,
                        )
                    } else {
                        blockBodyRange(source, call)?.let {
                            regions += Region(Kind.TEX_MACRO, it.first, it.last + 1, start, callSpanEnd(source, call))
                        }
                    }
                }
            }
        }
        return regions
    }

    /**
     * The absolute range of the indented block body following [call], or `null` when the call
     * has no body. The body starts at the first character of the line after the call and ends
     * at the last non-blank line indented by at least two spaces (or one tab) — the same rule
     * Quarkdown uses for body arguments. Trailing blank lines are not part of the body.
     */
    private fun blockBodyRange(text: String, call: QuarkdownCallParser.Call): IntRange? {
        if (!call.hasBodyArgument) return null
        // The body follows the call itself — i.e. right after the last argument's `}`, or right
        // after the name when there are no arguments. `call.end` cannot be used here: argument
        // parsing skips whitespace, so it has already moved past the newline that starts the body.
        val after = call.args.lastOrNull()?.braceEnd ?: call.nameEnd
        var i = after
        while (i < text.length && (text[i] == ' ' || text[i] == '\t')) i++
        if (i >= text.length || text[i] != '\n') return null

        val bodyStart = i + 1
        var cursor = bodyStart
        var bodyEnd = -1
        while (cursor <= text.length) {
            var lineEnd = cursor
            while (lineEnd < text.length && text[lineEnd] != '\n' && text[lineEnd] != '\r') lineEnd++
            val line = text.substring(cursor, lineEnd)
            if (line.isNotBlank()) {
                val indent = line.takeWhile { it == ' ' || it == '\t' }.length
                if (indent < 2) break // not indented → the body ended
                bodyEnd = lineEnd
            }
            if (lineEnd >= text.length) break
            cursor = lineEnd + 1
            if (cursor < text.length && text[cursor - 1] == '\r' && text[cursor] == '\n') cursor++
        }
        return if (bodyEnd > bodyStart) bodyStart until bodyEnd else null
    }

    /**
     * The absolute ranges of the Quarkdown function calls nested inside `[from, to)` of `text`
     * (including `::` chain continuations). `.math` content is evaluated as Quarkdown, so these
     * keep their own highlighting and must not be analysed as TeX.
     */
    private fun nestedCallRanges(text: String, from: Int, to: Int): List<IntRange> {
        val ranges = mutableListOf<IntRange>()
        var i = from
        while (i < to) {
            if (text[i] == '.' && QuarkdownCallParser.isFunctionStartDot(text, i)) {
                val end = callChainEnd(text, i, to)
                if (end > i) {
                    ranges += i until end
                    i = end
                    continue
                }
            }
            i++
        }
        return ranges
    }

    /**
     * End of the call (and of any `::name {…}` chain) starting at [start], never past [limit].
     * Returns [start] when the text at [start] is not a parsable call.
     */
    private fun callChainEnd(text: String, start: Int, limit: Int): Int {
        val call = QuarkdownCallParser.parseCall(text, start) ?: return start
        var end = call.end
        if (end > limit) return start
        // Absorb `::name {…}` chain segments, which findCallStarts deliberately skips.
        while (end + 1 < limit && text[end] == ':' && text[end + 1] == ':') {
            val chained = QuarkdownCallParser.parseCall(text, end) ?: break
            if (chained.end <= end || chained.end > limit) break
            end = chained.end
        }
        return end
    }

    // ------------------------------------------------------------------
    // `$$$` fenced blocks
    // ------------------------------------------------------------------

    /**
     * Fills [regions] / [blockRanges] / [unclosed] with the `$$$` fenced blocks of [text].
     * A fence line is paired with the next fence line — the same pairing Quarkdown uses for
     * multiline equations — so closing fences never open a block.
     */
    private fun findMultilineBlocks(
        text: CharSequence,
        codeFences: List<IntRange>,
        regions: MutableList<Region>,
        blockRanges: MutableList<IntRange>,
        unclosed: MutableList<IntRange>,
    ) {
        var openLineStart = -1
        var openLineEnd = -1

        for ((lineStart, lineEnd) in lineRanges(text)) {
            if (codeFences.any { lineStart in it }) continue
            val line = text.substring(lineStart, lineEnd)
            if (QuarkdownEquationSyntax.parseFenceEquationLine(line) == null) continue

            if (openLineStart < 0) {
                openLineStart = lineStart
                openLineEnd = lineEnd
            } else {
                // Content spans from the character after the opening line's newline up to
                // the first character of the closing fence line.
                val contentStart = openLineEnd + 1
                if (contentStart <= lineStart) {
                    // The occurrence spans the two `$$$` delimiter lines, including the `{#id}`
                    // tag that may follow the opening fence.
                    regions += Region(Kind.MULTILINE, contentStart, lineStart, openLineStart, lineEnd)
                }
                blockRanges += openLineStart until lineEnd

                openLineStart = -1
            }
        }
        if (openLineStart >= 0) unclosed += openLineStart until openLineEnd
    }

    // ------------------------------------------------------------------
    // Delimiter helpers
    // ------------------------------------------------------------------

    /**
     * True when the `$` run of [len] characters at [pos] can open an equation: preceded by the
     * start of the text or a blank, and followed by a blank (or the end of the text).
     *
     * The blank *after* the run is what keeps `$5` and `word$x$` plain text, exactly like the
     * `\$[ \t]` opening of Quarkdown's `ONELINE_MATH` pattern.
     */
    private fun isOpeningDelimiter(text: CharSequence, pos: Int, len: Int): Boolean {
        val before = pos == 0 || text[pos - 1].isWhitespace()
        val after = pos + len >= text.length || text[pos + len].isWhitespace()
        return before && after
    }

    /**
     * True when the `$` run of [len] characters at [pos] can close an equation: preceded by a
     * blank (the content must end with one) and followed by the end of the text, a blank or a
     * non-word character.
     *
     * The trailing check mirrors the `(?=$|\s|\W)` lookahead of Quarkdown's `ONELINE_MATH`
     * pattern: a formula is still a formula when punctuation follows its closing `$` —
     * `$ d_k $,` and `$ x $.` are equations, and requiring whitespace there used to make the
     * closing delimiter invisible. The scan then ran on to the next `$` and paired the opening
     * with a delimiter far below, swallowing the whole paragraph — and every `{#id}` tag and
     * heading in between — into a bogus "equation" whose `#` then looked like a broken macro
     * parameter.
     */
    private fun isClosingDelimiter(text: CharSequence, pos: Int, len: Int): Boolean {
        val before = pos > 0 && text[pos - 1].isWhitespace()
        val after = pos + len >= text.length || !isWordCharacter(text[pos + len])
        return before && after
    }

    /** Java's `\w`, which the lookarounds of Quarkdown's math patterns are written against. */
    private fun isWordCharacter(c: Char): Boolean =
        c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '_'

    /**
     * Finds the closing delimiter of the same length as the opening one, skipping code
     * blocks and `$$$` blocks. Returns the delimiter offset, or `-1` when unclosed.
     */
    private fun findClosingDelimiter(text: CharSequence, from: Int, len: Int, skip: List<IntRange>): Int {
        var j = from
        while (j < text.length) {
            val skipped = skip.firstOrNull { j in it }
            if (skipped != null) {
                j = skipped.last + 1
                continue
            }
            if (text[j] != '$') {
                j++
                continue
            }
            val run = countDollarRun(text, j)
            if (run == len && isClosingDelimiter(text, j, run)) return j
            j += run
        }
        return -1
    }

    /** Number of consecutive `$` characters starting at [pos]. */
    private fun countDollarRun(text: CharSequence, pos: Int): Int {
        var count = 0
        while (pos + count < text.length && text[pos + count] == '$') count++
        return count
    }

    /**
     * End of an optional `{#id}` tag that may follow [from] (whitespace in between allowed).
     * Returns [from] when there is no tag, or when the braces belong to a function call's
     * argument (`{#…}` only counts when its content starts with `#`).
     */
    private fun idTagEnd(text: CharSequence, from: Int): Int {
        var i = from
        while (i < text.length && (text[i] == ' ' || text[i] == '\t')) i++
        if (i >= text.length || text[i] != '{') return from
        val close = text.indexOf('}', i + 1)
        if (close < 0) return from
        if (!text.substring(i + 1, close).trim().startsWith("#")) return from
        return close + 1
    }

    /**
     * End of the whole call — including its indented body argument when it has one —, used as
     * the end of a `.math` / `.texmacro` occurrence.
     */
    private fun callSpanEnd(text: String, call: QuarkdownCallParser.Call): Int =
        blockBodyRange(text, call)?.last?.plus(1) ?: call.end

    /**
     * [end] moved back over trailing whitespace, so an occurrence never swallows the separator
     * (space or newline) that follows it — replacing the occurrence must keep the surrounding
     * document layout intact.
     */
    private fun trimTrailingWhitespace(text: String, end: Int): Int {
        var i = end.coerceAtMost(text.length)
        while (i > 0 && text[i - 1].isWhitespace()) i--
        return i
    }

    /** True when only whitespace precedes [pos] on its line. */
    private fun isLineStartOnly(text: CharSequence, pos: Int): Boolean {
        var i = pos - 1
        while (i >= 0 && text[i] != '\n') {
            if (!text[i].isWhitespace()) return false
            i--
        }
        return true
    }

    /** True when only whitespace follows [pos] on its line. */
    private fun isLineEndOnly(text: CharSequence, pos: Int): Boolean {
        var i = pos
        while (i < text.length && text[i] != '\n') {
            if (!text[i].isWhitespace()) return false
            i++
        }
        return true
    }

    /**
     * `(start, end)` pairs for every line in [text]. The start is the line's first character
     * and the end is exclusive, i.e. it points at the line break (or the text end).
     */
    private fun lineRanges(text: CharSequence): List<Pair<Int, Int>> {
        val ranges = mutableListOf<Pair<Int, Int>>()
        var start = 0
        while (start <= text.length) {
            var end = start
            while (end < text.length && text[end] != '\n') end++
            ranges += start to end
            if (end >= text.length) break
            start = end + 1
        }
        return ranges
    }
}

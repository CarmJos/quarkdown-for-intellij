package cc.carm.plugin.intellij.quarkdown.lang.latex

import cc.carm.plugin.intellij.quarkdown.lang.codeblock.QuarkdownCodeBlockSyntax
import cc.carm.plugin.intellij.quarkdown.lang.equation.QuarkdownEquationSyntax

/**
 * Pure (no IntelliJ dependencies) locator for the TeX/LaTeX content of Quarkdown equations.
 *
 * Implements the delimiter rules documented by Quarkdown (wiki: *TeX formulae*):
 *
 * ```
 * INLINE      Let $ \overline v = \frac {\Delta x} {\Delta t} $ be the average velocity.
 * BLOCK       $ F(u) = \int^{+\infty}_{-\infty} f(x) e^{-i 2\pi x} dx $
 * MULTILINE   $$$
 *             f(x) = \begin{cases} 0 & \text{if } x = 0 \\ 1 & \text{otherwise} \end{cases}
 *             $$$
 * ```
 *
 * The exact rule is that **both `$` delimiters must be preceded *and* followed by
 * whitespace**, or be at the beginning / end of a line. That rule is what makes prose like
 * `it costs $5 and $10` stay plain text, so it is implemented literally here instead of the
 * laxer "any `$...$`" heuristic. A `$$$` (three or more) run only counts as a delimiter when
 * it sits alone on its line, optionally followed by the `{#id}` cross-reference tag.
 *
 * Content inside fenced code blocks is never treated as an equation.
 *
 * Known limitation: a `$ ... $` pair written *inside an inline code span with spaces around
 * the dollars* (`` ` $ x $ ` ``) is still treated as an equation. The common documentation
 * form — ``two `$` symbols`` — is safe because a backtick is not whitespace.
 *
 * Kept dependency-free so the logic can be unit-tested and reused by the editor annotator.
 */
object QuarkdownEquationRegions {

    /** Which delimiter form produced a region. */
    enum class Kind {
        /** `$ ... $` sharing its line with other prose. */
        INLINE,

        /** `$ ... $` isolated on its own line. */
        BLOCK,

        /** `$$$` fenced block, possibly spanning several lines. */
        MULTILINE
    }

    /**
     * The LaTeX content of one equation. [contentStart] points at the first character after
     * the opening delimiter and [contentEnd] at the delimiter character itself, so
     * `substring(contentStart, contentEnd)` is exactly the content to analyse.
     */
    data class Region(val kind: Kind, val contentStart: Int, val contentEnd: Int) {
        val isEmpty: Boolean get() = contentEnd <= contentStart
    }

    /** Result of a [find] pass. */
    data class Result(
        /** All equations found, in document order. */
        val regions: List<Region>,
        /** Opening delimiters that are never closed (the `$` run or the `$$$` fence line). */
        val unclosedDelimiters: List<IntRange>
    )

    /** Locates every equation content region in [text]. */
    fun find(text: CharSequence): Result {
        val codeFences = QuarkdownCodeBlockSyntax.findFenceRanges(text)
        val regions = mutableListOf<Region>()
        val unclosed = mutableListOf<IntRange>()
        // Whole `$$$` blocks (delimiter lines included) are skipped by the inline pass below.
        val blockRanges = mutableListOf<IntRange>()

        findMultilineBlocks(text, codeFences, regions, blockRanges, unclosed)

        val skip = codeFences + blockRanges
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
            if (!isDelimiter(text, i, run)) {
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
                regions += Region(kind, openEnd, closeStart)
            }
            i = closeStart + run
        }

        return Result(regions.sortedBy { it.contentStart }, unclosed.sortedBy { it.first })
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
                    regions += Region(Kind.MULTILINE, contentStart, lineStart)
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
     * True when the `$` run of [len] characters at [pos] is a valid equation delimiter:
     * preceded and followed by whitespace, or by the beginning / end of the text. Line
     * breaks count as whitespace, which is what makes "beginning / end of the line" work.
     */
    private fun isDelimiter(text: CharSequence, pos: Int, len: Int): Boolean {
        val before = pos == 0 || text[pos - 1].isWhitespace()
        val after = pos + len >= text.length || text[pos + len].isWhitespace()
        return before && after
    }

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
            if (run == len && isDelimiter(text, j, run)) return j
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

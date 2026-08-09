package cc.carm.plugin.intellij.quarkdown.statusbar

import cc.carm.plugin.intellij.quarkdown.lang.function.QuarkdownCallParser

/**
 * Word / paragraph counting for a Quarkdown (.qd) document.
 *
 * Kept free of IntelliJ dependencies so it can be unit-tested in isolation.
 *
 * Counting rules:
 *  - **Words** are matched as runs of Unicode letters/digits (CJK text counts as a
 *    single run). Markdown structure (`#` headings, `-` lists, `>`, `|` table cells,
 *    `**bold**` etc.) is stripped before counting, and link text `[text](url)` is kept.
 *  - **Paragraphs** are blocks of non-empty text lines; blank lines, function calls,
 *    fenced code blocks, separators and HTML comments delimit them.
 *  - **Function calls** (`.var`, `.read`, `.center`, `.container`, …) and their
 *    arguments / indented bodies are excluded entirely — only actual prose is counted.
 *  - Fenced code block contents are excluded from the word count too.
 */
object QuarkdownStatsParser {

    /** Matches a word: a run of letters/digits, allowing inner apostrophes/hyphens. */
    private val WORD_PATTERN = Regex("[\\p{L}\\p{N}]+(?:['’\\-][\\p{L}\\p{N}]+)*")

    private val headingMarkerPattern = Regex("^#{1,6}\\s+")
    private val blockquotePattern = Regex("^>+\\s?")
    private val unorderedListPattern = Regex("^[-*+]\\s+")
    private val orderedListPattern = Regex("^\\d+[.)]\\s+")
    private val imagePattern = Regex("""!\s*(?:\([^)]*\)\s*)?\[[^]]*]\s*\([^)]*\)""")
    private val linkPattern = Regex("""\[([^]]*)]\s*\([^)]*\)""")
    private val inlineFormattingPattern = Regex("[*_~`]")
    private val autoLinkPattern = Regex("<[^>]+>")

    private val separatorPattern = Regex("^(?:-{3,}|\\*{3,}|_{3,})$")
    private val tableSeparatorPattern = Regex("^\\|?\\s*:?-{3,}:?\\s*(?:\\|\\s*:?-{3,}:?\\s*)*\\|?$")

    /** Counting result for one document. */
    data class QuarkdownStats(
        val wordCount: Int,
        val paragraphCount: Int
    )

    /** Computes the word & paragraph counts of a Quarkdown document. */
    fun computeStats(text: String): QuarkdownStats {
        val cleaned = maskFunctionCalls(text)
        return countLines(cleaned)
    }

    // ------------------------------------------------------------------
    // Function-call masking
    // ------------------------------------------------------------------

    /**
     * Replaces the character ranges of every function call (including chained `::` parts
     * and indented bodies) with spaces, so later line-based counting simply sees blanks.
     * Newlines are preserved so paragraph structure is unchanged.
     */
    private fun maskFunctionCalls(text: String): String {
        val sb = StringBuilder(text)
        for (range in computeCallRanges(text)) {
            for (i in range) {
                if (i < sb.length && sb[i] != '\n' && sb[i] != '\r') sb[i] = ' '
            }
        }
        return sb.toString()
    }

    /** Computes and merges the document ranges covered by function calls. */
    private fun computeCallRanges(text: String): List<IntRange> {
        val ranges = mutableListOf<IntRange>()
        for (start in QuarkdownCallParser.findAllCallStarts(text)) {
            var end = parseCallEnd(text, start) ?: continue
            // Extend through chained `::name {…}` segments that follow immediately.
            while (true) {
                val chainStart = findChainStart(text, end) ?: break
                val chainEnd = parseCallEnd(text, chainStart) ?: break
                end = chainEnd
            }
            ranges += start until end
        }
        return mergeRanges(ranges)
    }

    /** Parses a call starting at [start] and returns the absolute end offset. */
    private fun parseCallEnd(text: String, start: Int): Int? {
        val call = QuarkdownCallParser.parseCall(text, start) ?: return null
        var end = call.end
        if (call.hasBodyArgument) {
            end = findBodyEnd(text, end)
        }
        return end
    }

    /** Returns the offset of a `::name` chain segment right after [from], or `null`. */
    private fun findChainStart(text: String, from: Int): Int? {
        var i = from
        while (i < text.length && (text[i] == ' ' || text[i] == '\t' || text[i] == '\n' || text[i] == '\r')) i++
        if (i + 1 < text.length && text[i] == ':' && text[i + 1] == ':' &&
            text.getOrNull(i + 2)?.isLetter() == true
        ) {
            return i
        }
        return null
    }

    /**
     * Given the end offset of a parsed call, finds the end of its indented body block:
     * the body starts on the first line (indent ≥ 2) after the call and ends at a blank
     * line or the first non-indented line.
     */
    private fun findBodyEnd(text: String, callEnd: Int): Int {
        val n = text.length
        var i = callEnd
        // Skip trailing whitespace/newlines to the first body line.
        while (i < n && (text[i] == ' ' || text[i] == '\t' || text[i] == '\n' || text[i] == '\r')) i++
        if (i >= n) return callEnd

        var end = i
        while (true) {
            // Advance to the end of the current line.
            var lineEnd = end
            while (lineEnd < n && text[lineEnd] != '\n' && text[lineEnd] != '\r') lineEnd++
            end = lineEnd

            // Skip the newline characters.
            var next = lineEnd
            if (next < n && text[next] == '\r') next++
            if (next < n && text[next] == '\n') next++
            if (next >= n) break

            // Inspect the next line.
            var k = next
            var indent = 0
            while (k < n && (text[k] == ' ' || text[k] == '\t')) {
                indent++
                k++
            }
            if (k >= n) break
            val c = text[k]
            if (c == '\n' || c == '\r') break   // blank line ends the body
            if (indent < 2) break               // non-indented line ends the body
            end = next
        }
        return end
    }

    private fun mergeRanges(ranges: List<IntRange>): List<IntRange> {
        val sorted = ranges.sortedBy { it.first }
        val merged = mutableListOf<IntRange>()
        for (range in sorted) {
            val last = merged.lastOrNull()
            if (last != null && range.first <= last.last + 1) {
                merged[merged.size - 1] = last.first..maxOf(last.last, range.last)
            } else {
                merged += range
            }
        }
        return merged
    }

    // ------------------------------------------------------------------
    // Line-based counting
    // ------------------------------------------------------------------

    private fun countLines(text: String): QuarkdownStats {
        var wordCount = 0
        var paragraphCount = 0
        var inParagraph = false
        var inCodeBlock = false

        for (rawLine in text.split('\n')) {
            val line = rawLine.trim()

            // Fenced code blocks are excluded from the word count and act as a boundary.
            if (line.startsWith("```") || line.startsWith("~~~")) {
                inCodeBlock = !inCodeBlock
                if (inParagraph) {
                    paragraphCount++
                    inParagraph = false
                }
                continue
            }
            if (inCodeBlock) continue

            // Blank line / single-line HTML comment / separator: end the paragraph.
            if (line.isEmpty()) {
                if (inParagraph) {
                    paragraphCount++
                    inParagraph = false
                }
                continue
            }
            if (line.startsWith("<!--") && line.endsWith("-->")) {
                if (inParagraph) {
                    paragraphCount++
                    inParagraph = false
                }
                continue
            }
            if (separatorPattern.matches(line)) {
                if (inParagraph) {
                    paragraphCount++
                    inParagraph = false
                }
                continue
            }

            // Table separator rows (`| --- | --- |`) carry no words and do not split
            // the table paragraph.
            if (tableSeparatorPattern.matches(line)) continue

            val words = countWords(stripMarkdown(line))
            if (words > 0) inParagraph = true
            wordCount += words
        }
        if (inParagraph) paragraphCount++

        return QuarkdownStats(wordCount, paragraphCount)
    }

    private fun countWords(text: String): Int = WORD_PATTERN.findAll(text).count()

    /** Removes Markdown / Quarkdown syntax markers, keeping the visible text. */
    private fun stripMarkdown(line: String): String {
        var s = line.trim()
        // Structural markers at line start.
        s = headingMarkerPattern.replaceFirst(s, "")
        s = blockquotePattern.replaceFirst(s, "")
        s = unorderedListPattern.replaceFirst(s, "")
        s = orderedListPattern.replaceFirst(s, "")
        // Table cells: drop the pipes, keep the cell contents.
        s = s.trim('|').replace("|", " ")
        // Images contribute nothing; keep link text `[text](url)`.
        s = imagePattern.replace(s, " ")
        s = linkPattern.replace(s, "$1")
        // Inline formatting: **bold**, *italic*, __x__, _x_, ~~x~~, `code`.
        s = inlineFormattingPattern.replace(s, " ")
        // Auto-links <url>.
        s = autoLinkPattern.replace(s, " ")
        return s
    }
}

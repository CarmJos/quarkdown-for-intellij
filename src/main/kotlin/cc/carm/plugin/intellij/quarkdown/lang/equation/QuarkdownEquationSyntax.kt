package cc.carm.plugin.intellij.quarkdown.lang.equation

/**
 * Pure (no IntelliJ dependencies) parsing and building helpers for Quarkdown equations:
 *
 * ```
 * INLINE   $ E = mc^2 $ {#energy}
 * FENCED   $$$
 *              E = mc^2
 *          $$$
 * ```
 *
 * Equations are numbered and cross-referenceable only when they carry a `{#id}` (see the
 * Quarkdown wiki: TeX formulae / cross references / numbering). Unlike code blocks there is
 * no language, so the only editable attribute is the id.
 *
 * Kept dependency-free so the logic can be unit-tested and reused by the gutter marker
 * provider and its edit dialog.
 */
object QuarkdownEquationSyntax {

    /** Which declaration syntax an equation uses. */
    enum class Kind { INLINE, FENCED }

    /** Parsed metadata of a `$ ... $ {#id}` (inline or one-line block) equation. */
    data class InlineInfo(
        /** Leading whitespace of the line. */
        val indent: String,
        /** The opening delimiter (e.g. "$" or "$$"). */
        val open: String,
        /** The raw equation content, including surrounding whitespace. */
        val rawContent: String,
        /** The closing delimiter. */
        val close: String,
        /** Cross-reference id written as `{#id}`, or empty. */
        val id: String,
        /** The original full line text. */
        val line: String
    )

    /** Parsed metadata of a fenced equation `$$$ {#id}` (opening or closing) line. */
    data class FenceInfo(
        /** Leading whitespace of the line. */
        val indent: String,
        /** The fence delimiter (always 3+ dollar signs). */
        val fence: String,
        /** Cross-reference id written as `{#id}`, or empty. */
        val id: String,
        /** The original full line text. */
        val line: String
    )

    /**
     * Matches a standalone `$ ... $ {#id}` / `$$ ... $$ {#id}` line. Both delimiters must
     * appear on the same line, the content must be non-blank, and the delimiter runs must
     * match in length. Groups: 1 indent, 2 open, 3 raw content, 4 close, 5 id.
     */
    private val inlineRegex = Regex(
        """^(\s*)(\$+)(.*?)(\$+)\s*(?:\{#([^}]+)})?\s*$"""
    )

    /** Matches a fenced equation line `$$$ {#id}` (opening or closing). Groups: 1 indent, 2 fence, 3 id. */
    private val fenceRegex = Regex(
        """^(\s*)(\$+)\s*(?:\{#([^}]+)})?\s*$"""
    )

    /**
     * Matches a standalone inline/one-line block equation line. Returns `null` for prose
     * lines containing `$...$` inline, for `$$$` fenced lines and for mismatched delimiters.
     */
    fun parseInlineEquationLine(line: String): InlineInfo? {
        val m = inlineRegex.matchEntire(line) ?: return null
        val open = m.groupValues[2]
        val close = m.groupValues[4]
        // `$$$` fences and mismatched delimiters are not inline equations.
        if (open.length > 2 || close.length > 2 || open.length != close.length) return null
        if (m.groupValues[3].isBlank()) return null
        return InlineInfo(m.groupValues[1], open, m.groupValues[3], close, m.groupValues[5].trim(), line)
    }

    /**
     * Matches a fenced equation line (`$$$` or more, optionally followed by `{#id}`) — both
     * the opening and the closing delimiter line. Returns `null` for other lines.
     */
    fun parseFenceEquationLine(line: String): FenceInfo? {
        val m = fenceRegex.matchEntire(line) ?: return null
        val fence = m.groupValues[2]
        if (fence.length < 3) return null
        return FenceInfo(m.groupValues[1], fence, m.groupValues[3].trim(), line)
    }

    /**
     * Rebuilds an inline/one-line block equation line, preserving the original indentation,
     * delimiters and raw content. An empty [id] removes the `{#id}` tag.
     */
    fun buildInlineLine(originalLine: String, id: String): String {
        val info = parseInlineEquationLine(originalLine) ?: return originalLine
        val sb = StringBuilder(info.indent).append(info.open).append(info.rawContent).append(info.close)
        if (id.isNotEmpty()) sb.append(" {#").append(id).append("}")
        return sb.toString()
    }

    /**
     * Rebuilds a fenced equation opening line, preserving the original indentation and fence
     * delimiter. An empty [id] removes the `{#id}` tag.
     */
    fun buildFenceLine(originalLine: String, id: String): String {
        val info = parseFenceEquationLine(originalLine) ?: return originalLine
        val sb = StringBuilder(info.indent).append(info.fence)
        if (id.isNotEmpty()) sb.append(" {#").append(id).append("}")
        return sb.toString()
    }

    /**
     * Builds a fresh inline equation line for insertion: `$ content $ {#id}`.
     * An empty [content] yields `$ $` (the caret can be placed between the delimiters).
     */
    fun buildInlineInsert(content: String, id: String): String {
        val sb = StringBuilder("$")
        if (content.isNotBlank()) sb.append(' ').append(content.trim())
        sb.append(" $")
        if (id.isNotBlank()) sb.append(" {#").append(id.trim()).append("}")
        return sb.toString()
    }

    /**
     * Builds a fresh fenced equation block for insertion:
     * ```
     * $$$ {#id}
     * content
     * $$$
     * ```
     */
    fun buildFencedInsert(content: String, id: String): String {
        val sb = StringBuilder("$$$")
        if (id.isNotBlank()) sb.append(" {#").append(id.trim()).append("}")
        sb.append('\n')
        if (content.isNotBlank()) sb.append(content.trim())
        sb.append('\n').append("$$$")
        return sb.toString()
    }

    /**
     * Returns the absolute line-start offsets of every *opening* fenced equation delimiter
     * in [text]. A single linear scan pairs each opening `$$$` with the next `$$$` line, so
     * closing fences never produce an entry (mirrors the code-block fence pairing).
     */
    fun findEquationFenceOpenOffsets(text: CharSequence): Set<Int> {
        val result = mutableSetOf<Int>()
        var i = 0
        var fenceStart = -1
        while (i < text.length) {
            val atLineStart = i == 0 || text[i - 1] == '\n'
            if (atLineStart) {
                var spaces = 0
                while (i + spaces < text.length && text[i + spaces] == ' ') spaces++
                val contentPos = i + spaces
                if (contentPos < text.length && text[contentPos] == '$') {
                    if (countDollars(text, contentPos) >= 3) {
                        if (fenceStart < 0) {
                            fenceStart = i
                        } else {
                            result.add(fenceStart)
                            fenceStart = -1
                        }
                    }
                }
            }
            i++
        }
        return result
    }

    private fun countDollars(text: CharSequence, pos: Int): Int {
        var count = 0
        while (pos + count < text.length && text[pos + count] == '$') count++
        return count
    }
}

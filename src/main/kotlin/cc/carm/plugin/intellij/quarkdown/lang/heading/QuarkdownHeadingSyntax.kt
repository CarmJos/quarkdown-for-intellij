package cc.carm.plugin.intellij.quarkdown.lang.heading

/**
 * Pure (no IntelliJ dependencies) parsing and building helpers for Quarkdown headings:
 *
 * ```
 * # Title              (level 1)
 * ## Section {#id}     (level 2, with cross-reference id)
 * ### Sub {#sub}       (level 3)
 * ##! Appendix         (level 2, decorative: unnumbered, out of the table of contents)
 * ```
 *
 * Headings can carry an explicit cross-reference id written as `{#id}`; without one,
 * Quarkdown derives an implicit slug from the text (see the Quarkdown wiki:
 * cross-references). A decorative heading (the optional `!` of Quarkdown's heading
 * pattern) is still a heading for the outline, the gutter marker and `.ref` resolution;
 * only its numbering and its table-of-contents entry are suppressed. Unlike code blocks
 * there is no language, so the editable attributes are the level, the text content and
 * the id.
 *
 * Kept dependency-free so the logic can be unit-tested and reused by the gutter marker
 * provider and its edit dialog.
 */
object QuarkdownHeadingSyntax {

    /** Parsed metadata of a heading line. */
    data class HeadingInfo(
        /** Leading whitespace of the line. */
        val indent: String,
        /** The heading marker, e.g. `##` (without the decorative `!`). */
        val marker: String,
        /** The heading level: 1 for `#`, up to 6 for `######`. */
        val level: Int,
        /** The heading text content (without the marker, trailing `#`s or `{#id}`). */
        val content: String,
        /** Cross-reference id written as `{#id}`, or empty. */
        val id: String,
        /** True for a decorative heading (`##! Title`): unnumbered and out of the contents. */
        val decorative: Boolean,
        /** The original full line text. */
        val line: String
    )

    /**
     * Matches a heading line `#...######! Content {#id}`. Groups: 1 indent, 2 marker,
     * 3 the decorative `!`, 4 content, 5 id. Content is captured lazily so a trailing
     * `{#id}` is never part of it.
     *
     * The optional `!` mirrors Quarkdown's `^ {0,3}(#{1,6})(!?)(?=\s|$)` heading pattern.
     * The trailing `\r?` makes the rule work on CRLF documents: callers hand over the line
     * *including* its carriage return, and `.` never matches one, so without it no heading
     * of a Windows-authored document would ever be recognized.
     */
    private val headingLineRegex = Regex(
        """^(\s*)(#{1,6})(!?)[ \t]+(.+?)(?:[ \t]+\{#([^}]+)})?[ \t]*\r?$"""
    )

    /** Trailing ATX closing run (`## Title ##`), stripped from the content. */
    private val trailingAtxRegex = Regex("""[ \t]+#+[ \t]*$""")

    /** Parses a heading line; returns `null` for non-heading lines. */
    fun parseHeadingLine(line: String): HeadingInfo? {
        val m = headingLineRegex.matchEntire(line) ?: return null
        val indent = m.groupValues[1]
        val marker = m.groupValues[2]
        val decorative = m.groupValues[3].isNotEmpty()
        val id = m.groupValues[5].trim()
        val content = m.groupValues[4]
            .replace(trailingAtxRegex, "")
            .trim()
        return HeadingInfo(indent, marker, marker.length, content, id, decorative, line)
    }

    /**
     * Rebuilds a heading line, preserving the original indentation and decorative `!`.
     */
    fun buildHeadingLine(originalLine: String, level: Int, content: String, id: String): String {
        val info = parseHeadingLine(originalLine) ?: return originalLine
        return buildHeadingInsert(level, content, id, info.indent, info.decorative)
    }

    /**
     * Builds a heading line: `## content {#id}`, or `##! content {#id}` for a decorative
     * heading. [indent] defaults to empty (new line); a caller may pass an existing line's
     * indent.
     */
    fun buildHeadingInsert(
        level: Int,
        content: String,
        id: String,
        indent: String = "",
        decorative: Boolean = false,
    ): String {
        val sb = StringBuilder(indent).append("#".repeat(level.coerceIn(1, 6)))
        if (decorative) sb.append('!')
        val trimmedContent = content.trim()
        if (trimmedContent.isNotEmpty()) sb.append(' ').append(trimmedContent)
        val trimmedId = id.trim()
        if (trimmedId.isNotEmpty()) sb.append(" {#").append(trimmedId).append("}")
        return sb.toString()
    }

    /**
     * Extracts a default id from heading [content]: lower-cased, every run of
     * non-letter/non-digit characters (spaces, punctuation) becomes a single `-`, and
     * leading/trailing `-` are removed. Unicode letters (e.g. CJK) are preserved so
     * Chinese headings still produce a usable id.
     */
    fun extractIdFromContent(content: String): String {
        return content.trim()
            .lowercase()
            .replace(Regex("""[^\p{L}\p{N}]+"""), "-")
            .trim('-')
    }
}


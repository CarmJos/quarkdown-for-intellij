package cc.carm.plugin.intellij.quarkdown.lang.reference

import cc.carm.plugin.intellij.quarkdown.lang.codeblock.QuarkdownCodeBlockSyntax
import cc.carm.plugin.intellij.quarkdown.lang.equation.QuarkdownEquationSyntax

/**
 * Dependency-free resolver that maps a `.ref {id}` cross-reference to a human-readable
 * description of its target element — its **type** (Section / Figure / Table / Code /
 * Equation) and its **caption** (heading text, image title, table caption, code caption).
 *
 * It is used by the folding builder to preview a `.ref {id}` reference as a CustomFold
 * placeholder (e.g. `Table Beverage preferences`), mirroring the actual rendered reference
 * as closely as possible.
 *
 * Quarkdown's *exact* rendered reference (e.g. `Table 1.1: Beverage preferences`)
 * additionally depends on the numbering configuration (`.numbering`) and the document
 * language (`.doclang`). Those are deliberately **not** replicated here — this resolver
 * returns only the stable type + caption parts, and callers fall back to `Reference(id)`
 * when no caption-bearing target can be identified.
 */
object QuarkdownReferenceLabelResolver {

    /** The kind of target a `.ref {id}` can resolve to, with its default English label. */
    enum class Kind(val label: String) {
        SECTION("Section"),
        FIGURE("Figure"),
        TABLE("Table"),
        CODE("Code"),
        EQUATION("Equation")
    }

    /** A resolved reference target: the element kind plus its caption (may be empty). */
    data class Target(val kind: Kind, val caption: String)

    /** Matches `{#id}` label declarations (case-insensitive, hyphenated ids allowed). */
    private val labelPattern = Regex("""\{\s*#\s*([a-zA-Z0-9_-]+)\s*}""")

    /** Matches a heading line `# Title` … `###### Title` (decorative `#!` excluded). */
    private val headingPattern = Regex("""^\s*(#{1,6})\s+(.+)$""")

    /** Strips a trailing `{#id}` label from a heading/image line. */
    private val trailingLabelPattern = Regex("""\{\s*#\s*[a-zA-Z0-9_-]+\s*}\s*$""")

    /**
     * Matches a table caption line: an optional quoted caption immediately followed by a
     * `{#id}` label and nothing else (`"Caption" {#id}`, `{#id}`, `(Caption) {#id}`).
     */
    private val tableLabelPattern = Regex(
        """^\s*(?:"([^"]*)"|'([^']*)'|\(([^)]*)\))?\s*\{\s*#\s*[a-zA-Z0-9_-]+\s*}\s*$"""
    )

    /**
     * Matches an image/figure `![alt](path "title")` — with an optional size `(100%)`,
     * optional quoted/parenthesised title, and the `{#id}` possibly following. Groups:
     * 1 alt, 2 path, 3/4/5 title (double-quoted / single-quoted / parenthesised).
     */
    private val imagePattern = Regex(
        """!\s*(?:\([^)]*\)\s*)?\[([^\]]*)\]\s*\(\s*([^)\s]+)(?:\s+(?:"([^"]*)"|'([^']*)'|\(([^)]*)\)))?\s*\)"""
    )

    /**
     * Resolves [id] to a description of its target element, or `null` when no caption-bearing
     * target can be identified (callers then fall back to `Reference(id)`).
     */
    fun resolve(text: String, id: String): Target? {
        val normalized = id.trim()
        if (normalized.isEmpty()) return null

        // 1) Explicit `{#id}` label declaration.
        findLabel(text, normalized)?.let { match ->
            classifyLabelLine(lineOf(text, match), normalized)?.let { return it }
        }

        // 2) `.code … ref:{id}` function declaration.
        resolveCodeFunction(text, normalized)?.let { return it }

        // 3) Heading whose slug matches the id (no explicit label).
        return resolveHeadingSlug(text, normalized)
    }

    // ------------------------------------------------------------------
    // `{#id}` label declaration
    // ------------------------------------------------------------------

    /** Finds the first `{#id}` declaration whose id equals [id] (case-insensitive). */
    private fun findLabel(text: String, id: String): MatchResult? {
        return labelPattern.findAll(text).firstOrNull { it.groupValues[1].equals(id, ignoreCase = true) }
    }

    /** Returns the full line (without the trailing newline) containing [match]. */
    private fun lineOf(text: String, match: MatchResult): String {
        val start = text.lastIndexOf('\n', match.range.first - 1) + 1
        var end = text.indexOf('\n', match.range.last + 1)
        if (end < 0) end = text.length
        return text.substring(start, end)
    }

    /** Classifies the line holding a `{#id}` label declaration into a target. */
    private fun classifyLabelLine(line: String, id: String): Target? {
        val trimmed = line.trim()

        // Fenced code block: ```lang "caption" {#id} / ~~~lang 'caption' {#id}.
        QuarkdownCodeBlockSyntax.parseFenceLine(line)?.let { fence ->
            if (fence.id.equals(id, ignoreCase = true)) return Target(Kind.CODE, fence.caption)
        }

        // Fenced equation: $$$ {#id}.
        QuarkdownEquationSyntax.parseFenceEquationLine(line)?.let { eq ->
            if (eq.id.equals(id, ignoreCase = true)) return Target(Kind.EQUATION, "")
        }

        // Inline / one-line equation: $ ... $ {#id} / $$ ... $$ {#id}.
        QuarkdownEquationSyntax.parseInlineEquationLine(line)?.let { eq ->
            if (eq.id.equals(id, ignoreCase = true)) return Target(Kind.EQUATION, "")
        }

        // Heading: # … {#id}.
        headingPattern.matchEntire(line)?.let { m ->
            val heading = m.groupValues[2]
                .replace(trailingLabelPattern, "")
                .replace(Regex("""\s*#+\s*$"""), "")
                .trim()
            if (heading.isNotEmpty()) return Target(Kind.SECTION, heading)
        }

        // Image / figure: ![alt](path "title") {#id}.
        if (trimmed.startsWith("!")) {
            imagePattern.find(line)?.let { m ->
                val title = m.groupValues[3].ifEmpty { m.groupValues[4] }.ifEmpty { m.groupValues[5] }.trim()
                val caption = title.ifEmpty { m.groupValues[1].trim() }
                return Target(Kind.FIGURE, caption)
            }
        }

        // Table caption: "Caption" {#id} / {#id} on its own line.
        tableLabelPattern.matchEntire(line)?.let { m ->
            val caption = m.groupValues[1].ifEmpty { m.groupValues[2] }.ifEmpty { m.groupValues[3] }.trim()
            return Target(Kind.TABLE, caption)
        }

        return null
    }

    // ------------------------------------------------------------------
    // `.code` function declaration
    // ------------------------------------------------------------------

    /** Finds a `.code … ref:{id}` declaration and returns its caption. */
    private fun resolveCodeFunction(text: String, id: String): Target? {
        for (line in text.lines()) {
            val info = QuarkdownCodeBlockSyntax.parseCodeFunctionLine(line) ?: continue
            if (info.id.equals(id, ignoreCase = true)) return Target(Kind.CODE, info.caption)
        }
        return null
    }

    // ------------------------------------------------------------------
    // Heading slug fallback
    // ------------------------------------------------------------------

    /** Finds a heading whose slug matches [id] (used when no explicit `{#id}` exists). */
    private fun resolveHeadingSlug(text: String, id: String): Target? {
        val slug = slugify(id)
        if (slug.isEmpty()) return null
        for (line in text.lines()) {
            val m = headingPattern.matchEntire(line) ?: continue
            val heading = m.groupValues[2]
                .replace(trailingLabelPattern, "")
                .replace(Regex("""\s*#+\s*$"""), "")
                .trim()
            if (heading.isEmpty()) continue
            if (slugify(heading) == slug) return Target(Kind.SECTION, heading)
        }
        return null
    }

    /** Slugifies [value] the way Quarkdown derives heading ids. */
    private fun slugify(value: String): String =
        value.lowercase().replace(Regex("""[^a-z0-9]+"""), "-").trim('-')
}

package cc.carm.plugin.intellij.quarkdown.lang.equation

import cc.carm.plugin.intellij.quarkdown.lang.function.QuarkdownCallParser
import cc.carm.plugin.intellij.quarkdown.lang.latex.QuarkdownEquationRegions

/**
 * Pure (no IntelliJ dependencies) model for editing a *whole* equation occurrence — the
 * delimiters, the TeX content and the optional cross-reference id — and for converting between
 * the syntax Quarkdown accepts:
 *
 * ```
 * $ x = 1 $ {#energy}          inline / one-line block
 * $$$ {#energy}                multi-line block
 *     x = 1
 * $$$
 * .math {x = 1} ref:{energy}   function call (content is evaluated as Quarkdown)
 * ```
 *
 * The editor works on [QuarkdownEquationRegions.Region.span], so replacing an equation never
 * disturbs the prose around it. Whether the dollar form uses `$ … $` or `$$$ … $$$` follows
 * the construct the document already used — or, when converting, whether the content spans
 * several lines.
 *
 * Kept dependency-free so the logic can be unit-tested and reused by the editor dialog.
 */
object QuarkdownEquationEdit {

    /** Syntax family the editor can convert between. */
    enum class Form { DOLLAR, MATH }

    /** The editable state of one equation occurrence. */
    data class Occurrence(
        /** The syntax family currently used. */
        val form: Form,
        /** The TeX content, without delimiters and without surrounding whitespace. */
        val content: String,
        /** The cross-reference id, or empty. */
        val id: String,
        /** True when the dollar form uses the `$$$` fence (multi-line block). */
        val fence: Boolean,
        /** Leading whitespace of a standalone occurrence, kept when rewriting it. */
        val indent: String,
        /** True when the occurrence starts its own line. */
        val standalone: Boolean,
        /** Whether the id can be edited at all (all forms here support it). */
        val idEditable: Boolean = true,
    )

    /**
     * Reads the editable state of [region] out of [text]. Returns `null` for regions that are
     * not equations (`.texmacro` name/body), which have no content/id model of their own.
     */
    fun occurrence(text: CharSequence, region: QuarkdownEquationRegions.Region): Occurrence? {
        val source = text.toString()
        return when (region.kind) {
            QuarkdownEquationRegions.Kind.TEX_MACRO -> null

            QuarkdownEquationRegions.Kind.MULTILINE -> {
                val openingLine = source.substring(region.spanStart, lineEndOf(source, region.spanStart))
                Occurrence(
                    form = Form.DOLLAR,
                    content = source.substring(region.contentStart, region.contentEnd).trim(),
                    id = QuarkdownEquationSyntax.parseFenceEquationLine(openingLine)?.id.orEmpty(),
                    fence = true,
                    // The span starts at the line start, so the indentation lives inside it.
                    indent = openingLine.takeWhile { it == ' ' || it == '\t' },
                    standalone = true,
                )
            }

            QuarkdownEquationRegions.Kind.INLINE,
            QuarkdownEquationRegions.Kind.BLOCK -> {
                val lineStart = lineStartOf(source, region.spanStart)
                Occurrence(
                    form = Form.DOLLAR,
                    content = source.substring(region.contentStart, region.contentEnd).trim(),
                    id = trailingIdTag(source, region),
                    fence = false,
                    indent = source.substring(lineStart, region.spanStart),
                    standalone = lineStart == region.spanStart,
                )
            }

            QuarkdownEquationRegions.Kind.MATH_CALL -> {
                val call = QuarkdownCallParser.parseCall(source, region.spanStart)
                val lineStart = lineStartOf(source, region.spanStart)
                // The raw body keeps its indentation, so it is dedented *before* trimming:
                // that removes the body indentation from every line, not just from the first.
                val raw = source.substring(region.contentStart, region.contentEnd)
                Occurrence(
                    form = Form.MATH,
                    content = dedent(raw).trim(),
                    id = call?.args?.firstOrNull { it.paramName == "ref" }?.raw?.trim().orEmpty(),
                    fence = false,
                    indent = source.substring(lineStart, region.spanStart),
                    standalone = lineStart == region.spanStart,
                )
            }
        }
    }

    /**
     * Renders the occurrence text to write back into the document.
     *
     *  - [Form.DOLLAR] with [fence] produces a `$$$` block, otherwise a `$ … $` equation.
     *  - [Form.MATH] produces a `.math` call; a multi-line content becomes an indented body,
     *    formatted with the `ref:` on the header line and the expression below it:
     *
     *    ```
     *    .math ref:{energy}
     *          \begin{aligned} … \end{aligned}
     *    ```
     *
     *  - [indent] is applied only for a standalone occurrence, so an inline equation stays
     *    inline and never gains stray whitespace.
     *
     * Note the `ref:` has **no space** before its brace. Quarkdown accepts `ref:{id}` but silently
     * produces an *empty* formula for `ref: {id}`, which was verified against the CLI.
     */
    fun render(
        form: Form,
        content: String,
        id: String,
        fence: Boolean,
        indent: String,
        standalone: Boolean,
    ): String {
        val prefix = if (standalone) indent else ""
        val trimmedId = id.trim()
        return when (form) {
            Form.DOLLAR -> {
                if (fence) {
                    buildString {
                        append(prefix).append("$$$")
                        if (trimmedId.isNotEmpty()) append(" {#").append(trimmedId).append("}")
                        append('\n').append(content.trim()).append('\n').append("$$$")
                    }
                } else {
                    buildString {
                        append(prefix).append("$ ")
                        if (content.isNotBlank()) append(content.trim()).append(' ')
                        append("$")
                        if (trimmedId.isNotEmpty()) append(" {#").append(trimmedId).append("}")
                    }
                }
            }

            Form.MATH -> {
                val body = content.trim()
                if (body.isEmpty()) {
                    buildString {
                        append(prefix).append(".math {}")
                        if (trimmedId.isNotEmpty()) append(" ref:{").append(trimmedId).append("}")
                    }
                } else if (!body.contains('\n')) {
                    // Named arguments must come after positional ones, so the content braces
                    // stay first and `ref:` follows.
                    buildString {
                        append(prefix).append(".math {").append(body).append("}")
                        if (trimmedId.isNotEmpty()) append(" ref:{").append(trimmedId).append("}")
                    }
                } else {
                    // A multi-line expression becomes an indented body argument; a `ref:` can
                    // precede it on the header line.
                    buildString {
                        append(prefix).append(".math")
                        if (trimmedId.isNotEmpty()) append(" ref:{").append(trimmedId).append("}")
                        append('\n').append(indentBlock(body))
                    }
                }
            }
        }
    }

    /**
     * Whether switching to [target] should keep using the `$$$` fence. Fenced blocks stay
     * fenced (their content is usually multi-line); a `.math` block body becomes a fence as
     * well, because that is the only dollar syntax able to hold several lines.
     */
    fun fenceAfterConversion(
        current: Occurrence,
        target: Form,
        content: String,
    ): Boolean = when (target) {
        Form.MATH -> false
        Form.DOLLAR -> when {
            content.contains('\n') -> true
            else -> current.fence
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** The `{#id}` written right after a `$ … $` equation, or empty when there is none. */
    private fun trailingIdTag(text: String, region: QuarkdownEquationRegions.Region): String {
        // The id tag sits inside the occurrence span but after the closing delimiter.
        val afterContent = text.substring(region.contentEnd, region.spanEnd)
        val open = afterContent.indexOf('{')
        if (open < 0) return ""
        val close = afterContent.indexOf('}', open + 1)
        if (close < 0) return ""
        return afterContent.substring(open + 1, close).removePrefix("#").trim()
    }

    private fun lineStartOf(text: String, offset: Int): Int {
        var i = offset.coerceIn(0, text.length)
        while (i > 0 && text[i - 1] != '\n') i--
        return i
    }

    private fun lineEndOf(text: String, offset: Int): Int {
        var i = offset.coerceIn(0, text.length)
        while (i < text.length && text[i] != '\n' && text[i] != '\r') i++
        return i
    }

    /** Removes the common leading indentation of the non-blank lines of [content]. */
    private fun dedent(content: String): String {
        val lines = content.lines()
        val common = lines.filter { it.isNotBlank() }
            .minOfOrNull { line -> line.takeWhile { it == ' ' || it == '\t' }.length }
            ?: 0
        if (common == 0) return content
        return lines.joinToString("\n") { line -> line.drop(common.coerceAtMost(line.length)) }
    }

    /** Indents every non-blank line so the result is a valid body argument.
     *
     * Quarkdown requires at least two spaces of indentation; [BODY_INDENT] uses six so the body
     * lines up under the call's arguments (`.math ` is six characters wide), which keeps long
     * formulas visually grouped with their `.math` header.
     */
    private fun indentBlock(content: String): String =
        content.lines().joinToString("\n") { if (it.isBlank()) it else BODY_INDENT + it }

    /** Indentation of a multi-line `.math` body (see [indentBlock]). */
    private const val BODY_INDENT = "      "
}

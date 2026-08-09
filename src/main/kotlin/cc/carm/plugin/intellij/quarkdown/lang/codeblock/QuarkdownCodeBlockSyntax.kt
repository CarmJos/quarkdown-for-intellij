package cc.carm.plugin.intellij.quarkdown.lang.codeblock

import cc.carm.plugin.intellij.quarkdown.lang.function.QuarkdownCallParser

/**
 * Pure (no IntelliJ dependencies) parsing and building helpers for Quarkdown code block
 * declarations:
 *
 * ```
 * FENCED        ```python "Fibonacci function" {#fibonacci}
 * CODE_FUNCTION .code lang:{python} caption:{Fibonacci function} ref:{example}
 * ```
 *
 * Kept dependency-free so the logic can be unit-tested and reused by the gutter marker
 * provider and its edit dialog.
 */
object QuarkdownCodeBlockSyntax {

    /** Which declaration syntax a code block uses. */
    enum class Kind { FENCED, CODE_FUNCTION }

    /** Parsed metadata of a fenced code block opening line. */
    data class FenceInfo(
        /** Leading whitespace of the line. */
        val indent: String,
        /** The fence delimiter, e.g. "```" or "~~~". */
        val fence: String,
        /** Language identifier after the fence (e.g. "python"), or empty. */
        val language: String,
        /** Caption written in double quotes, single quotes or parentheses, or empty. */
        val caption: String,
        /** Cross-reference id written as `{#id}`, or empty. */
        val id: String,
        /** The original full line text. */
        val line: String
    )

    /** Parsed metadata of a `.code` function block header line. */
    data class CodeFunctionInfo(
        /** Leading whitespace of the line. */
        val indent: String,
        /** Value of the `lang:{...}` argument, or empty. */
        val language: String,
        /** Value of the `caption:{...}` argument, or empty. */
        val caption: String,
        /** Value of the `ref:{...}` argument (the cross-reference id), or empty. */
        val id: String,
        /** The original header text without the leading indentation. */
        val headerText: String,
        /** The original full line text. */
        val line: String
    )

    /**
     * Matches a fenced code block opening/closing line:
     * ` ```lang "caption" {#id}` or `~~~lang (caption) {#id}`.
     *
     * Groups: 1 indent, 2 fence, 3 language, 4/5/6 caption ("…", '…', (…)), 7 id.
     */
    private val fenceRegex = Regex(
        """^(\s*)(`{3,}|~{3,})\s*([^\s`~]*)?(?:\s+(?:"([^"]*)"|'([^']*)'|\(([^)]*)\)))?(?:\s+\{#([^}]+)})?\s*$"""
    )

    /** True when [line] is a fenced code block declaration line (opening or closing fence). */
    fun parseFenceLine(line: String): FenceInfo? {
        val m = fenceRegex.matchEntire(line) ?: return null
        val indent = m.groupValues[1]
        val fence = m.groupValues[2]
        val language = m.groupValues[3].trim()
        val caption = m.groupValues[4].ifEmpty { m.groupValues[5] }.ifEmpty { m.groupValues[6] }.trim()
        val id = m.groupValues[7].trim()
        return FenceInfo(indent, fence, language, caption, id, line)
    }

    /** True when [line] is a `.code` function block header line. */
    fun parseCodeFunctionLine(line: String): CodeFunctionInfo? {
        val indent = line.takeWhile { it == ' ' || it == '\t' }
        val content = line.substring(indent.length)
        // `.code` must be the first non-space token on the line (not `text .code …`,
        // not `.codespan`).
        if (!content.startsWith(".code")) return null
        if (content.length > 5 && content[5].isLetterOrDigit()) return null
        val call = QuarkdownCallParser.parseCall(content, 0) ?: return null
        if (call.name != "code") return null
        val language = call.args.firstOrNull { it.paramName == "lang" }?.raw?.trim().orEmpty()
        val caption = call.args.firstOrNull { it.paramName == "caption" }?.raw?.trim().orEmpty()
        val id = call.args.firstOrNull { it.paramName == "ref" }?.raw?.trim().orEmpty()
        return CodeFunctionInfo(indent, language, caption, id, content, line)
    }

    /**
     * Rebuilds a fenced code block opening line, preserving the original indentation and
     * fence delimiter. Empty values are omitted.
     */
    fun buildFenceLine(originalLine: String, language: String, caption: String, id: String): String {
        val info = parseFenceLine(originalLine) ?: return originalLine
        val sb = StringBuilder(info.indent).append(info.fence)
        if (language.isNotEmpty()) sb.append(language)
        if (caption.isNotEmpty()) sb.append(" \"").append(caption).append("\"")
        if (id.isNotEmpty()) sb.append(" {#").append(id).append("}")
        return sb.toString()
    }

    /**
     * Rebuilds a `.code` block header line, preserving the original indentation and the
     * order of the existing arguments. The values of `lang`/`caption`/`ref` are updated
     * in place; empty values remove the argument; missing arguments are appended at the
     * end. All other arguments (e.g. `linenumbers:{no}`, a positional path) are kept.
     */
    fun buildCodeFunctionLine(originalLine: String, language: String, caption: String, id: String): String {
        val info = parseCodeFunctionLine(originalLine) ?: return originalLine
        val call = QuarkdownCallParser.parseCall(info.headerText, 0) ?: return originalLine

        val parts = mutableListOf<String>()
        var hasLang = false
        var hasCaption = false
        var hasRef = false
        for (arg in call.args) {
            when (arg.paramName) {
                null -> parts += "{${arg.raw}}"
                "lang" -> {
                    hasLang = true
                    if (language.isNotEmpty()) parts += "lang:{$language}"
                }
                "caption" -> {
                    hasCaption = true
                    if (caption.isNotEmpty()) parts += "caption:{$caption}"
                }
                "ref" -> {
                    hasRef = true
                    if (id.isNotEmpty()) parts += "ref:{$id}"
                }
                else -> parts += "${arg.paramName}:{${arg.raw}}"
            }
        }
        // Append the arguments that were not present in the original call.
        if (!hasLang && language.isNotEmpty()) parts += "lang:{$language}"
        if (!hasCaption && caption.isNotEmpty()) parts += "caption:{$caption}"
        if (!hasRef && id.isNotEmpty()) parts += "ref:{$id}"
        return info.indent + ".code" + parts.joinToString("") { " $it" }
    }

    /**
     * Returns the absolute line-start offsets of every *opening* fenced code block fence
     * in [text]. A single linear scan pairs each opening fence with the next same-char
     * fence (``` or ~~~), exactly like the folding builder, so closing fences never
     * produce an entry.
     */
    fun findFenceOpenOffsets(text: CharSequence): Set<Int> {
        val result = mutableSetOf<Int>()
        var i = 0
        var fenceStart = -1
        var fenceChar = '`'
        while (i < text.length) {
            val atLineStart = i == 0 || text[i - 1] == '\n'
            if (atLineStart) {
                var spaces = 0
                while (i + spaces < text.length && text[i + spaces] == ' ') spaces++
                val contentPos = i + spaces
                if (contentPos < text.length) {
                    val c = text[contentPos]
                    // Any line whose first non-space char is ``` or ~~~ (3+) is a fence
                    // line. A language identifier may follow the fence (```python), so no
                    // whitespace check is applied after the delimiter run.
                    if ((c == '`' || c == '~') && fenceStart < 0) {
                        val count = countFence(text, contentPos, c)
                        if (count >= 3) {
                            fenceStart = i
                            fenceChar = c
                        }
                    } else if (c == fenceChar && fenceStart >= 0) {
                        val count = countFence(text, contentPos, c)
                        if (count >= 3) {
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

    private fun countFence(text: CharSequence, pos: Int, c: Char): Int {
        var count = 0
        while (pos + count < text.length && text[pos + count] == c) count++
        return count
    }
}

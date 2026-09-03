package cc.carm.plugin.intellij.quarkdown.lang.table

/**
 * Parses and builds Quarkdown `.tablebyrows` table calls.
 *
 * Kept free of IntelliJ dependencies so it can be unit-tested in isolation.
 *
 * `.tablebyrows` takes an optional iterable of headers and an iterable of rows,
 * where each row is an iterable of cell values:
 * ```
 * .tablebyrows {.headers}
 *     - - John
 *       - 25
 *       - NY
 *     - - Lisa
 *       - 32
 *       - LA
 * ```
 *
 * The headers argument can be a literal list, a reference to a `.var`-defined
 * list (`{.headers}`), or any dynamic expression; the rows argument is either a
 * brace-wrapped list or the call's indented body. Only calls whose headers and
 * rows are *static* (literal lists, or a resolvable variable reference) can be
 * edited in the table editor — dynamic content (`.repeat`, function calls, ...)
 * cannot be represented in a grid.
 *
 * Note: the `"caption" {#id}` label line is a pipe-table-only feature and is
 * **not** supported after a `.tablebyrows` call (it renders as a plain paragraph),
 * so the editor only writes labels for Markdown output.
 */
object QuarkdownTableByRows {

    /** How the headers argument of a `.tablebyrows` call is expressed. */
    sealed class HeadersSource {
        /** No headers argument: the table has no header row. */
        data object Absent : HeadersSource()

        /** A literal list of headers (`{ - Name - Age }`, one item per line). */
        data class Literal(val items: List<String>) : HeadersSource()

        /** A reference to a `.var`-defined list (`{.name}`); [resolved] is `null`
         *  when the variable cannot be found or is not a literal list. */
        data class Reference(val name: String, val resolved: List<String>?) : HeadersSource()

        /** A dynamic expression (loop, chained call, ...) that cannot be edited statically. */
        data object Dynamic : HeadersSource()
    }

    /** A `.tablebyrows` call found in a document. */
    data class Block(
        /** Absolute offset of the call line's first character (including indentation). */
        val startOffset: Int,
        /** Absolute offset just after the last line of the call (args + body). */
        val endOffset: Int,
        /** Text of each line of the block (call, brace arguments, body). */
        val lines: List<String>,
        /** Absolute offset of each block line's first character. */
        val lineStarts: List<Int>,
        /** Leading whitespace of the call line. */
        val indent: String,
        /** How the headers argument is expressed. */
        val headersSource: HeadersSource,
        /** Headers resolved for display (empty when absent, dynamic or unresolved). */
        val headerItems: List<String>,
        /** The parsed data rows, or `null` when the rows are not a static literal list. */
        val rows: List<List<String>>?
    ) {
        /** True when the whole table (headers + rows) can be edited in a grid. */
        val isEditable: Boolean
            get() = rows != null && when (headersSource) {
                is HeadersSource.Dynamic -> false
                is HeadersSource.Reference -> headersSource.resolved != null
                else -> true
            }
    }

    private data class LineInfo(val start: Int, val text: String)

    private data class RawArg(val name: String?, val content: String)

    private val callLineRegex = Regex("""^(\s*)\.tablebyrows\b(.*)$""")
    private val varCallRegexTemplate = """^\s*\.var\s*\{\s*%s\s*\}"""
    private val variableReferenceRegex = Regex("""^\.\s*([A-Za-z][A-Za-z0-9_-]*)$""")
    private val listItemRegex = Regex("""^-\s+(.*)$""")
    private val namedArgumentRegex = Regex("""^([A-Za-z][A-Za-z0-9_-]*)\s*:""")

    /**
     * Quarkdown lists cannot contain bare (empty) items, so a blank cell is written as
     * an HTML comment placeholder (`- <!-- -->`), which renders as an empty cell.
     */
    private const val EMPTY_CELL_PLACEHOLDER = "<!-- -->"
    private val htmlCommentRegex = Regex("""^<!--.*?-->$""", RegexOption.DOT_MATCHES_ALL)

    // ------------------------------------------------------------------
    // Detection
    // ------------------------------------------------------------------

    /** Scans [text] for `.tablebyrows` call blocks (both static and dynamic ones). */
    fun findBlocks(text: CharSequence): List<Block> {
        val lines = splitLines(text)
        val blocks = mutableListOf<Block>()
        var i = 0
        while (i < lines.size) {
            if (callLineRegex.matches(lines[i].text)) {
                val payload = parseCallAt(lines, i)
                if (payload != null) {
                    blocks += buildBlock(text, lines, i, payload)
                    i = payload.nextLineIndex
                    continue
                }
            }
            i++
        }
        return blocks
    }

    /**
     * Parses the `.tablebyrows` call starting at line [ci]: its brace arguments
     * (possibly spanning multiple lines), followed by its indented body.
     */
    private fun parseCallAt(lines: List<LineInfo>, ci: Int): CallPayload? {
        val callLine = lines[ci]
        val match = callLineRegex.matchEntire(callLine.text) ?: return null
        val callIndent = match.groupValues[1].length
        var li = ci + 1

        // 1) Logical argument text: the rest of the call line, plus continuations
        //    (a trailing backslash continues the argument list on the next line).
        var logical = match.groupValues[2]
        while (logical.trimEnd().endsWith("\\") && li < lines.size) {
            logical = logical.trimEnd().removeSuffix("\\") + " " + lines[li].text.trimStart()
            li++
        }

        // 2) Brace arguments from the logical text; an unbalanced `{` consumes
        //    following physical lines until it closes.
        val args = mutableListOf<RawArg>()
        var source: String? = logical
        while (source != null) {
            val (parsed, leftover) = parseBraceArgs(source)
            args += parsed
            source = null
            val leftoverTrimmed = leftover?.trimStart().orEmpty()
            val dangling = leftover != null &&
                    (leftoverTrimmed.startsWith("{") || namedArgumentRegex.containsMatchIn(leftoverTrimmed))
            if (dangling) {
                var pending = leftover!!
                while (li < lines.size && braceDepth(pending) > 0) {
                    pending += "\n" + lines[li].text
                    li++
                }
                if (braceDepth(pending) <= 0) source = pending
            }
        }

        // 3) Body: the lines indented deeper than the call, up to the first
        //    non-blank line that is not indented deeper.
        val body = mutableListOf<String>()
        val bodyIndices = mutableListOf<Int>()
        var k = li
        while (k < lines.size) {
            val t = lines[k].text
            if (t.isBlank()) {
                body += t
                bodyIndices += k
                k++
                continue
            }
            if (indentWidth(t) > callIndent) {
                body += t
                bodyIndices += k
                k++
                continue
            }
            break
        }
        while (body.isNotEmpty() && body.last().isBlank()) {
            body.removeAt(body.lastIndex)
            bodyIndices.removeAt(bodyIndices.lastIndex)
        }

        val lastLineIndex = bodyIndices.lastOrNull() ?: (li - 1)
        val lastLine = lines[lastLineIndex]
        return CallPayload(
            args = args,
            bodyLines = body,
            nextLineIndex = lastLineIndex + 1,
            endOffset = lastLine.start + lastLine.text.length,
            blockLines = (ci..lastLineIndex).map { lines[it].text },
            blockLineStarts = (ci..lastLineIndex).map { lines[it].start }
        )
    }

    private data class CallPayload(
        val args: List<RawArg>,
        val bodyLines: List<String>,
        val nextLineIndex: Int,
        val endOffset: Int,
        val blockLines: List<String>,
        val blockLineStarts: List<Int>
    )

    /**
     * Extracts balanced `{...}` arguments from [source]. Returns the parsed
     * arguments and any leftover text: leftover starting with `{` (optionally
     * preceded by a `name:` prefix) is a dangling argument that must be balanced
     * against further lines; any other leftover is non-argument content.
     */
    private fun parseBraceArgs(source: String): Pair<List<RawArg>, String?> {
        val args = mutableListOf<RawArg>()
        var i = 0
        val n = source.length
        while (i < n) {
            while (i < n && source[i].isWhitespace()) i++
            if (i >= n) return args to null
            val argStart = i
            var name: String? = null
            val nameMatch = namedArgumentRegex.find(source, i)
            if (nameMatch != null && nameMatch.range.first == i) {
                name = nameMatch.groupValues[1]
                i = nameMatch.range.last + 1
                while (i < n && source[i].isWhitespace()) i++
            }
            if (i < n && source[i] == '{') {
                var depth = 0
                var j = i
                while (j < n) {
                    when (source[j]) {
                        '{' -> depth++
                        '}' -> {
                            depth--
                            if (depth == 0) break
                        }
                    }
                    j++
                }
                if (j >= n) return args to source.substring(argStart) // unbalanced
                args += RawArg(name, source.substring(i + 1, j))
                i = j + 1
            } else {
                return args to source.substring(argStart) // not an argument
            }
        }
        return args to null
    }

    private fun buildBlock(text: CharSequence, lines: List<LineInfo>, ci: Int, payload: CallPayload): Block {
        // Map arguments onto the (headers, rows) signature, positionally or by name.
        var headersArg: String? = null
        var headersGiven = false
        var rowsArg: String? = null
        var unknownArgument = false
        val positional = ArrayDeque(listOf("headers", "rows"))
        for (arg in payload.args) {
            val parameter = arg.name?.lowercase() ?: positional.removeFirstOrNull()
            when (parameter) {
                "headers" -> {
                    headersArg = arg.content
                    headersGiven = true
                }

                "rows" -> rowsArg = arg.content
                else -> unknownArgument = true
            }
        }

        val headersSource = when {
            unknownArgument -> HeadersSource.Dynamic
            !headersGiven -> HeadersSource.Absent
            else -> parseHeaders(headersArg.orEmpty(), text)
        }

        val rows: List<List<String>>? = when {
            unknownArgument -> null
            payload.bodyLines.any { it.isNotBlank() } -> parseRows(payload.bodyLines)
            rowsArg != null -> parseRows(rowsArg.lines())
            else -> emptyList()
        }

        val headerItems = when (headersSource) {
            is HeadersSource.Literal -> headersSource.items
            is HeadersSource.Reference -> headersSource.resolved ?: emptyList()
            else -> emptyList()
        }

        val callLine = lines[ci]
        val indentEnd = callLine.text.indexOfFirst { it != ' ' && it != '\t' }.let { if (it < 0) 0 else it }
        return Block(
            startOffset = callLine.start,
            endOffset = payload.endOffset,
            lines = payload.blockLines,
            lineStarts = payload.blockLineStarts,
            indent = callLine.text.substring(0, indentEnd),
            headersSource = headersSource,
            headerItems = headerItems,
            rows = rows
        )
    }

    // ------------------------------------------------------------------
    // Headers / rows / lists
    // ------------------------------------------------------------------

    private fun parseHeaders(argumentContent: String, text: CharSequence): HeadersSource {
        if (argumentContent.isBlank()) return HeadersSource.Absent
        val trimmed = argumentContent.trim()
        variableReferenceRegex.matchEntire(trimmed)?.let { ref ->
            val name = ref.groupValues[1]
            return HeadersSource.Reference(name, resolveVar(text, name))
        }
        // Not trimmed before list parsing: the items keep their indentation inside
        // brace arguments, and [parseListItems] tolerates any shared indentation.
        val items = parseListItems(argumentContent)
        return if (items != null) HeadersSource.Literal(items) else HeadersSource.Dynamic
    }

    /**
     * Resolves a `.var {name}` definition to its literal list items. Supports both
     * `.var {name} { - item ... }` (brace value on the same line) and the body form
     * `.var {name}` followed by indented `- item` lines. Returns `null` when the
     * variable is not found or its value is not a literal list.
     */
    fun resolveVar(text: CharSequence, name: String): List<String>? {
        val lines = splitLines(text)
        val varRegex = Regex(varCallRegexTemplate.format(Regex.escape(name)))
        for (i in lines.indices) {
            val match = varRegex.find(lines[i].text) ?: continue
            if (match.range.first != 0) continue
            val rest = lines[i].text.substring(match.range.last + 1)
            // A balanced brace value on the same line wins over the body.
            val (sameLineArgs, _) = parseBraceArgs(rest)
            if (sameLineArgs.isNotEmpty()) return parseListItems(sameLineArgs.first().content)

            val callIndent = indentWidth(lines[i].text)
            val body = mutableListOf<String>()
            var k = i + 1
            while (k < lines.size) {
                val t = lines[k].text
                if (t.isBlank()) {
                    body += t
                    k++
                    continue
                }
                if (indentWidth(t) > callIndent) {
                    body += t
                    k++
                    continue
                }
                break
            }
            while (body.isNotEmpty() && body.last().isBlank()) body.removeAt(body.lastIndex)
            return parseListItems(body.joinToString("\n"))
        }
        return null
    }

    /**
     * Parses the rows of a `.tablebyrows` body: a list whose items are themselves
     * lists of cells.
     * ```
     * - - John        <- row marker + first cell on the same line
     *   - 25          <- further cells, indented deeper than the row marker
     *   - NY
     * - - Lisa
     * ```
     * Returns `null` when any line is not part of such a static literal structure
     * (e.g. nested function calls like `.repeat`), making the table non-editable.
     */
    fun parseRows(rawLines: List<String>): List<List<String>>? {
        val contentLines = rawLines.filter { it.isNotBlank() }
        if (contentLines.isEmpty()) return emptyList()
        val baseIndent = contentLines.minOf { indentWidth(it) }

        val rows = mutableListOf<List<String>>()
        var cells: MutableList<String>? = null
        for (raw in contentLines) {
            val indent = indentWidth(raw)
            val trimmed = raw.trim()
            val markerContent = when {
                trimmed == "-" -> ""
                trimmed.startsWith("- ") -> trimmed.removePrefix("- ").trim()
                else -> return null
            }
            if (indent == baseIndent) {
                // A new row.
                cells?.let { rows += it }
                cells = mutableListOf()
                if (markerContent.isEmpty()) {
                    // Cells follow on the deeper-indented lines.
                } else if (markerContent == "-") {
                    // `- -`: nested empty first cell.
                    cells += ""
                } else if (markerContent.startsWith("- ")) {
                    cells += normalizeCell(markerContent.removePrefix("- ").trim())
                } else {
                    cells += normalizeCell(markerContent)
                }
            } else if (indent > baseIndent && cells != null) {
                cells += normalizeCell(markerContent)
            } else {
                return null
            }
        }
        cells?.let { rows += it }
        return rows
    }

    /** Parses a simple literal list (`- item` lines sharing one indentation) or returns `null`. */
    private fun parseListItems(content: String): List<String>? {
        val nonBlank = content.lines().filter { it.isNotBlank() }
        if (nonBlank.isEmpty()) return emptyList()
        val baseIndent = nonBlank.minOf { indentWidth(it) }
        val items = mutableListOf<String>()
        for (line in nonBlank) {
            if (indentWidth(line) != baseIndent) return null
            val match = listItemRegex.matchEntire(line.trim()) ?: return null
            items += normalizeCell(match.groupValues[1].trim())
        }
        return items
    }

    /** Blank cells are written as an HTML comment placeholder; read them back as empty. */
    private fun normalizeCell(content: String): String =
        if (htmlCommentRegex.matches(content)) "" else content

    // ------------------------------------------------------------------
    // Building
    // ------------------------------------------------------------------

    /**
     * Builds the `.tablebyrows` source lines for [table] with the given [indent].
     *
     * When [headersReference] is given (and the table has headers) the headers are
     * emitted as a variable reference (`{.name}`), preserving the original wiring;
     * otherwise a literal brace-wrapped list is written:
     * ```
     * .tablebyrows {
     *     - Name
     *     - Age
     * }
     *     - - John
     *       - 25
     * ```
     * Both forms were verified against the Quarkdown compiler. Alignment markers do
     * not exist in `.tablebyrows` and are dropped.
     */
    fun build(
        table: QuarkdownTableParser.Table,
        indent: String = "",
        headersReference: String? = null
    ): List<String> {
        val lines = mutableListOf<String>()
        val hasHeaders = table.headers.any { it.isNotBlank() }
        when {
            hasHeaders && headersReference != null ->
                lines += "$indent.tablebyrows {.$headersReference}"

            hasHeaders -> {
                lines += "$indent.tablebyrows {"
                for (header in table.headers) lines += "$indent    - ${headerItem(header)}"
                lines += "$indent}"
            }

            else -> lines += "$indent.tablebyrows"
        }
        for (row in table.rows) {
            // Trailing blank cells are dropped: the stdlib fills missing cells with
            // empty content. A fully blank row keeps one comment placeholder so the
            // row itself survives (Quarkdown lists cannot hold bare empty items).
            val lastContent = row.indexOfLast { it.isNotBlank() }
            val cells = when {
                lastContent < 0 -> listOf("")
                else -> row.take(lastContent + 1)
            }
            lines += "$indent    - ${nestedItem(cells[0])}"
            for (c in 1 until cells.size) {
                lines += "$indent      ${nestedItem(cells[c])}"
            }
        }
        return lines
    }

    /** Converts an editable [block] into the common [QuarkdownTableParser.Table] model. */
    fun toTable(block: Block): QuarkdownTableParser.Table? {
        if (!block.isEditable) return null
        return QuarkdownTableParser.Table(block.headerItems, block.rows!!, emptyList())
    }

    /** A nested list item: `- content`, or a comment placeholder for a blank cell. */
    private fun nestedItem(cell: String): String =
        if (cell.isBlank()) "- $EMPTY_CELL_PLACEHOLDER" else "- $cell"

    /** A header list item; blank headers use the same comment placeholder. */
    private fun headerItem(header: String): String =
        if (header.isBlank()) EMPTY_CELL_PLACEHOLDER else header

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun splitLines(text: CharSequence): List<LineInfo> {
        val out = mutableListOf<LineInfo>()
        var start = 0
        var i = 0
        while (i < text.length) {
            if (text[i] == '\n') {
                out += LineInfo(start, text.subSequence(start, i).toString())
                start = i + 1
            }
            i++
        }
        out += LineInfo(start, text.subSequence(start, text.length).toString())
        return out
    }

    private fun indentWidth(line: String): Int = line.indexOfFirst { it != ' ' && it != '\t' }.let {
        if (it < 0) line.length else it
    }

    private fun braceDepth(text: String): Int {
        var depth = 0
        for (c in text) {
            when (c) {
                '{' -> depth++
                '}' -> depth--
            }
        }
        return depth
    }
}

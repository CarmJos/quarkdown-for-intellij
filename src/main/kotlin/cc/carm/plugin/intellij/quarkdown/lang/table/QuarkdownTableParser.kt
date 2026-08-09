package cc.carm.plugin.intellij.quarkdown.lang.table

/**
 * Parses and builds Quarkdown table syntax.
 *
 * Kept free of IntelliJ dependencies so it can be unit-tested in isolation.
 *
 * Table syntax (CommonMark/GFM-style, as used by Quarkdown):
 * ```
 * | Header 1 | Header 2 | Header 3 |
 * |:---------|:--------:|---------:|
 * | Cell 1   | Cell 2   | Cell 3   |
 * ```
 *
 * The separator row encodes per-column alignment:
 *  - `:---`  → left-aligned
 *  - `:---:` → centered
 *  - `---:`  → right-aligned
 *  - `---`   → no explicit alignment
 */
object QuarkdownTableParser {

    enum class Alignment(val separator: String) {
        NONE("---"),
        LEFT(":---"),
        CENTER(":---:"),
        RIGHT("---:");

        companion object {
            fun fromSeparator(raw: String): Alignment {
                val s = raw.trim()
                return when {
                    s.startsWith(":") && s.endsWith(":") -> CENTER
                    s.startsWith(":") -> LEFT
                    s.endsWith(":") -> RIGHT
                    else -> NONE
                }
            }
        }
    }

    /** A parsed Quarkdown table. */
    data class Table(
        val headers: List<String>,
        val rows: List<List<String>>,
        val alignments: List<Alignment>
    ) {
        val columnCount: Int get() = headers.size
        val rowCount: Int get() = rows.size
    }

    /** True when [line] looks like a table separator row (`| --- | :---: |`). */
    private val separatorRegex = Regex("""^\|?\s*(?::?-{3,}:?)(?:\s*\|\s*(?::?-{3,}:?))*\s*\|?$""")

    private val cellSplitRegex = Regex("""\s*\|\s*""")

    /**
     * Parses the given table lines (typically the consecutive `|`-containing lines of
     * a document). Returns `null` when [lines] does not form a valid table
     * (missing header or separator).
     */
    fun parse(lines: List<String>): Table? {
        val cleaned = lines.map { it.trim() }.filter { it.isNotEmpty() }
        if (cleaned.size < 2) return null

        // First line is the header, second the separator.
        val headerLine = cleaned[0]
        if (!headerLine.contains('|')) return null
        val separatorLine = cleaned[1]
        if (!separatorRegex.matches(separatorLine)) return null

        val headers = splitCells(headerLine)
        if (headers.isEmpty()) return null

        val alignments = splitCells(separatorLine).map { Alignment.fromSeparator(it) }

        // Remaining lines are data rows; skip any table separators just in case.
        val dataRows = cleaned.drop(2).filter { !separatorRegex.matches(it) }
            .map { splitCells(it).toMutableList() }
        // Pad rows to the header width.
        for (row in dataRows) {
            while (row.size < headers.size) row.add("")
        }
        val rows = dataRows.map { it.take(headers.size) }

        return Table(headers, rows, alignments)
    }

    /** Splits a table row into cells, removing the outer pipes. */
    private fun splitCells(line: String): List<String> {
        val trimmed = line.trim()
        val content = if (trimmed.startsWith("|")) trimmed.drop(1) else trimmed
        val withoutTrailing = if (content.endsWith("|")) content.dropLast(1) else content
        return withoutTrailing.split(cellSplitRegex).map { it.trim() }
    }

    /**
     * Builds the Quarkdown table source lines from the given data. Cells are padded to
     * a common column width so the source stays aligned and readable.
     *
     * Each column is at least as wide as its alignment marker (e.g. `:---:`), and the
     * separator row is built from the alignment so every cell matches the header/data
     * column width exactly (e.g. a centered column of width 8 renders as `:------:`).
     */
    fun build(table: Table): List<String> {
        val columns = table.columnCount
        val alignments = table.alignments.padTo(columns, Alignment.NONE)
        val colWidths = IntArray(columns) { 0 }
        for (c in 0 until columns) {
            var width = table.headers[c].length
            for (row in table.rows) {
                if (c < row.size) width = maxOf(width, row[c].length)
            }
            // The separator marker must always fit into the column.
            colWidths[c] = maxOf(width, alignments[c].separator.length)
        }

        val lines = mutableListOf<String>()

        // Header row.
        val headerRow = table.headers.mapIndexed { c, h -> padCell(h, colWidths[c]) }.joinToString(" | ", "| ", " |")
        lines += headerRow

        // Separator row with alignment markers, matching the column widths exactly.
        val sepRow = (0 until columns).map { c -> buildSeparatorCell(alignments[c], colWidths[c]) }
            .joinToString(" | ", "| ", " |")
        lines += sepRow

        // Data rows.
        for (row in table.rows) {
            val cells = (0 until columns).map { c ->
                val value = if (c < row.size) row[c] else ""
                padCell(value, colWidths[c])
            }.joinToString(" | ", "| ", " |")
            lines += cells
        }

        return lines
    }

    /**
     * Builds a single separator cell that fills [width] and encodes [alignment]:
     *  - NONE   → `-----`
     *  - LEFT   → `:----`
     *  - RIGHT  → `----:`
     *  - CENTER → `:----:`
     *
     * [width] is guaranteed to be at least the marker's minimum length, so the
     * produced cell always re-parses to the same [alignment].
     */
    private fun buildSeparatorCell(alignment: Alignment, width: Int): String = when (alignment) {
        Alignment.NONE -> "-".repeat(width)
        Alignment.LEFT -> ":" + "-".repeat(width - 1)
        Alignment.RIGHT -> "-".repeat(width - 1) + ":"
        Alignment.CENTER -> ":" + "-".repeat(width - 2) + ":"
    }

    private fun padCell(text: String, width: Int): String {
        if (text.length >= width) return text
        return text + " ".repeat(width - text.length)
    }

    private fun <T> List<T>.padTo(size: Int, value: T): List<T> {
        if (this.size >= size) return this
        return this + List(size - this.size) { value }
    }
}

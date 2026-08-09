package cc.carm.plugin.intellij.quarkdown.lang.table

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project

/**
 * In-place modification of Quarkdown tables in an open document: formatting (re-align),
 * adding rows/columns and changing a column's alignment.
 *
 * All operations parse the current table source with [QuarkdownTableParser], mutate the
 * parsed model and rebuild the source lines, preserving leading indentation.
 */
object QuarkdownTableModificationUtils {

    private val separatorRegex = Regex("""^\|?\s*:?-{3,}:?(?:\s*\|\s*:?-{3,}:?)*\s*\|?$""")

    /** Matches a Quarkdown table label/id line: `"label" {#id}` (both optional, but at least one). */
    private val labelLineRegex = Regex("""^\s*(?:"([^"]*)"\s*)?(?:\{#([^}]+)}\s*)?$""")

    /** A table block found in a document: consecutive `|`-containing lines. */
    data class TableBlock(
        /** Absolute offset of the first table line. */
        val startOffset: Int,
        /** Absolute offset just after the last table line (before its newline). */
        val endOffset: Int,
        /** Absolute offset of each table line's first character. */
        val lineStarts: List<Int>,
        /** Text of each table line (without trailing newline). */
        val lines: List<String>,
        /** Optional label/id line following the table (e.g. `"label" {#id}`), or `null`. */
        val labelLine: String? = null,
        /** Absolute offset of the label/id line's first character, or `-1` when absent. */
        val labelLineStart: Int = -1,
        /** Absolute offset just after the label/id line (before its newline), or `endOffset`. */
        val fullEndOffset: Int = -1
    ) {
        val headerLineIndex: Int = 0
        val separatorLineIndex: Int = 1
        val lastLineIndex: Int get() = lines.size - 1
    }

    /**
     * Scans [text] for table blocks. A table starts at a `|`-line whose next non-empty
     * line is a separator row; the block extends over all following `|`-containing lines
     * and an optional trailing `"label" {#id}` line.
     */
    fun findTableBlocks(text: CharSequence): List<TableBlock> {
        val blocks = mutableListOf<TableBlock>()
        var i = 0
        val n = text.length
        while (i < n) {
            val lineStart = i
            var lineEnd = i
            while (lineEnd < n && text[lineEnd] != '\n') lineEnd++
            val line = text.subSequence(lineStart, lineEnd).toString()

            if (line.contains('|') && isFollowedBySeparator(text, lineEnd)) {
                val blockLines = mutableListOf(line)
                val blockStarts = mutableListOf(lineStart)
                var k = if (lineEnd < n) lineEnd + 1 else lineEnd // skip the newline
                while (k < n) {
                    val lStart = k
                    var lEnd = k
                    while (lEnd < n && text[lEnd] != '\n') lEnd++
                    val lText = text.subSequence(lStart, lEnd).toString()
                    if (!lText.contains('|')) break
                    blockLines += lText
                    blockStarts += lStart
                    k = if (lEnd < n) lEnd + 1 else lEnd
                }
                val end = blockStarts.last() + blockLines.last().length

                // Check for a trailing label/id line immediately after the table.
                var labelLine: String? = null
                var labelLineStart = -1
                var fullEndOffset = end
                if (k < n) {
                    var lStart = k
                    var lEnd = k
                    while (lEnd < n && text[lEnd] != '\n') lEnd++
                    val lText = text.subSequence(lStart, lEnd).toString()
                    if (lText.isNotBlank() && labelLineRegex.matches(lText)) {
                        labelLine = lText
                        labelLineStart = lStart
                        fullEndOffset = lEnd
                    }
                }

                blocks += TableBlock(lineStart, end, blockStarts, blockLines, labelLine, labelLineStart, fullEndOffset)
                i = k
            } else {
                i = if (lineEnd < n) lineEnd + 1 else lineEnd
            }
        }
        return blocks
    }

    private fun isFollowedBySeparator(text: CharSequence, afterLineEnd: Int): Boolean {
        var j = afterLineEnd
        while (j < text.length) {
            // Skip the newline that ends the previous line.
            if (text[j] == '\n') {
                j++
                continue
            }
            var sEnd = j
            while (sEnd < text.length && text[sEnd] != '\n') sEnd++
            val sLine = text.subSequence(j, sEnd).toString()
            if (sLine.trim().isEmpty()) {
                j = if (sEnd < text.length) sEnd + 1 else sEnd
                continue
            }
            return separatorRegex.matches(sLine.trim())
        }
        return false
    }

    // ------------------------------------------------------------------
    // Operations (all run in a write command)
    // ------------------------------------------------------------------

    fun formatTable(project: Project?, document: Document, block: TableBlock) {
        modify(project, document, block) { it }
    }

    fun addRow(project: Project?, document: Document, block: TableBlock) {
        modify(project, document, block) { table ->
            val emptyRow = List(table.columnCount) { "" }
            table.copy(rows = table.rows + listOf(emptyRow))
        }
    }

    fun addColumn(project: Project?, document: Document, block: TableBlock) {
        modify(project, document, block) { table ->
            table.copy(
                headers = table.headers + "",
                alignments = table.alignments + QuarkdownTableParser.Alignment.NONE,
                rows = table.rows.map { row -> row + "" }
            )
        }
    }

    fun setColumnAlignment(
        project: Project?,
        document: Document,
        block: TableBlock,
        column: Int,
        alignment: QuarkdownTableParser.Alignment
    ) {
        modify(project, document, block) { table ->
            val alignments = table.alignments.toMutableList()
            while (alignments.size <= column) alignments.add(QuarkdownTableParser.Alignment.NONE)
            alignments[column] = alignment
            table.copy(alignments = alignments)
        }
    }

    /** Inserts an empty row at the given index in [block] (0-based among data rows). */
    fun insertRow(project: Project?, document: Document, block: TableBlock, row: Int) {
        modify(project, document, block) { table ->
            val emptyRow = List(table.columnCount) { "" }
            val rows = table.rows.toMutableList()
            rows.add(row.coerceIn(0, rows.size), emptyRow)
            table.copy(rows = rows)
        }
    }

    /** Removes the data row at the given index. */
    fun deleteRow(project: Project?, document: Document, block: TableBlock, row: Int) {
        modify(project, document, block) { table ->
            val rows = table.rows.toMutableList()
            if (row in rows.indices && rows.size > 1) rows.removeAt(row)
            table.copy(rows = rows)
        }
    }

    /** Swaps two data rows (indices must be valid). */
    fun swapRows(project: Project?, document: Document, block: TableBlock, row1: Int, row2: Int) {
        modify(project, document, block) { table ->
            val rows = table.rows.toMutableList()
            if (row1 in rows.indices && row2 in rows.indices) {
                val tmp = rows[row1]
                rows[row1] = rows[row2]
                rows[row2] = tmp
            }
            table.copy(rows = rows)
        }
    }

    /** Inserts an empty column before/after [column] in [block]. */
    fun insertColumn(
        project: Project?,
        document: Document,
        block: TableBlock,
        column: Int,
        after: Boolean = true
    ) {
        modify(project, document, block) { table ->
            val idx = (column + if (after) 1 else 0).coerceIn(0, table.columnCount)
            val headers = table.headers.toMutableList()
            headers.add(idx, "")
            val alignments = table.alignments.toMutableList()
            alignments.add(idx, QuarkdownTableParser.Alignment.NONE)
            val rows = table.rows.map { row ->
                val r = row.toMutableList()
                r.add(idx, "")
                r
            }
            table.copy(headers = headers, alignments = alignments, rows = rows)
        }
    }

    /** Removes the column at [column]. */
    fun deleteColumn(project: Project?, document: Document, block: TableBlock, column: Int) {
        modify(project, document, block) { table ->
            if (table.columnCount <= 1 || column !in 0 until table.columnCount) return@modify table
            table.copy(
                headers = table.headers.filterIndexed { i, _ -> i != column },
                alignments = table.alignments.filterIndexed { i, _ -> i != column },
                rows = table.rows.map { row -> row.filterIndexed { i, _ -> i != column } }
            )
        }
    }

    /** Swaps two columns (indices must be valid). */
    fun swapColumns(project: Project?, document: Document, block: TableBlock, col1: Int, col2: Int) {
        modify(project, document, block) { table ->
            if (col1 !in 0 until table.columnCount || col2 !in 0 until table.columnCount) return@modify table
            fun <T> swap(list: List<T>): List<T> {
                val m = list.toMutableList()
                val tmp = m[col1]
                m[col1] = m[col2]
                m[col2] = tmp
                return m
            }
            table.copy(
                headers = swap(table.headers),
                alignments = swap(table.alignments),
                rows = table.rows.map { swap(it) }
            )
        }
    }

    /** Selects the whole row (including border pipes) with the editor caret. */
    fun selectRow(project: Project?, editor: Editor, block: TableBlock, row: Int) {
        if (row !in block.lines.indices) return
        val start = block.lineStarts[row]
        val end = start + block.lines[row].length
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
            editor.caretModel.removeSecondaryCarets()
            editor.caretModel.currentCaret.setSelection(start, end)
        }
    }

    /**
     * Selects the given column across all rows using multiple carets.
     * @param withBorders when true, the pipes surrounding each cell are included.
     */
    fun selectColumn(
        project: Project?,
        editor: Editor,
        block: TableBlock,
        column: Int,
        withBorders: Boolean = true
    ) {
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
            val caretModel = editor.caretModel
            caretModel.removeSecondaryCarets()
            var primarySet = false
            for (i in block.lines.indices) {
                val ranges = cellRanges(block, i)
                if (column >= ranges.size) continue
                val (cellStart, cellEnd) = ranges[column]
                val start = if (withBorders) cellStart - 1 else cellStart
                val end = if (withBorders) cellEnd + 1 else cellEnd
                if (start < block.lineStarts[i] || end > block.lineStarts[i] + block.lines[i].length) continue
                if (!primarySet) {
                    caretModel.currentCaret.setSelection(start, end)
                    primarySet = true
                } else {
                    caretModel.addCaret(editor.offsetToVisualPosition(start))?.setSelection(start, end)
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun modify(
        project: Project?,
        document: Document,
        block: TableBlock,
        transform: (QuarkdownTableParser.Table) -> QuarkdownTableParser.Table
    ) {
        val parsed = QuarkdownTableParser.parse(block.lines) ?: return
        val newTable = transform(parsed)
        val newLines = QuarkdownTableParser.build(newTable)
        val indent = indentOf(block)
        val replacement = newLines.joinToString("\n") { indent + it }

        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
            document.replaceString(block.startOffset, block.endOffset, replacement)
        }
    }

    private fun indentOf(block: TableBlock): String {
        val first = block.lines.firstOrNull() ?: return ""
        val idx = first.indexOfFirst { it != ' ' && it != '\t' }
        return if (idx > 0) first.substring(0, idx) else ""
    }

    /**
     * Computes the text range of every cell in a table row.
     * Cell content excludes the surrounding pipes.
     */
    private fun cellRanges(block: TableBlock, lineIndex: Int): List<Pair<Int, Int>> {
        val line = block.lines[lineIndex]
        val lineStart = block.lineStarts[lineIndex]
        val ranges = mutableListOf<Pair<Int, Int>>()
        var i = 0
        if (line.startsWith("|")) i = 1
        var colStart = lineStart + i
        while (i < line.length) {
            val pipe = line.indexOf('|', i)
            if (pipe < 0) {
                ranges += colStart to (lineStart + line.length)
                break
            }
            ranges += colStart to (lineStart + pipe)
            i = pipe + 1
            colStart = lineStart + i
        }
        return ranges
    }

}

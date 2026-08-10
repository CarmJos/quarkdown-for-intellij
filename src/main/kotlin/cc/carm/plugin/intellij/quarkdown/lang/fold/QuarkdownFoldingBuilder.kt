package cc.carm.plugin.intellij.quarkdown.lang.fold

import cc.carm.plugin.intellij.quarkdown.QuarkdownBundle
import cc.carm.plugin.intellij.quarkdown.QuarkdownLanguage
import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.FoldingGroup
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

/**
 * Code folding for Quarkdown files — sections, fenced code blocks, and tables.
 *
 * Foldable regions:
 *   1. **Section** — from a heading line to the next heading of same or higher level.
 *   2. **Code block** — from ` ```lang ` to matching ` ``` `.
 *   3. **Table** — consecutive lines containing `|`.
 *
 * Each fold shows a placeholder like `... (12 lines)` or `` ```...``` ``.
 */
class QuarkdownFoldingBuilder : FoldingBuilderEx() {

    // ------------------------------------------------------------------
    // FoldingBuilderEx API
    // ------------------------------------------------------------------

    override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<FoldingDescriptor> {
        if (root !is PsiFile) return FoldingDescriptor.EMPTY_ARRAY
        if (root.language !is QuarkdownLanguage) return FoldingDescriptor.EMPTY_ARRAY

        val descriptors = mutableListOf<FoldingDescriptor>()
        val text = document.text
        val lines = text.split("\n")

        // Collect headings: (lineIndex, level)
        val headings = mutableListOf<Pair<Int, Int>>()
        for ((i, line) in lines.withIndex()) {
            val trimmed = line.trimStart()
            if (trimmed.startsWith("#")) {
                var hCount = 0
                while (hCount < trimmed.length && trimmed[hCount] == '#') hCount++
                if (hCount in 1..6) {
                    val after = hCount
                    if (after < trimmed.length && (trimmed[after] == ' ' || trimmed[after] == '\t')) {
                        headings.add(i to hCount)
                    }
                }
            }
        }

        // --- Section folding ---
        for (i in headings.indices) {
            val (lineIdx, level) = headings[i]
            val startLine = lines[lineIdx]
            val startOffset = offsetOfLine(lines, lineIdx)
            // Fold starts after the heading line (skip the heading itself)
            val foldStart = startOffset + startLine.length

            // Find the end: next heading of same or higher level
            var foldEnd = text.length
            for (j in (i + 1) until headings.size) {
                if (headings[j].second <= level) {
                    foldEnd = offsetOfLine(lines, headings[j].first)
                    break
                }
            }

            // Trim trailing blank lines so a separator remains visible after folding
            val nextLineIdx = if (foldEnd < text.length) lineIndexAt(lines, foldEnd) else lines.size
            foldEnd = trimTrailingBlankLines(lines, lineIdx + 1, nextLineIdx)

            if (foldStart < foldEnd) {
                val contentLines = countNonEmptyLines(text, foldStart, foldEnd)
                if (contentLines > 0) {
                    val group = FoldingGroup.newGroup("quarkdown.section")
                    val unit = QuarkdownBundle.message(
                        if (contentLines == 1) "quarkdown.fold.line" else "quarkdown.fold.lines"
                    )
                    descriptors.add(
                        FoldingDescriptor(
                            root,
                            foldStart,
                            foldEnd,
                            group,
                            QuarkdownBundle.message("quarkdown.fold.section.placeholder", contentLines, unit)
                        )
                    )
                }
            }
        }

        // --- Code block folding ---
        val fenceRanges = findFenceRanges(text)
        val codeGroup = FoldingGroup.newGroup("quarkdown.code")
        for ((from, to) in fenceRanges) {
            val nlIdx = text.indexOf('\n', from)
            val openingLine = text.substring(from, if (nlIdx < 0 || nlIdx > to) to else nlIdx)
            val lang = openingLine.trim().removeSurrounding("`").trim()
            val placeholder = if (lang.isNotEmpty()) "```$lang ... ```" else "```...```"
            descriptors.add(
                FoldingDescriptor(root, from, to, codeGroup, placeholder)
            )
        }

        // --- Table folding ---
        val tableRanges = findTableRanges(lines)
        val tableGroup = FoldingGroup.newGroup("quarkdown.table")
        for ((startIdx, endIdx) in tableRanges) {
            val foldStart = offsetOfLine(lines, startIdx)
            val foldEnd = offsetOfLine(lines, endIdx) + lines[endIdx].length
            val rowCount = endIdx - startIdx + 1
            val unit = QuarkdownBundle.message(
                if (rowCount == 1) "quarkdown.fold.row" else "quarkdown.fold.rows"
            )
            descriptors.add(
                FoldingDescriptor(
                    root,
                    foldStart,
                    foldEnd,
                    tableGroup,
                    QuarkdownBundle.message("quarkdown.fold.table.placeholder", rowCount, unit)
                )
            )
        }

        return descriptors.toTypedArray()
    }

    override fun getPlaceholderText(node: ASTNode): String = "..."

    override fun isCollapsedByDefault(node: ASTNode): Boolean = false

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Compute the character offset of [lineIdx] within the full text. */
    private fun offsetOfLine(lines: List<String>, lineIdx: Int): Int {
        var offset = 0
        for (i in 0 until lineIdx) offset += lines[i].length + 1 // +1 for '\n'
        return offset
    }

    /** Count non-blank lines in [text] between [start] and [end]. */
    private fun countNonEmptyLines(text: String, start: Int, end: Int): Int {
        var count = 0
        var i = start
        while (i < end) {
            val lineEnd = text.indexOf('\n', i).let { if (it < 0 || it > end) end else it }
            if (text.substring(i, lineEnd).isNotBlank()) count++
            i = lineEnd + 1
        }
        return count
    }

    /**
     * Find fenced code block ranges: list of (start, end) where start is the
     * beginning of the opening fence line and end is the end of the closing fence line.
     */
    private fun findFenceRanges(text: String): List<Pair<Int, Int>> {
        val ranges = mutableListOf<Pair<Int, Int>>()
        var i = 0
        var fenceStart = -1
        var fenceChars: Char = '`'

        while (i < text.length) {
            val atLineStart = i == 0 || text[i - 1] == '\n'
            if (atLineStart) {
                var spaces = 0
                while (i + spaces < text.length && text[i + spaces] == ' ') spaces++
                val contentPos = i + spaces

                if (contentPos < text.length) {
                    val c = text[contentPos]
                    if ((c == '`' || c == '~') && fenceStart < 0) {
                        var count = 0
                        while (contentPos + count < text.length && text[contentPos + count] == c) count++
                        if (count >= 3) {
                            val after = contentPos + count
                            if (after >= text.length || text[after] == ' ' || text[after] == '\t' || text[after] == '\n' || text[after] == '\r') {
                                fenceStart = i
                                fenceChars = c
                            }
                        }
                    } else if (c == fenceChars && fenceStart >= 0) {
                        var count = 0
                        while (contentPos + count < text.length && text[contentPos + count] == c) count++
                        if (count >= 3) {
                            val after = contentPos + count
                            if (after >= text.length || text[after] == ' ' || text[after] == '\t' || text[after] == '\n' || text[after] == '\r') {
                                var lineEnd = after
                                while (lineEnd < text.length && text[lineEnd] != '\n') lineEnd++
                                if (lineEnd < text.length) lineEnd++
                                ranges.add(fenceStart to lineEnd)
                                fenceStart = -1
                            }
                        }
                    }
                }
            }
            i++
        }
        return ranges
    }

    /**
     * Find table ranges: list of (startLineIdx, endLineIdx) for consecutive
     * lines containing `|`.
     */
    private fun findTableRanges(lines: List<String>): List<Pair<Int, Int>> {
        val ranges = mutableListOf<Pair<Int, Int>>()
        var tableStart = -1

        for (i in lines.indices) {
            val hasPipe = lines[i].contains('|')
            if (hasPipe && tableStart < 0) {
                tableStart = i
            } else if (!hasPipe && tableStart >= 0) {
                if (i - tableStart >= 2) {
                    ranges.add(tableStart to i - 1)
                }
                tableStart = -1
            }
        }
        if (tableStart >= 0 && lines.size - tableStart >= 2) {
            ranges.add(tableStart to lines.size - 1)
        }
        return ranges
    }

    /**
     * Trim trailing blank lines so a separator remains visible after folding.
     * Returns the character offset of [trimmedLineIdx].
     */
    private fun trimTrailingBlankLines(lines: List<String>, startLine: Int, endLine: Int): Int {
        var i = endLine
        while (i > startLine && lines[i - 1].trim().isEmpty()) i--
        return offsetOfLine(lines, i)
    }

    /** Find the line index that contains the given character offset. */
    private fun lineIndexAt(lines: List<String>, offset: Int): Int {
        var pos = 0
        for (i in lines.indices) {
            if (pos + lines[i].length >= offset) return i
            pos += lines[i].length + 1
        }
        return lines.size
    }
}

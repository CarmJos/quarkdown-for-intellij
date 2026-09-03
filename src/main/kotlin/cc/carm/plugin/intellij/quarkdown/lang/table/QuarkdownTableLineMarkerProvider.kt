package cc.carm.plugin.intellij.quarkdown.lang.table

import cc.carm.plugin.intellij.quarkdown.QuarkdownBundle
import cc.carm.plugin.intellij.quarkdown.QuarkdownFileType
import cc.carm.plugin.intellij.quarkdown.QuarkdownIcons
import cc.carm.plugin.intellij.quarkdown.action.table.TableEditorDialog
import cc.carm.plugin.intellij.quarkdown.lang.reference.QuarkdownIdRenameUtils
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import java.awt.event.MouseEvent

/**
 * Shows a table icon in the gutter on the first line of a Quarkdown table block.
 *
 * Two kinds of tables are recognized:
 *  - **Markdown pipe tables** (`| a | b |` + separator row), optionally followed by a
 *    `"caption" {#id}` line;
 *  - **`.tablebyrows` calls** with static (grid-editable) headers and rows. Dynamic
 *    tables (loops, unresolved variable references, ...) get no marker.
 *
 * Clicking opens [TableEditorDialog], a spreadsheet-style editor that edits the table
 * data and converts between the two table formats. The caption/id are written as a
 * `"label" {#id}` line below Markdown tables (captions are a pipe-table-only feature),
 * with the same indentation.
 */
class QuarkdownTableLineMarkerProvider : LineMarkerProvider {

    /** Which kind of table a gutter marker belongs to. */
    enum class TableKind { MARKDOWN, TABLE_BY_ROWS }

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        val file = element.containingFile ?: return null
        if (file.fileType !is QuarkdownFileType) return null
        if (element.firstChild != null) return null

        val text = file.text
        val elementOffset = element.textRange.startOffset
        val lineStart = findLineStart(text, elementOffset)

        // Only show one marker per table: on the very first line of a table block,
        // anchored at the element that starts the line (offset == lineStart).
        if (elementOffset != lineStart) return null
        val kind = detectTableKind(text, lineStart) ?: return null

        return LineMarkerInfo(
            element,
            element.textRange,
            QuarkdownIcons.TABLE_MARKER,
            { QuarkdownBundle.message("quarkdown.marker.table.tooltip") },
            TableGutterHandler(kind),
            GutterIconRenderer.Alignment.RIGHT,
            { QuarkdownBundle.message("quarkdown.marker.table.tooltip") }
        )
    }

    /** Determines which kind of table begins at [lineStart], if any. */
    private fun detectTableKind(text: CharSequence, lineStart: Int): TableKind? {
        val line = getFullLine(text, lineStart)
        if (line.trimStart().startsWith(".tablebyrows")) {
            val block = QuarkdownTableByRows.findBlocks(text).firstOrNull { it.startOffset == lineStart }
            return if (block != null && block.isEditable) TableKind.TABLE_BY_ROWS else null
        }
        if (isTableStartLine(text, lineStart)) return TableKind.MARKDOWN
        return null
    }

    /**
     * True when the line at [lineStart] begins a table block: it contains a `|` and is
     * followed by a separator row (`| --- | :---: |`) on the next non-empty line.
     */
    private fun isTableStartLine(text: CharSequence, lineStart: Int): Boolean {
        val line = getFullLine(text, lineStart)
        if (!line.contains('|')) return false

        var next = skipLine(text, lineStart)
        while (next < text.length) {
            val nextLine = getFullLine(text, next)
            if (nextLine.trim().isEmpty()) {
                next = skipLine(text, next)
                continue
            }
            return separatorRegex.matches(nextLine.trim())
        }
        return false
    }

    private fun getFullLine(text: CharSequence, offset: Int): String {
        val start = findLineStart(text, offset)
        val end = findLineEnd(text, offset)
        return text.subSequence(start, end).toString()
    }

    private fun findLineStart(text: CharSequence, offset: Int): Int {
        var i = offset.coerceAtMost(text.length - 1)
        while (i > 0 && text[i - 1] != '\n') i--
        return i
    }

    private fun findLineEnd(text: CharSequence, offset: Int): Int {
        var i = offset
        while (i < text.length && text[i] != '\n') i++
        return i
    }

    private fun skipLine(text: CharSequence, offset: Int): Int {
        var i = findLineEnd(text, offset)
        if (i < text.length && text[i] == '\n') i++
        return i
    }

    inner class TableGutterHandler(private val kind: TableKind) : GutterIconNavigationHandler<PsiElement> {

        override fun navigate(e: MouseEvent, elt: PsiElement) {
            val project = elt.project
            val file = elt.containingFile ?: return
            val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return
            val lineStart = findLineStart(document.immutableCharSequence, elt.textRange.startOffset)

            when (kind) {
                TableKind.MARKDOWN -> editMarkdownTable(project, file, document, lineStart)
                TableKind.TABLE_BY_ROWS -> editTableByRows(project, file, document, lineStart)
            }
        }

        private fun editMarkdownTable(
            project: com.intellij.openapi.project.Project,
            file: com.intellij.psi.PsiFile,
            document: com.intellij.openapi.editor.Document,
            lineStart: Int
        ) {
            val block = QuarkdownTableModificationUtils.findTableBlocks(document.immutableCharSequence)
                .firstOrNull { it.startOffset == lineStart } ?: return
            val parsed = QuarkdownTableParser.parse(block.lines) ?: return

            val dialog = TableEditorDialog(project)
            dialog.loadMarkdownTable(parsed, block.labelLine, indentOf(block.lines))
            dialog.setTarget(document, block.startOffset)
            if (!dialog.showAndGet()) return

            val oldId = QuarkdownTableModificationUtils.parseLabelLineId(block.labelLine)
            applyDialogResult(project, file, document, block.startOffset, oldId, dialog)
        }

        private fun editTableByRows(
            project: com.intellij.openapi.project.Project,
            file: com.intellij.psi.PsiFile,
            document: com.intellij.openapi.editor.Document,
            lineStart: Int
        ) {
            val block = QuarkdownTableByRows.findBlocks(document.immutableCharSequence)
                .firstOrNull { it.startOffset == lineStart } ?: return
            val table = QuarkdownTableByRows.toTable(block) ?: return
            val headersReference = (block.headersSource as? QuarkdownTableByRows.HeadersSource.Reference)?.name

            val dialog = TableEditorDialog(project)
            dialog.loadTableByRows(table, headersReference, block.indent)
            dialog.setTarget(document, block.startOffset)
            if (!dialog.showAndGet()) return

            applyDialogResult(project, file, document, block.startOffset, "", dialog)
        }

        /**
         * Replaces the edited block with the dialog result. The block is re-resolved
         * against the current document text (checking both table kinds): its length —
         * or even its kind, after a live "Format Table" conversion — may differ from
         * what was captured when the dialog opened, so stored offsets may be stale.
         */
        private fun applyDialogResult(
            project: com.intellij.openapi.project.Project,
            file: com.intellij.psi.PsiFile,
            document: com.intellij.openapi.editor.Document,
            startOffset: Int,
            oldId: String,
            dialog: TableEditorDialog
        ) {
            val text = document.immutableCharSequence
            var start = -1
            var end = -1
            QuarkdownTableModificationUtils.findTableBlocks(text)
                .firstOrNull { it.startOffset == startOffset }
                ?.let {
                    start = it.startOffset
                    end = if (it.labelLineStart >= 0) it.fullEndOffset else it.endOffset
                }
            if (start < 0) {
                QuarkdownTableByRows.findBlocks(text)
                    .firstOrNull { it.startOffset == startOffset }
                    ?.let {
                        start = it.startOffset
                        end = it.endOffset
                    }
            }
            if (start < 0) return

            val replacement = dialog.buildReplacementLines().joinToString("\n")
            WriteCommandAction.runWriteCommandAction(project) {
                document.replaceString(start, end, replacement)
            }
            // Renaming an existing id must also update every `.ref {oldId}` usage,
            // just like a refactor rename.
            val newId = dialog.getResultId()
            if (oldId != newId) {
                QuarkdownIdRenameUtils.renameRefUsagesAndNotify(project, file, oldId, newId)
            }
        }

        private fun indentOf(lines: List<String>): String {
            val first = lines.firstOrNull() ?: return ""
            val idx = first.indexOfFirst { it != ' ' && it != '\t' }
            return if (idx > 0) first.substring(0, idx) else ""
        }
    }

    companion object {
        private val separatorRegex = Regex("""^\|?\s*(?::?-{3,}:?)(?:\s*\|\s*(?::?-{3,}:?))*\s*\|?$""")
    }
}

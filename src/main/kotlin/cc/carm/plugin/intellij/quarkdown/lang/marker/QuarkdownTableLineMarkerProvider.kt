package cc.carm.plugin.intellij.quarkdown.lang.marker

import cc.carm.plugin.intellij.quarkdown.QuarkdownFileType
import cc.carm.plugin.intellij.quarkdown.QuarkdownIcons
import cc.carm.plugin.intellij.quarkdown.action.table.TableDialog
import cc.carm.plugin.intellij.quarkdown.lang.editor.QuarkdownTableModificationUtils
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
 * Clicking opens [TableDialog] to edit the table's label and ID (written as a
 * `"label" {#id}` line below the table, with the same indentation).
 */
class QuarkdownTableLineMarkerProvider : LineMarkerProvider {

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
        if (!isTableStartLine(text, lineStart)) return null

        return LineMarkerInfo(
            element,
            element.textRange,
            QuarkdownIcons.TABLE,
            { "Edit table properties" },
            TableGutterHandler(),
            GutterIconRenderer.Alignment.RIGHT,
            { "Edit table properties" }
        )
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

    inner class TableGutterHandler : GutterIconNavigationHandler<PsiElement> {

        override fun navigate(e: MouseEvent, elt: PsiElement) {
            val project = elt.project
            val file = elt.containingFile ?: return
            val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return

            val lineStart = findLineStart(document.immutableCharSequence, elt.textRange.startOffset)
            val block = QuarkdownTableModificationUtils.findTableBlocks(document.immutableCharSequence)
                .firstOrNull { it.startOffset == lineStart } ?: return

            val dialog = TableDialog(project)
            dialog.parseExistingTable(block.lines, block.labelLine)

            if (dialog.showAndGet()) {
                val replacement = dialog.buildTableLines()
                val start = block.startOffset
                val end = if (block.labelLineStart >= 0) block.fullEndOffset else block.endOffset
                WriteCommandAction.runWriteCommandAction(project) {
                    document.replaceString(start, end, replacement.joinToString("\n"))
                }
            }
        }
    }

    companion object {
        private val separatorRegex = Regex("""^\|?\s*(?::?-{3,}:?)(?:\s*\|\s*(?::?-{3,}:?))*\s*\|?$""")
    }
}

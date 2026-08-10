package cc.carm.plugin.intellij.quarkdown.lang.marker

import cc.carm.plugin.intellij.quarkdown.QuarkdownBundle
import cc.carm.plugin.intellij.quarkdown.QuarkdownFileType
import cc.carm.plugin.intellij.quarkdown.QuarkdownIcons
import cc.carm.plugin.intellij.quarkdown.action.heading.HeadingDialog
import cc.carm.plugin.intellij.quarkdown.lang.heading.QuarkdownHeadingSyntax
import cc.carm.plugin.intellij.quarkdown.lang.lexer.QuarkdownTokenTypes
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import java.awt.event.MouseEvent

/**
 * Shows a heading icon in the gutter on every Quarkdown heading line:
 *
 *  - `# Title {#id}` … `###### Title {#id}` — on the heading line
 *
 * Clicking opens [HeadingDialog] to edit the heading level, content and cross-reference id.
 *
 * Anchored at the `HEADING_MARKER` leaf (the lexer emits it starting at the line start,
 * indentation included), so exactly one marker per heading line.
 */
class QuarkdownHeadingLineMarkerProvider : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        val file = element.containingFile ?: return null
        if (file.fileType !is QuarkdownFileType) return null
        if (element.firstChild != null) return null
        // The lexer always starts a heading with a HEADING_MARKER leaf at the line start.
        if (element.node?.elementType != QuarkdownTokenTypes.HEADING_MARKER) return null

        val elementOffset = element.textRange.startOffset
        val lineStart = findLineStart(file.text, elementOffset)
        if (elementOffset != lineStart) return null

        val lineEnd = findLineEnd(file.text, elementOffset)
        val line = file.text.subSequence(lineStart, lineEnd).toString()
        if (QuarkdownHeadingSyntax.parseHeadingLine(line) == null) return null

        return LineMarkerInfo(
            element,
            element.textRange,
            QuarkdownIcons.HEADING_MARKER,
            { QuarkdownBundle.message("quarkdown.marker.heading.tooltip") },
            HeadingGutterHandler(lineStart),
            GutterIconRenderer.Alignment.RIGHT,
            { QuarkdownBundle.message("quarkdown.marker.heading.tooltip") }
        )
    }

    inner class HeadingGutterHandler(
        private val lineStart: Int
    ) : GutterIconNavigationHandler<PsiElement> {

        override fun navigate(e: MouseEvent, elt: PsiElement) {
            val project = elt.project
            val file = elt.containingFile ?: return
            val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return

            // Re-resolve the line bounds: the document may have been edited since the
            // marker was computed, so the captured offsets can be stale.
            val text = document.immutableCharSequence
            val anchor = lineStart.coerceIn(0, text.length)
            val start = findLineStart(text, anchor)
            val end = findLineEnd(text, start)
            val line = text.subSequence(start, end).toString()

            val info = QuarkdownHeadingSyntax.parseHeadingLine(line) ?: return
            val dialog = HeadingDialog(project)
            dialog.parseHeading(info)

            if (dialog.showAndGet()) {
                WriteCommandAction.runWriteCommandAction(project) {
                    val replacement = dialog.buildLine()
                    document.replaceString(start, end, replacement)
                }
            }
        }
    }

    private fun findLineStart(text: CharSequence, offset: Int): Int {
        var i = offset.coerceAtMost(text.length)
        while (i > 0 && text[i - 1] != '\n') i--
        return i
    }

    private fun findLineEnd(text: CharSequence, offset: Int): Int {
        var i = offset
        while (i < text.length && text[i] != '\n') i++
        return i
    }
}

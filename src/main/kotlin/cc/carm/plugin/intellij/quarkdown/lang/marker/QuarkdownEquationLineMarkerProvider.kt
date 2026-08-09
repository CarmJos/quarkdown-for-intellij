package cc.carm.plugin.intellij.quarkdown.lang.marker

import cc.carm.plugin.intellij.quarkdown.QuarkdownFileType
import cc.carm.plugin.intellij.quarkdown.QuarkdownIcons
import cc.carm.plugin.intellij.quarkdown.action.equation.EquationDialog
import cc.carm.plugin.intellij.quarkdown.lang.equation.QuarkdownEquationSyntax
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import java.awt.event.MouseEvent

/**
 * Shows an equation icon in the gutter on every Quarkdown equation:
 *
 *  - standalone `$ ... $ {#id}` equations — on the equation line
 *  - fenced `$$$ {#id}` equations — on the opening `$$$` line
 *
 * Clicking opens [EquationDialog] to edit the cross-reference id.
 *
 * Implemented through [collectSlowLineMarkers] so the document is scanned once per pass to
 * pair opening/closing `$$$` fences; a naive per-element scan would misclassify closing
 * fences.
 */
class QuarkdownEquationLineMarkerProvider : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null

    override fun collectSlowLineMarkers(
        elements: MutableList<out PsiElement>,
        result: MutableCollection<in LineMarkerInfo<*>>
    ) {
        if (elements.isEmpty()) return
        val file = elements.first().containingFile ?: return
        if (file.fileType !is QuarkdownFileType) return
        val text = file.text

        val fenceOpenOffsets = QuarkdownEquationSyntax.findEquationFenceOpenOffsets(text)

        for (element in elements) {
            if (element.firstChild != null) continue
            val elementOffset = element.textRange.startOffset
            val lineStart = findLineStart(text, elementOffset)
            val lineEnd = findLineEnd(text, elementOffset)
            val line = text.subSequence(lineStart, lineEnd).toString()

            // Fenced equation opening line — `$$$` is lexed as TEXT starting at the line
            // start (indentation included), so anchor at the line-start leaf.
            val fence = QuarkdownEquationSyntax.parseFenceEquationLine(line)
            if (fence != null) {
                if (elementOffset != lineStart) continue
                if (lineStart !in fenceOpenOffsets) continue
                result.add(createMarker(element, lineStart, lineEnd, QuarkdownEquationSyntax.Kind.FENCED))
                continue
            }

            // Standalone `$ ... $ {#id}` equation line — same anchor.
            val inline = QuarkdownEquationSyntax.parseInlineEquationLine(line)
            if (inline != null) {
                if (elementOffset != lineStart) continue
                result.add(createMarker(element, lineStart, lineEnd, QuarkdownEquationSyntax.Kind.INLINE))
            }
        }
    }

    private fun createMarker(
        element: PsiElement,
        lineStart: Int,
        lineEnd: Int,
        kind: QuarkdownEquationSyntax.Kind
    ): LineMarkerInfo<*> {
        return LineMarkerInfo(
            element,
            element.textRange,
            QuarkdownIcons.EQUATION_MARKER,
            { "Edit equation" },
            EquationGutterHandler(kind, lineStart, lineEnd),
            GutterIconRenderer.Alignment.RIGHT,
            { "Edit equation" }
        )
    }

    inner class EquationGutterHandler(
        private val kind: QuarkdownEquationSyntax.Kind,
        private val lineStart: Int,
        private val lineEnd: Int
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

            val dialog = EquationDialog(project, kind)
            when (kind) {
                QuarkdownEquationSyntax.Kind.INLINE -> {
                    val info = QuarkdownEquationSyntax.parseInlineEquationLine(line) ?: return
                    dialog.parseInline(info)
                }
                QuarkdownEquationSyntax.Kind.FENCED -> {
                    val info = QuarkdownEquationSyntax.parseFenceEquationLine(line) ?: return
                    dialog.parseFence(info)
                }
            }

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

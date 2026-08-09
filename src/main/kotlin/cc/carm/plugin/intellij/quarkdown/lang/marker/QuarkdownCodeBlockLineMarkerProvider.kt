package cc.carm.plugin.intellij.quarkdown.lang.marker

import cc.carm.plugin.intellij.quarkdown.QuarkdownFileType
import cc.carm.plugin.intellij.quarkdown.QuarkdownIcons
import cc.carm.plugin.intellij.quarkdown.action.code.CodeBlockDialog
import cc.carm.plugin.intellij.quarkdown.lang.codeblock.QuarkdownCodeBlockSyntax
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import java.awt.event.MouseEvent

/**
 * Shows a code-block icon in the gutter on the header line of every Quarkdown code block:
 *
 *  - fenced blocks (` ```python "caption" {#id}`) — on the opening fence line
 *  - `.code` function blocks (`.code lang:{python} caption:{…} ref:{…}`) — on the call line
 *
 * Clicking opens [CodeBlockDialog] to edit the language, caption and cross-reference id.
 *
 * Implemented through [collectSlowLineMarkers] so the document is scanned once per pass to
 * pair opening/closing fences; a naive per-element scan would misclassify closing fences
 * that are followed by another block.
 */
class QuarkdownCodeBlockLineMarkerProvider : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null

    override fun collectSlowLineMarkers(
        elements: MutableList<out PsiElement>,
        result: MutableCollection<in LineMarkerInfo<*>>
    ) {
        if (elements.isEmpty()) return
        val file = elements.first().containingFile ?: return
        if (file.fileType !is QuarkdownFileType) return
        val text = file.text

        val fenceOpenOffsets = QuarkdownCodeBlockSyntax.findFenceOpenOffsets(text)

        for (element in elements) {
            if (element.firstChild != null) continue
            val elementOffset = element.textRange.startOffset
            val lineStart = findLineStart(text, elementOffset)
            val lineEnd = findLineEnd(text, elementOffset)
            val line = text.subSequence(lineStart, lineEnd).toString()

            // Fenced code block opening line — the FENCED_CODE_START leaf starts the line.
            val fence = QuarkdownCodeBlockSyntax.parseFenceLine(line)
            if (fence != null) {
                if (elementOffset != lineStart) continue
                if (lineStart !in fenceOpenOffsets) continue
                result.add(createMarker(element, lineStart, lineEnd, QuarkdownCodeBlockSyntax.Kind.FENCED))
                continue
            }

            // `.code` function header line — anchor at the FUNCTION_DOT leaf.
            val codeFunction = QuarkdownCodeBlockSyntax.parseCodeFunctionLine(line)
            if (codeFunction != null) {
                val contentStart = lineStart + (line.length - line.trimStart().length)
                if (elementOffset != contentStart) continue
                result.add(createMarker(element, lineStart, lineEnd, QuarkdownCodeBlockSyntax.Kind.CODE_FUNCTION))
            }
        }
    }

    private fun createMarker(
        element: PsiElement,
        lineStart: Int,
        lineEnd: Int,
        kind: QuarkdownCodeBlockSyntax.Kind
    ): LineMarkerInfo<*> {
        return LineMarkerInfo(
            element,
            element.textRange,
            QuarkdownIcons.CODE_MARKER,
            { "Edit code block" },
            CodeBlockGutterHandler(kind, lineStart, lineEnd),
            GutterIconRenderer.Alignment.RIGHT,
            { "Edit code block" }
        )
    }

    inner class CodeBlockGutterHandler(
        private val kind: QuarkdownCodeBlockSyntax.Kind,
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

            val dialog = CodeBlockDialog(project, kind)
            when (kind) {
                QuarkdownCodeBlockSyntax.Kind.FENCED -> {
                    val info = QuarkdownCodeBlockSyntax.parseFenceLine(line) ?: return
                    dialog.parseFence(info)
                }

                QuarkdownCodeBlockSyntax.Kind.CODE_FUNCTION -> {
                    val info = QuarkdownCodeBlockSyntax.parseCodeFunctionLine(line) ?: return
                    dialog.parseCodeFunction(info)
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

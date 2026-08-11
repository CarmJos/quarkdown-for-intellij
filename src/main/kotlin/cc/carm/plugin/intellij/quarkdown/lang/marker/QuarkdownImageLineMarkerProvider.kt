package cc.carm.plugin.intellij.quarkdown.lang.marker

import cc.carm.plugin.intellij.quarkdown.QuarkdownBundle
import cc.carm.plugin.intellij.quarkdown.QuarkdownFileType
import cc.carm.plugin.intellij.quarkdown.QuarkdownIcons
import cc.carm.plugin.intellij.quarkdown.action.image.ImageDialog
import cc.carm.plugin.intellij.quarkdown.lang.reference.QuarkdownIdRenameUtils
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import java.awt.event.MouseEvent

/**
 * Shows an image icon in the gutter on lines containing Quarkdown image syntax.
 * Clicking opens ImageDialog in EDIT mode, preserving indentation.
 */
class QuarkdownImageLineMarkerProvider : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        val file = element.containingFile ?: return null
        if (file.fileType !is QuarkdownFileType) return null
        if (element.firstChild != null) return null

        val elementOffset = element.textRange.startOffset
        val lineStart = findLineStart(file.text, elementOffset)
        val line = getFullLine(file.text, elementOffset)
        if (!isImageLine(line)) return null

        // Only show one marker per line: match the element whose offset
        // corresponds to the '!' character of the image syntax.
        val bangIndex = line.indexOf('!')
        if (bangIndex < 0 || elementOffset != lineStart + bangIndex) return null

        val fileVirtualFile = file.virtualFile ?: return null
        val dir = fileVirtualFile.parent

        return LineMarkerInfo(
            element,
            element.textRange,
            QuarkdownIcons.IMAGE_MARKER,
            { QuarkdownBundle.message("quarkdown.marker.image.tooltip") },
            ImageGutterHandler(dir),
            GutterIconRenderer.Alignment.RIGHT,
            { QuarkdownBundle.message("quarkdown.marker.image.tooltip") }
        )
    }

    private fun isImageLine(line: String): Boolean {
        return Regex("""!\s*(?:\([^)]*\))?\s*(?:\[[^]]*\])?\s*\([^)]+\)""").containsMatchIn(line)
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

    inner class ImageGutterHandler(
        private val fileDir: VirtualFile?
    ) : GutterIconNavigationHandler<PsiElement> {

        override fun navigate(e: MouseEvent, elt: PsiElement) {
            val project = elt.project
            val file = elt.containingFile ?: return
            val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return
            if (FileEditorManager.getInstance(project).selectedTextEditor == null) return

            val elementOffset = elt.textRange.startOffset
            val text = document.immutableCharSequence
            val line = getFullLine(text, elementOffset)

            val dialog = ImageDialog(project, ImageDialog.Mode.EDIT)
            dialog.setCurrentFileDir(fileDir)
            dialog.parseExistingLine(line)
            // The anchor id field now holds the pre-edit value.
            val oldAnchorId = dialog.getAnchorIdForTest()

            if (dialog.showAndGet()) {
                val newAnchorId = dialog.getAnchorIdForTest()
                WriteCommandAction.runWriteCommandAction(project) {
                    val lineStart = findLineStart(text, elementOffset)
                    val lineEnd = findLineEnd(text, elementOffset)
                    document.replaceString(lineStart, lineEnd, dialog.buildImageSyntax())
                }
                if (oldAnchorId != newAnchorId) {
                    QuarkdownIdRenameUtils.renameRefUsagesAndNotify(project, file, oldAnchorId, newAnchorId)
                }
            }
        }
    }
}

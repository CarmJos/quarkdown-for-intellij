package cc.carm.plugin.intellij.quarkdown.lang.reference

import cc.carm.plugin.intellij.quarkdown.QuarkdownFileType
import com.intellij.find.actions.ShowUsagesAction
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement

/**
 * Shows the OFFICIAL IntelliJ Show Usages popup on Ctrl+Click over a Quarkdown
 * declaration (`{#id}` label or `.var { name }`).
 *
 * The platform's `CtrlMouseHandler2` navigates in `mouseReleased` and skips the event if
 * it is already consumed. Registering with `order="first"` and consuming the event here
 * prevents the platform from running its default GTD / Choose Declaration; we then invoke
 * the real [ShowUsagesAction.startFindUsages], which renders the same Java-style Show
 * Usages window (title, toolbar, file/line/preview list, preview source) through our
 * [QuarkdownFindUsagesHandlerFactory].
 */
class QuarkdownEditorMouseListener : EditorMouseListener {

    override fun mouseClicked(event: EditorMouseEvent) = Unit

    override fun mouseReleased(event: EditorMouseEvent) {
        if (event.isConsumed) return
        val mouseEvent = event.mouseEvent ?: return
        if (!mouseEvent.isControlDown) return
        if (mouseEvent.clickCount != 1) return

        val editor = event.editor ?: return
        val project = editor.project ?: return
        if (project.isDisposed) return
        val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return
        if (file.fileType != QuarkdownFileType.INSTANCE) return

        val offset = event.offset
        if (offset < 0 || offset > editor.document.textLength) return

        val declarationElement = declarationElementAt(file, offset) ?: return

        // Consume so the platform does not run its default GTD / Choose Declaration.
        event.consume()

        // Show the official Java-style Show Usages popup (EDT).
        val position = JBPopupFactory.getInstance().guessBestPopupLocation(editor)
        ShowUsagesAction.startFindUsages(declarationElement, position, editor)
    }

    /** Returns the declaration leaf at [offset] (label / var-decl), or null. */
    internal fun declarationElementAt(file: com.intellij.psi.PsiFile, offset: Int): PsiElement? {
        // Only for declaration anchors: label / var-decl.
        val anchor = QuarkdownReferenceAnchors.of(file).firstOrNull {
            TextRange(it.start, it.end).contains(offset)
        } ?: return null
        if (anchor.referenceType != "label" && anchor.referenceType != "var-decl") return null
        return file.findElementAt(anchor.start)
    }

    override fun mousePressed(event: EditorMouseEvent) = Unit
    override fun mouseEntered(event: EditorMouseEvent) = Unit
    override fun mouseExited(event: EditorMouseEvent) = Unit
}
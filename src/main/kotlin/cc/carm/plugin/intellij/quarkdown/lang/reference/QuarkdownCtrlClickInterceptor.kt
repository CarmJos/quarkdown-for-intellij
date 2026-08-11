package cc.carm.plugin.intellij.quarkdown.lang.reference

import cc.carm.plugin.intellij.quarkdown.QuarkdownFileType
import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import java.awt.AWTEvent
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities

/**
 * Suppresses the platform's Ctrl+Click `GotoDeclaration` action over Quarkdown declarations
 * so it never shows "Choose Declaration" or "Cannot find declaration to go to".
 *
 * The platform binds `GotoDeclaration` to the `ctrl button1` mouse shortcut. On Ctrl+Click
 * (`MOUSE_RELEASED`) [com.intellij.openapi.keymap.impl.IdeMouseEventDispatcher] fires that
 * action BEFORE any editor mouse listener runs. When the [QuarkdownGotoDeclarationHandler]
 * returns no targets for declarations, the platform falls back to the Symbol model, whose
 * `searchTargetVariants` may return empty — producing a "Cannot find declaration to go to"
 * hint instead of the Show Usages popup.
 *
 * [IdeEventQueue.EventDispatcher]s (registered via `IdeEventQueue.addDispatcher`) run
 * BEFORE the keymap mouse dispatcher (see `IdeEventQueue._dispatchEvent`). For a plain
 * Ctrl+Click over a Quarkdown `{#id}` / `.var { name }` declaration this dispatcher consumes
 * the AWT event, so the keymap shortcut is skipped — no popup at all. It returns `false` so
 * the event still reaches the editor, where [QuarkdownEditorMouseListener] shows the official
 * Show Usages popup.
 *
 * Ctrl+Click over a `.ref` usage, a file path or plain text is left untouched so those still
 * navigate through the standard `GotoDeclaration` action.
 */
class QuarkdownCtrlClickInterceptor : IdeEventQueue.EventDispatcher {

    override fun dispatch(e: AWTEvent): Boolean {
        if (e !is MouseEvent) return false
        // The keymap mouse shortcut for GotoDeclaration fires on MOUSE_RELEASED.
        if (e.id != MouseEvent.MOUSE_RELEASED) return false
        if (e.clickCount != 1) return false
        if (!e.isControlDown) return false
        if (e.isPopupTrigger) return false

        val editor = findEditor(e) ?: return false
        val project = editor.project ?: return false
        if (project.isDisposed) return false

        val offset = offsetAt(editor, e) ?: return false
        val isDeclaration = com.intellij.openapi.application.ReadAction.compute<Boolean, RuntimeException> {
            val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return@compute false
            if (file.fileType != QuarkdownFileType.INSTANCE) return@compute false
            isDeclarationOffset(file, offset)
        }
        if (!isDeclaration) return false

        // Swallow the click so the keymap "GotoDeclaration" shortcut does not fire.
        // Returning false keeps dispatching the event to the editor component, where
        // QuarkdownEditorMouseListener shows the official Show Usages popup.
        e.consume()
        return false
    }

    /** Returns the [Editor] whose content component (or a descendant) received [e]. */
    private fun findEditor(e: MouseEvent): Editor? {
        val component = e.component ?: return null
        for (editor in EditorFactory.getInstance().allEditors) {
            if (editor.isDisposed) continue
            val content = editor.contentComponent
            if (content === component || SwingUtilities.isDescendingFrom(component, content)) {
                return editor
            }
        }
        return null
    }

    /** Computes the document offset under the mouse event (content-component coordinates). */
    private fun offsetAt(editor: Editor, e: MouseEvent): Int? {
        val point = SwingUtilities.convertPoint(e.component, e.point, editor.contentComponent)
        if (point.x < 0 || point.y < 0) return null
        val offset = editor.logicalPositionToOffset(editor.xyToLogicalPosition(point))
        if (offset < 0 || offset > editor.document.textLength) return null
        return offset
    }

    private fun isDeclarationOffset(file: PsiFile, offset: Int): Boolean {
        val anchor = QuarkdownReferenceAnchors.of(file).firstOrNull {
            TextRange(it.start, it.end).contains(offset)
        } ?: return false
        return anchor.referenceType == "label" || anchor.referenceType == "var-decl"
    }
}

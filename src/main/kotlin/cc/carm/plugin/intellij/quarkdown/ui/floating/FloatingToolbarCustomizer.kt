package cc.carm.plugin.intellij.quarkdown.ui.floating

import cc.carm.plugin.intellij.quarkdown.QuarkdownFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Installs the [FormattingFloatingToolbar] on every Quarkdown text editor.
 *
 * The platform's `<textEditorCustomizer>` extension point (backed by the internal
 * `com.intellij.openapi.fileEditor.impl.text.TextEditorCustomizer`) is not available to
 * third-party plugins — using it fails the Marketplace verification
 * (`INTERNAL_API_USAGES`). It is therefore replaced by the **public** [EditorFactoryListener]
 * API: a single listener is registered once per application (guarded by [installed]) and
 * installs the toolbar when a main text editor for a `.qd` file is created. The toolbar is
 * disposed when the editor is released.
 *
 * Behaviour is identical to the previous implementation (mirroring the Markdown plugin's
 * `AddFloatingToolbarTextEditorCustomizer`): the floating toolbar appears above a text
 * selection in Quarkdown files only.
 */
class FloatingToolbarCustomizer private constructor() {

    companion object {
        private val installed = AtomicBoolean(false)

        /** Tracks the toolbar installed per live editor so it can be disposed on release. */
        private val toolbars = ConcurrentHashMap<Editor, FormattingFloatingToolbar>()

        /**
         * Registers the editor-factory listener (once) so every new Quarkdown editor gets
         * the floating formatting toolbar. Safe to call from any project startup.
         */
        fun install() {
            if (!installed.compareAndSet(false, true)) return
            val listener = object : EditorFactoryListener {
                override fun editorCreated(event: EditorFactoryEvent) {
                    val editor = event.editor
                    if (editor.editorKind != EditorKind.MAIN_EDITOR) return
                    val file = FileDocumentManager.getInstance().getFile(editor.document) ?: return
                    if (file.fileType !is QuarkdownFileType) return

                    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
                    val toolbar = FormattingFloatingToolbar(editor = editor, coroutineScope = scope)
                    toolbars[editor] = toolbar
                }

                override fun editorReleased(event: EditorFactoryEvent) {
                    toolbars.remove(event.editor)?.let(Disposer::dispose)
                }
            }
            EditorFactory.getInstance()
                .addEditorFactoryListener(listener, ApplicationManager.getApplication())
        }
    }
}

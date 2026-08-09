package cc.carm.plugin.intellij.quarkdown.ui.floating

import cc.carm.plugin.intellij.quarkdown.QuarkdownFileType
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.text.TextEditorCustomizer
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Installs the [FormattingFloatingToolbar] on every text editor that shows a Quarkdown
 * file (mirrors the Markdown plugin's AddFloatingToolbarTextEditorCustomizer).
 */
class FloatingToolbarCustomizer : TextEditorCustomizer {

    override fun customize(textEditor: TextEditor) {
        val file = textEditor.file
        if (file.fileType !is QuarkdownFileType) return

        // Own scope tied to the editor's disposal.
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        var registered = false
        try {
            val toolbar = FormattingFloatingToolbar(editor = textEditor.editor, coroutineScope = scope)
            registered = Disposer.tryRegister(textEditor, toolbar)
            if (registered) {
                Disposer.register(textEditor) { scope.cancel() }
            } else {
                Disposer.dispose(toolbar)
            }
        } finally {
            if (!registered) {
                scope.cancel()
            }
        }
    }
}

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
 *
 * The `TextEditorCustomizer` interface changed across IDE generations:
 *  - ≤ 2026.1 the abstract method was `customize(TextEditor)` (now deprecated);
 *  - ≥ 2026.2 the abstract method is `customize(TextEditor, CoroutineScope)`.
 *
 * Both overloads are provided so the class stays concrete (and the floating toolbar keeps
 * working) on every supported IDE generation. On 2026.2+ the JVM binds
 * `customize(TextEditor, CoroutineScope)` to the interface's abstract method even though it
 * cannot be declared with `override` while compiling against the older 2025.2 SDK.
 */
class FloatingToolbarCustomizer : TextEditorCustomizer {

    // Legacy overload, called by IDE generations ≤ 2026.1.
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun customize(textEditor: TextEditor) {
        // Own scope tied to the editor's disposal.
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        if (installToolbar(textEditor, scope)) {
            Disposer.register(textEditor) { scope.cancel() }
        } else {
            scope.cancel()
        }
    }

    // Abstract method on IDEA 2026.2+. The passed scope is owned by the platform and is
    // cancelled when the editor is closed, so no extra bookkeeping is required here.
    fun customize(textEditor: TextEditor, coroutineScope: CoroutineScope) {
        installToolbar(textEditor, coroutineScope)
    }

    /** Installs the toolbar and registers it for disposal with the editor. */
    private fun installToolbar(textEditor: TextEditor, scope: CoroutineScope): Boolean {
        val file = textEditor.file
        if (file.fileType !is QuarkdownFileType) return false

        val toolbar = FormattingFloatingToolbar(editor = textEditor.editor, coroutineScope = scope)
        return if (Disposer.tryRegister(textEditor, toolbar)) {
            true
        } else {
            Disposer.dispose(toolbar)
            false
        }
    }
}

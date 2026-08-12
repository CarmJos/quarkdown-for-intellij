package cc.carm.plugin.intellij.quarkdown.lang

import cc.carm.plugin.intellij.quarkdown.QuarkdownFileType
import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.editorActions.BackspaceHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile

/**
 * Re-triggers the completion popup when the user deletes a character with Backspace in a
 * Quarkdown file.
 *
 * The LSP auto-popup handler only fires on *typed* trigger characters (`.`, `:`, `{`),
 * so deleting a character (e.g. backspacing `e` in an already-completed `.doctype`) would
 * otherwise leave the popup closed. After the deletion we schedule auto-popup so the
 * LSP completion re-appears and offers the functions/parameters matching the new prefix.
 */
class QuarkdownBackspaceHandlerDelegate : BackspaceHandlerDelegate() {

    override fun beforeCharDeleted(c: Char, file: PsiFile, editor: Editor) {
        // no-op
    }

    override fun charDeleted(c: Char, file: PsiFile, editor: Editor): Boolean {
        if (file.fileType != QuarkdownFileType.INSTANCE) return false
        if (editor.isDisposed) return false
        // Only re-pop when the caret is inside a plausible completion prefix (a `.`-led
        // function name / path, or just after a named-argument colon). This keeps
        // backspace in plain prose from popping completion.
        val chars = editor.document.charsSequence
        val offset = editor.caretModel.offset
        if (offset > 0 && (offset >= chars.length || chars[offset].isWhitespace() || chars[offset] == '\n')) {
            var i = offset - 1
            while (i >= 0 && (chars[i].isLetterOrDigit() || chars[i] == '_')) i--
            if (i >= 0 && chars[i] == '.') {
                AutoPopupController.getInstance(file.project).scheduleAutoPopup(editor)
            }
        }
        return false
    }
}

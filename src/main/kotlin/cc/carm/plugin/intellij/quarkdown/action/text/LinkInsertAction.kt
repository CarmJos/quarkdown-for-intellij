package cc.carm.plugin.intellij.quarkdown.action.text

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.DumbAware

class LinkInsertAction : AnAction(), DumbAware {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        e.presentation.isEnabled = editor != null && !editor.isViewer
    }

    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return

        WriteCommandAction.runWriteCommandAction(e.project) {
            val primaryCaret = editor.caretModel.primaryCaret
            if (primaryCaret.hasSelection()) {
                // Wrap the selected text as the link label, then place the caret
                // inside the parentheses, ready to type the URL.
                val selected = primaryCaret.selectedText.orEmpty()
                val start = primaryCaret.selectionStart
                val end = primaryCaret.selectionEnd
                val wrapped = "[$selected]()"
                editor.document.replaceString(start, end, wrapped)
                primaryCaret.moveToOffset(start + wrapped.length - 1)
            } else {
                // No selection: insert `[]()` and place the caret inside the parens.
                val offset = editor.caretModel.offset
                editor.document.insertString(offset, "[]()")
                primaryCaret.moveToOffset(offset + 3)
            }
        }
    }
}
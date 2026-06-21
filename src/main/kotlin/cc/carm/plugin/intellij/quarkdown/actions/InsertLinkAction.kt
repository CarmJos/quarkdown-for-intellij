package cc.carm.plugin.intellij.quarkdown.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.DumbAware

class InsertLinkAction : AnAction(), DumbAware {

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
                val selected = primaryCaret.selectedText
                val start = primaryCaret.selectionStart
                val end = primaryCaret.selectionEnd
                val wrapped = "[$selected](url)"
                editor.document.replaceString(start, end, wrapped)
                primaryCaret.moveToOffset(start + wrapped.length)
            } else {
                editor.document.insertString(editor.caretModel.offset, "[text](url)")
                val pos = editor.caretModel.offset
                primaryCaret.setSelection(pos + 1, pos + 5)
            }
        }
    }
}

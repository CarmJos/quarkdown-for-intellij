package cc.carm.plugin.intellij.quarkdown.action.table

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.DumbAware

class InsertTableAction : AnAction(), DumbAware {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        e.presentation.isEnabled = editor != null && !editor.isViewer
    }

    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return

        WriteCommandAction.runWriteCommandAction(e.project) {
            val offset = editor.caretModel.offset
            editor.document.insertString(offset, TABLE_TEMPLATE)
            editor.caretModel.primaryCaret.moveToOffset(offset + TABLE_TEMPLATE.length)
        }
    }

    companion object {
        private val TABLE_TEMPLATE = "\n" +
                "| Header 1 | Header 2 | Header 3 |\n" +
                "|----------|----------|----------|\n" +
                "| Cell 1   | Cell 2   | Cell 3   |\n" +
                "| Cell 4   | Cell 5   | Cell 6   |\n"
    }
}
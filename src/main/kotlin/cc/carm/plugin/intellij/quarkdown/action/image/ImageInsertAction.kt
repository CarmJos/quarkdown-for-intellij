package cc.carm.plugin.intellij.quarkdown.action.image

import cc.carm.plugin.intellij.quarkdown.QuarkdownBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbAware

class ImageInsertAction : AnAction(
    QuarkdownBundle.message("quarkdown.action.image"),
    QuarkdownBundle.message("quarkdown.action.image.description"),
    null
), DumbAware {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        e.presentation.isEnabled = editor != null && !editor.isViewer
    }

    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return

        val dialog = ImageDialog(e.project, ImageDialog.Mode.INSERT)
        FileDocumentManager.getInstance().getFile(editor.document)?.let { dialog.setCurrentFileDir(it.parent) }

        if (!dialog.showAndGet()) return

        val syntax = dialog.buildImageSyntax()

        WriteCommandAction.runWriteCommandAction(e.project) {
            val primaryCaret = editor.caretModel.primaryCaret
            if (primaryCaret.hasSelection()) {
                val start = primaryCaret.selectionStart
                val end = primaryCaret.selectionEnd
                editor.document.replaceString(start, end, syntax)
                primaryCaret.moveToOffset(start + syntax.length)
            } else {
                editor.document.insertString(editor.caretModel.offset, syntax)
            }
        }
    }
}

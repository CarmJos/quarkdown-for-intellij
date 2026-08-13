package cc.carm.plugin.intellij.quarkdown.action.equation

import cc.carm.plugin.intellij.quarkdown.QuarkdownBundle
import cc.carm.plugin.intellij.quarkdown.lang.equation.QuarkdownEquationSyntax
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.DumbAware

/**
 * Inserts a new Quarkdown equation at the caret.
 *
 * Opens [EquationDialog] to enter the equation content and an optional `{#id}`.
 * The equation is inserted as a standalone `$ ... $ {#id}` line (inline kind).
 */
class InsertEquationAction : AnAction(
    QuarkdownBundle.message("quarkdown.action.insert.equation"),
    QuarkdownBundle.message("quarkdown.action.insert.equation.description"),
    null
), DumbAware {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        e.presentation.isEnabled = editor != null && !editor.isViewer
    }

    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val project = e.project

        val dialog = EquationDialog(project, QuarkdownEquationSyntax.Kind.INLINE)
        if (!dialog.showAndGet()) return
        val line = dialog.buildInsertLine()

        WriteCommandAction.runWriteCommandAction(project) {
            val document = editor.document
            val caret = editor.caretModel.primaryCaret
            val offset = caret.offset
            val lineStart = document.charsSequence.lastIndexOf('\n', offset - 1) + 1
            val insert = if (lineStart == offset) "$line\n" else "\n$line\n"
            document.insertString(offset, insert)
            caret.moveToOffset(offset + insert.length - 1)
        }
    }
}

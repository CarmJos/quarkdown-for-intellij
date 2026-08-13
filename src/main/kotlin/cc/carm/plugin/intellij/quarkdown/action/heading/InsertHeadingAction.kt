package cc.carm.plugin.intellij.quarkdown.action.heading

import cc.carm.plugin.intellij.quarkdown.QuarkdownBundle
import cc.carm.plugin.intellij.quarkdown.lang.heading.QuarkdownHeadingSyntax
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.DumbAware

/**
 * Inserts a new Quarkdown heading at the caret.
 *
 * Opens [HeadingDialog] to choose the level, content and optional `{#id}`. When a
 * selection exists the selected text is used as the heading content; otherwise a
 * fresh heading line is inserted before the caret.
 */
class InsertHeadingAction : AnAction(
    QuarkdownBundle.message("quarkdown.action.insert.heading"),
    QuarkdownBundle.message("quarkdown.action.insert.heading.description"),
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

        val dialog = HeadingDialog(project)
        val selected = editor.caretModel.primaryCaret.selectedText
        if (!selected.isNullOrBlank()) {
            // Pre-fill the content with the current selection so the user can
            // turn an existing line into a heading.
            val line = QuarkdownHeadingSyntax.buildHeadingInsert(1, selected.trim(), "")
            QuarkdownHeadingSyntax.parseHeadingLine(line)?.let { dialog.parseHeading(it) }
        }

        if (!dialog.showAndGet()) return
        val line = dialog.buildInsertLine()

        WriteCommandAction.runWriteCommandAction(project) {
            val document = editor.document
            val caret = editor.caretModel.primaryCaret
            if (caret.hasSelection()) {
                // Replace the selection with the heading line.
                val start = caret.selectionStart
                val end = caret.selectionEnd
                document.replaceString(start, end, line)
                caret.moveToOffset(start + line.length)
            } else {
                // Insert a new heading line before the current line.
                val offset = caret.offset
                val lineStart = document.charsSequence.lastIndexOf('\n', offset - 1) + 1
                val insert = if (lineStart == offset) "$line\n" else "\n$line\n"
                document.insertString(offset, insert)
                caret.moveToOffset(offset + insert.length - 1)
            }
        }
    }
}

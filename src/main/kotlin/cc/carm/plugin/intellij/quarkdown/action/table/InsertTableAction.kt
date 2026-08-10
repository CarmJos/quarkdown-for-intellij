package cc.carm.plugin.intellij.quarkdown.action.table

import cc.carm.plugin.intellij.quarkdown.QuarkdownBundle
import cc.carm.plugin.intellij.quarkdown.QuarkdownFileType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware

/**
 * Inserts an empty Quarkdown table. Opens a spreadsheet-style row/column picker first
 * (mirrors the IntelliJ Markdown plugin); the chosen grid is inserted as an empty table.
 */
class InsertTableAction : AnAction(
    QuarkdownBundle.message("quarkdown.action.table"),
    QuarkdownBundle.message("quarkdown.action.table.description"),
    null
), DumbAware {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val file = e.getData(CommonDataKeys.PSI_FILE)
        e.presentation.isEnabled =
            editor != null && !editor.isViewer && file != null && file.fileType is QuarkdownFileType
    }

    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val selector = TableGridSelector(selectedCallback = { rows, columns ->
            actuallyInsertTable(editor, e.project, rows, columns)
        })
        selector.show(editor)
    }

    private fun actuallyInsertTable(
        editor: Editor,
        project: com.intellij.openapi.project.Project?,
        rows: Int,
        columns: Int
    ) {
        // rows includes the header; the separator is added separately.
        WriteCommandAction.runWriteCommandAction(project) {
            val offset = editor.caretModel.offset
            val text = buildEmptyTable(rows, columns)
            val content = if (offset > 0 && editor.document.charsSequence[offset - 1] != '\n') "\n$text" else text
            editor.document.insertString(offset, content)
            editor.caretModel.primaryCaret.moveToOffset(offset + content.length)
        }
    }

    /** Builds an empty aligned table: header row, separator row, then [dataRows] empty rows. */
    private fun buildEmptyTable(dataRows: Int, columns: Int): String {
        val header = "|" + List(columns) { " Header ${it + 1} " }.joinToString("|") + "|"
        val separator = "|" + List(columns) { "----------" }.joinToString("|") + "|"
        val body = List(dataRows) {
            "|" + List(columns) { "           " }.joinToString("|") + "|"
        }
        return (listOf(header, separator) + body).joinToString("\n") + "\n"
    }
}
package cc.carm.plugin.intellij.quarkdown.action.text

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.DumbAware

/**
 * Action that inserts a multi-line fenced code block (` ```language\n\n ``` `)
 * at the caret position, then selects the language placeholder for immediate typing.
 *
 * The language portion supports code completion via [CodeBlockLanguageCompletionContributor].
 */
class CodeBlockInsertAction : AnAction(), DumbAware {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        e.presentation.isEnabled = editor != null && !editor.isViewer
    }

    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val project = e.project

        WriteCommandAction.runWriteCommandAction(project) {
            val document = editor.document
            val offset = editor.caretModel.offset

            // Determine if we need a leading newline (cursor not at line start)
            val lineStart = document.charsSequence.lastIndexOf('\n', offset - 1) + 1
            val needsLeadingNewline = offset != lineStart

            // Check if we need a trailing newline
            val atEnd = offset >= document.textLength - 1
            val needsTrailingNewline = !atEnd && document.charsSequence[offset] != '\n'

            val template = buildString {
                if (needsLeadingNewline) append('\n')
                append(CODE_FENCE)
                append(LANGUAGE_PLACEHOLDER)
                append('\n')
                append('\n')
                append(CODE_FENCE)
                if (needsTrailingNewline) append('\n')
            }

            document.insertString(offset, template)

            // Calculate and select the language placeholder
            val langStart = offset + (if (needsLeadingNewline) 1 else 0) + CODE_FENCE.length
            val langEnd = langStart + LANGUAGE_PLACEHOLDER.length

            editor.caretModel.primaryCaret.moveToOffset(langEnd)
            editor.caretModel.primaryCaret.setSelection(langStart, langEnd)
        }
    }

    companion object {
        private const val CODE_FENCE = "```"
        private const val LANGUAGE_PLACEHOLDER = "plaintext"
    }
}
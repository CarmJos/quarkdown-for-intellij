package cc.carm.plugin.intellij.quarkdown.lang

import cc.carm.plugin.intellij.quarkdown.QuarkdownFileType
import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

class QuarkdownTypedHandlerDelegate : TypedHandlerDelegate() {

    override fun checkAutoPopup(c: Char, project: Project, editor: Editor, file: PsiFile): Result {
        if (file.fileType == QuarkdownFileType.INSTANCE) {
            if (c == '.') {
                AutoPopupController.getInstance(project).scheduleAutoPopup(editor)
                return Result.DEFAULT
            }
        }
        return super.checkAutoPopup(c, project, editor, file)
    }

    override fun charTyped(c: Char, project: Project, editor: Editor, file: PsiFile): Result {
        if (file.fileType != QuarkdownFileType.INSTANCE) {
            return super.charTyped(c, project, editor, file)
        }

        if (c == '.') {
            AutoPopupController.getInstance(project).scheduleAutoPopup(editor)
        } else if (c == '"') {
            return handleDoubleQuote(editor)
        } else if (c == '`') {
            return handleBacktick(editor)
        }

        return super.charTyped(c, project, editor, file)
    }

    /**
     * Auto-close double quotes: typing `"` inserts `""` with cursor between them.
     * If the next character is already a closing `"`, skips past it instead.
     */
    private fun handleDoubleQuote(editor: Editor): Result {
        val document = editor.document
        val offset = editor.caretModel.offset
        val chars = document.charsSequence

        // If the next character is already a closing quote, skip past it
        if (offset < document.textLength && chars[offset] == '"') {
            editor.caretModel.moveToOffset(offset + 1)
            return Result.STOP
        }

        // Insert a closing quote and keep cursor between the pair
        document.insertString(offset, "\"")
        editor.caretModel.moveToOffset(offset)
        return Result.STOP
    }

    /**
     * Auto-close triple backtick code fences: when the user types `` ``` `` at the
     * start of a line, inserts a closing ` ``` ` fence on the next line and positions
     * the cursor between the fences.
     */
    private fun handleBacktick(editor: Editor): Result {
        val document = editor.document
        val offset = editor.caretModel.offset
        val chars = document.charsSequence

        // Check if we have typed three consecutive backticks
        if (offset >= 3) {
            val typed = chars.subSequence(offset - 3, offset).toString()
            if (typed == "```") {
                // Only auto-close at start of a line (or beginning of file)
                val atLineStart = offset == 3 || chars[offset - 4] == '\n'
                if (atLineStart) {
                    // Insert: newline + closing fence + newline
                    val insertion = "\n```\n"
                    document.insertString(offset, insertion)
                    // Position cursor at the start of the content line (between fences)
                    editor.caretModel.moveToOffset(offset)
                    return Result.STOP
                }
            }
        }

        return Result.DEFAULT
    }
}

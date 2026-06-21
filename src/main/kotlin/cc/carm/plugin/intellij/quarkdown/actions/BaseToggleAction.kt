package cc.carm.plugin.intellij.quarkdown.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAware

abstract class BaseToggleAction : AnAction(), DumbAware {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    abstract fun getWrapper(): String

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        e.presentation.isEnabled = editor != null && !editor.isViewer
    }

    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val document = editor.document

        WriteCommandAction.runWriteCommandAction(e.project) {
            for (caret in editor.caretModel.allCarets) {
                if (caret.hasSelection()) {
                    toggleSelection(document, caret)
                } else {
                    insertWrappers(document, caret)
                }
            }
        }
    }

    private fun toggleSelection(document: Document, caret: com.intellij.openapi.editor.Caret) {
        val start = caret.selectionStart
        val end = caret.selectionEnd
        val selected = document.text.substring(start, end)
        val wrapper = getWrapper()
        val wrapperLen = wrapper.length

        if (selected.startsWith(wrapper) && selected.endsWith(wrapper) && selected.length >= wrapperLen * 2) {
            val inner = selected.substring(wrapperLen, selected.length - wrapperLen)
            document.replaceString(start, end, inner)
            caret.removeSelection()
            caret.moveToOffset(start + inner.length)
        } else {
            val wrapped = wrapper + selected + wrapper
            document.replaceString(start, end, wrapped)
            caret.setSelection(start, start + wrapped.length)
        }
    }

    private fun insertWrappers(document: Document, caret: com.intellij.openapi.editor.Caret) {
        val wrapper = getWrapper()
        document.insertString(caret.offset, wrapper + wrapper)
        caret.moveCaretRelatively(wrapper.length, 0, false, false)
    }
}

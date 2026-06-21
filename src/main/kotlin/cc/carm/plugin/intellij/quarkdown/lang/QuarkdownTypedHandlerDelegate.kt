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
        if (file.fileType == QuarkdownFileType.INSTANCE) {
            System.err.println("!!! charTyped: '$c' in ${file.name} !!!")
            if (c == '.') {
                AutoPopupController.getInstance(project).scheduleAutoPopup(editor)
            }
        }
        return super.charTyped(c, project, editor, file)
    }
}

package cc.carm.plugin.intellij.quarkdown.actions

import cc.carm.plugin.intellij.quarkdown.QuarkdownIcons
import com.intellij.ide.actions.CreateFileFromTemplateAction
import com.intellij.ide.actions.CreateFileFromTemplateDialog
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory

class NewQuarkdownDocumentAction : CreateFileFromTemplateAction(
    "Quarkdown Document",
    "Create a new Quarkdown document file",
    QuarkdownIcons.FILE
), DumbAware {

    override fun buildDialog(
        project: Project, directory: PsiDirectory,
        builder: CreateFileFromTemplateDialog.Builder
    ) {
        builder.setTitle("New Quarkdown Document")
            .addKind("Empty Document", QuarkdownIcons.FILE, "Quarkdown Empty")
            .addKind("With Example", QuarkdownIcons.FILE, "Quarkdown Example")
    }

    override fun getActionName(
        directory: PsiDirectory,
        newName: String,
        templateName: String
    ): String =
        "Create Quarkdown Document: $newName"
}

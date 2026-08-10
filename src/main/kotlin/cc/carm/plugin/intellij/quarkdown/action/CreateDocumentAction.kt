package cc.carm.plugin.intellij.quarkdown.action

import cc.carm.plugin.intellij.quarkdown.QuarkdownBundle
import cc.carm.plugin.intellij.quarkdown.QuarkdownIcons
import com.intellij.ide.actions.CreateFileFromTemplateAction
import com.intellij.ide.actions.CreateFileFromTemplateDialog
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory

class CreateDocumentAction : CreateFileFromTemplateAction(
    QuarkdownBundle.message("quarkdown.action.create.document.text"),
    QuarkdownBundle.message("quarkdown.action.create.document.description"),
    QuarkdownIcons.FILE
), DumbAware {

    override fun buildDialog(
        project: Project, directory: PsiDirectory,
        builder: CreateFileFromTemplateDialog.Builder
    ) {
        builder.setTitle(QuarkdownBundle.message("quarkdown.action.create.document.title"))
            .addKind(
                QuarkdownBundle.message("quarkdown.action.create.document.kind.empty"),
                QuarkdownIcons.FILE,
                "Quarkdown Empty"
            )
            .addKind(
                QuarkdownBundle.message("quarkdown.action.create.document.kind.example"),
                QuarkdownIcons.FILE,
                "Quarkdown Example"
            )
    }

    override fun getActionName(
        directory: PsiDirectory,
        newName: String,
        templateName: String
    ): String =
        QuarkdownBundle.message("quarkdown.action.create.document.progress", newName)
}

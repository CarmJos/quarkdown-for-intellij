package cc.carm.plugin.intellij.quarkdown.lang.preview

import cc.carm.plugin.intellij.quarkdown.QuarkdownBundle
import cc.carm.plugin.intellij.quarkdown.lang.latex.QuarkdownEquationRegions
import cc.carm.plugin.intellij.quarkdown.lang.latex.QuarkdownFormulaPreviewSource
import cc.carm.plugin.intellij.quarkdown.ui.preview.QuarkdownFormulaPreviewDialog
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import java.io.File

/**
 * Renders a single formula with the Quarkdown CLI and shows the result in a dialog.
 *
 * The actual work is delegated to [QuarkdownFormulaRenderer] (which documents why the CLI is
 * used) and to [QuarkdownFormulaPreviewSource] (which builds the throwaway document).
 */
@Service(Service.Level.PROJECT)
class QuarkdownFormulaPreview(private val project: Project) {

    /** Compiles [region] of [documentText] and shows the rendered result. */
    fun preview(documentText: CharSequence, region: QuarkdownEquationRegions.Region) {
        render(documentText) { QuarkdownFormulaPreviewSource.build(documentText, region) }
    }

    /**
     * Compiles an arbitrary occurrence text — used by the editor dialog, whose content is not
     * necessarily what the document currently holds — and shows the rendered result.
     */
    fun preview(documentText: CharSequence, occurrenceText: String) {
        render(documentText) { QuarkdownFormulaPreviewSource.wrap(documentText, occurrenceText) }
    }

    private fun render(documentText: CharSequence, buildSource: () -> String) {
        val executable = QuarkdownCli.resolveExecutable(project)
        if (executable == null) {
            notify(
                QuarkdownBundle.message("quarkdown.math.preview.title"),
                QuarkdownBundle.message("quarkdown.preview.cli.not.found"),
                NotificationType.WARNING,
            )
            return
        }

        val source = buildSource()
        ProgressManager.getInstance().run(
            object : Task.Backgroundable(
                project,
                QuarkdownBundle.message("quarkdown.math.preview.progress"),
                true,
            ) {
                private var page: File? = null
                private var error: String? = null

                override fun run(indicator: ProgressIndicator) {
                    val result = QuarkdownFormulaRenderer.render(project, executable, source, indicator)
                    page = result.page
                    error = result.error
                }

                override fun onSuccess() {
                    val rendered = page
                    if (rendered != null) {
                        QuarkdownFormulaPreviewDialog.show(project, rendered)
                    } else {
                        notify(
                            QuarkdownBundle.message("quarkdown.math.preview.title"),
                            QuarkdownBundle.message(
                                "quarkdown.math.preview.failed",
                                error ?: QuarkdownBundle.message("quarkdown.math.preview.no.output"),
                            ),
                            NotificationType.ERROR,
                        )
                    }
                }
            }
        )
    }

    private fun notify(title: String, content: String, type: NotificationType) {
        ApplicationManager.getApplication().invokeLater {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Quarkdown")
                .createNotification(title, content, type)
                .notify(project)
        }
    }

    companion object {
        fun getInstance(project: Project): QuarkdownFormulaPreview =
            project.getService(QuarkdownFormulaPreview::class.java)
    }
}


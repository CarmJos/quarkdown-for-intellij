package cc.carm.plugin.intellij.quarkdown.action.preview

import cc.carm.plugin.intellij.quarkdown.QuarkdownBundle
import cc.carm.plugin.intellij.quarkdown.QuarkdownIcons
import cc.carm.plugin.intellij.quarkdown.lang.preview.QuarkdownPreviewService
import com.intellij.ide.actions.RevealFileAction
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

/**
 * Reveals the build output (the produced PDF) in the OS file manager. When a PDF
 * has been built it selects that file directly; otherwise it opens the output
 * directory so users don't have to hunt for the produced file manually.
 */
class QuarkdownShowOutputAction : AnAction(
    QuarkdownBundle.message("quarkdown.preview.show.output"),
    QuarkdownBundle.message("quarkdown.preview.show.output.description"),
    QuarkdownIcons.PREVIEW_SHOW_OUTPUT,
) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabled = project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = QuarkdownPreviewService.getInstance(project)

        // Reveal the produced PDF directly when one exists; otherwise fall back to
        // the output directory (created on demand).
        val outputFile = service.buildOutputFile()
        if (outputFile != null && outputFile.isFile) {
            RevealFileAction.openFile(outputFile)
            return
        }
        val outputDir = service.buildOutputDirectory()
        if (!outputDir.isDirectory) outputDir.mkdirs()
        RevealFileAction.openFile(outputDir)
    }
}

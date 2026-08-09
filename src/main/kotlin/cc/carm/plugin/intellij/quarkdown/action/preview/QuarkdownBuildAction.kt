package cc.carm.plugin.intellij.quarkdown.action.preview

import cc.carm.plugin.intellij.quarkdown.QuarkdownBundle
import cc.carm.plugin.intellij.quarkdown.QuarkdownIcons
import cc.carm.plugin.intellij.quarkdown.lang.preview.QuarkdownCli
import cc.carm.plugin.intellij.quarkdown.lang.preview.QuarkdownPreviewService
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

/**
 * Runs a one-shot Quarkdown build (PDF export) through the standard IDE *Run* tool
 * window: a run dialog executes
 * `quarkdown compile <file> --pdf --timeout 60 --allow all --out-name main -o <out> <extraArgs>`
 * with console output and a stop button.
 */
class QuarkdownBuildAction : AnAction(
    QuarkdownBundle.message("quarkdown.preview.build"),
    QuarkdownBundle.message("quarkdown.preview.build.description"),
    QuarkdownIcons.PREVIEW_BUILD,
) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isEnabled = false
            return
        }
        val service = QuarkdownPreviewService.getInstance(project)
        e.presentation.isEnabled = service.previewFile != null && QuarkdownCli.resolveExecutable(project) != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        QuarkdownPreviewService.getInstance(project).buildDocument()
    }
}


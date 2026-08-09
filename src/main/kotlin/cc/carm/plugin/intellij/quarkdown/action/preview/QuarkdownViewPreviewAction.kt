package cc.carm.plugin.intellij.quarkdown.action.preview

import cc.carm.plugin.intellij.quarkdown.QuarkdownBundle
import cc.carm.plugin.intellij.quarkdown.QuarkdownIcons
import cc.carm.plugin.intellij.quarkdown.lang.preview.QuarkdownPreviewService
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

/**
 * Views the running preview in an external browser (port-based preview).
 *
 * This button is only shown/enabled while the preview server is running — a quick
 * way to peek at the "background" preview outside the IDE.
 */
class QuarkdownViewPreviewAction : AnAction(
    QuarkdownBundle.message("quarkdown.preview.view"),
    QuarkdownBundle.message("quarkdown.preview.view.description"),
    QuarkdownIcons.PREVIEW_VIEW,
) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isEnabled = false
            e.presentation.isVisible = false
            return
        }
        val running = QuarkdownPreviewService.getInstance(project).state == QuarkdownPreviewService.State.RUNNING
        e.presentation.isEnabled = running
        e.presentation.isVisible = running
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        QuarkdownPreviewService.getInstance(project).openUrlInBrowser()
    }
}


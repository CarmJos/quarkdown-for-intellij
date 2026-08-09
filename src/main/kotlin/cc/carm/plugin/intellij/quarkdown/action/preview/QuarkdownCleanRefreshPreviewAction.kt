package cc.carm.plugin.intellij.quarkdown.action.preview

import cc.carm.plugin.intellij.quarkdown.QuarkdownBundle
import cc.carm.plugin.intellij.quarkdown.QuarkdownIcons
import cc.carm.plugin.intellij.quarkdown.lang.preview.QuarkdownPreviewService
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

/** Clears the preview output cache (`--clean`) and re-compiles the active document. */
class QuarkdownCleanRefreshPreviewAction : AnAction(
    QuarkdownBundle.message("quarkdown.preview.clean.refresh"),
    QuarkdownBundle.message("quarkdown.preview.clean.refresh.description"),
    QuarkdownIcons.PREVIEW_CLEAN,
) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabled = project != null && QuarkdownPreviewService.getInstance(project).previewFile != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        QuarkdownPreviewService.getInstance(project).cleanAndRefresh()
    }
}

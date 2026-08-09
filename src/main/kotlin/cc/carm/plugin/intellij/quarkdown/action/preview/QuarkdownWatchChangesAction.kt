package cc.carm.plugin.intellij.quarkdown.action.preview

import cc.carm.plugin.intellij.quarkdown.QuarkdownBundle
import cc.carm.plugin.intellij.quarkdown.QuarkdownIcons
import cc.carm.plugin.intellij.quarkdown.lang.preview.QuarkdownPreviewService
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction

/** Toggles watch mode: auto-refresh the preview on document changes. */
class QuarkdownWatchChangesAction : ToggleAction(
    QuarkdownBundle.message("quarkdown.preview.watch"),
    QuarkdownBundle.message("quarkdown.preview.watch.description"),
    QuarkdownIcons.PREVIEW_WATCH,
) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun isSelected(e: AnActionEvent): Boolean {
        val project = e.project ?: return true
        return QuarkdownPreviewService.getInstance(project).watchEnabled
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        val project = e.project ?: return
        QuarkdownPreviewService.getInstance(project).setWatchEnabled(state)
    }

    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.isEnabled = e.project != null
    }
}

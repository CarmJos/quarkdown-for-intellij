package cc.carm.plugin.intellij.quarkdown.action.preview

import cc.carm.plugin.intellij.quarkdown.QuarkdownBundle
import cc.carm.plugin.intellij.quarkdown.QuarkdownIcons
import cc.carm.plugin.intellij.quarkdown.lang.preview.QuarkdownPreviewService
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction

/**
 * Toggles the live preview on/off.
 *
 * Turning it on starts the Quarkdown preview server (`compile -p -w --server-port …`);
 * turning it off stops the background server.
 */
class QuarkdownPreviewToggleAction : ToggleAction(
    QuarkdownBundle.message("quarkdown.preview.start"),
    QuarkdownBundle.message("quarkdown.preview.start.description"),
    QuarkdownIcons.PREVIEW_PLAY,
) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun isSelected(e: AnActionEvent): Boolean {
        val project = e.project ?: return false
        return QuarkdownPreviewService.getInstance(project).state != QuarkdownPreviewService.State.STOPPED
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        val project = e.project ?: return
        val service = QuarkdownPreviewService.getInstance(project)
        if (state) service.startPreview() else service.stopPreview()
    }

    override fun update(e: AnActionEvent) {
        super.update(e)
        val project = e.project
        if (project == null) {
            e.presentation.isEnabled = false
            return
        }
        val service = QuarkdownPreviewService.getInstance(project)
        e.presentation.isEnabled = service.previewFile != null
        val running = service.state != QuarkdownPreviewService.State.STOPPED
        e.presentation.icon = if (running) QuarkdownIcons.PREVIEW_STOP else QuarkdownIcons.PREVIEW_PLAY
        e.presentation.text =
            QuarkdownBundle.message(if (running) "quarkdown.preview.stop" else "quarkdown.preview.start")
        e.presentation.description =
            QuarkdownBundle.message(if (running) "quarkdown.preview.stop.description" else "quarkdown.preview.start.description")
    }
}


package cc.carm.plugin.intellij.quarkdown.action.preview

import cc.carm.plugin.intellij.quarkdown.QuarkdownBundle
import cc.carm.plugin.intellij.quarkdown.QuarkdownIcons
import cc.carm.plugin.intellij.quarkdown.lang.preview.QuarkdownCli
import cc.carm.plugin.intellij.quarkdown.lang.preview.QuarkdownPreviewService
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

/** Opens the port-based live preview in an external browser (starting the server if needed). */
class QuarkdownOpenInBrowserAction : AnAction(
    QuarkdownBundle.message("quarkdown.preview.open.browser"),
    QuarkdownBundle.message("quarkdown.preview.open.browser.description"),
    QuarkdownIcons.PREVIEW_BROWSER,
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
        QuarkdownPreviewService.getInstance(project).openInBrowser()
    }
}

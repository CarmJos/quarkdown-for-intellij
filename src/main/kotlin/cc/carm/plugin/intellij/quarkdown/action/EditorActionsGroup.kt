package cc.carm.plugin.intellij.quarkdown.action

import cc.carm.plugin.intellij.quarkdown.QuarkdownBundle
import cc.carm.plugin.intellij.quarkdown.QuarkdownFileType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAware

/**
 * The "Insert" editor popup group. Only visible while the current editor shows a
 * Quarkdown (.qd) file.
 */
class EditorActionsGroup : DefaultActionGroup(
    QuarkdownBundle.message("quarkdown.editor.actions.text"),
    true
), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        val file = event.getData(CommonDataKeys.PSI_FILE)
        val editor = event.getData(CommonDataKeys.EDITOR)
        event.presentation.isEnabledAndVisible =
            editor != null && !editor.isViewer && file != null && file.fileType is QuarkdownFileType
        event.presentation.description = QuarkdownBundle.message("quarkdown.editor.actions.description")
    }
}

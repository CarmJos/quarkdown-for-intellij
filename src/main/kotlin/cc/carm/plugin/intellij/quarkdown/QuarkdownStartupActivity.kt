package cc.carm.plugin.intellij.quarkdown

import cc.carm.plugin.intellij.quarkdown.action.image.ImagePasteHandler
import cc.carm.plugin.intellij.quarkdown.lang.function.FunctionRegistry
import cc.carm.plugin.intellij.quarkdown.settings.QuarkdownPathDetector
import cc.carm.plugin.intellij.quarkdown.settings.QuarkdownSettings
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import java.util.concurrent.atomic.AtomicBoolean

class QuarkdownStartupActivity : ProjectActivity {

    private val logger = Logger.getInstance(QuarkdownStartupActivity::class.java)

    companion object {
        private val pasteHandlerInstalled = AtomicBoolean(false)
    }

    override suspend fun execute(project: Project) {
        // Completion/typed-handler extensions are declared declaratively in plugin.xml
        // and must NOT be registered programmatically (a manually constructed EP has a
        // null plugin descriptor, which crashes the IDE). No bootstrapping is needed here.

        installPasteHandlerIfNeeded()

        val settings = QuarkdownSettings.getInstance(project)
        val path = settings.state.quarkdownPath

        if (path.isNullOrEmpty()) {
            val detected = QuarkdownPathDetector.detect()
            if (detected != null) {
                logger.info("Auto-detected Quarkdown at: $detected")
                settings.state.quarkdownPath = detected
                project.service<FunctionRegistry>().refresh(detected)
            }
        } else {
            logger.info("Using existing Quarkdown path: $path")
            project.service<FunctionRegistry>().refresh(path)
        }
    }

    /**
     * Installs [ImagePasteHandler] as the first handler for the "EditorPaste" action.
     * Uses [EditorActionManager] to wrap the existing handler.
     */
    private fun installPasteHandlerIfNeeded() {
        if (!pasteHandlerInstalled.compareAndSet(false, true)) return
        try {
            val manager = EditorActionManager.getInstance()
            val actionId = IdeActions.ACTION_EDITOR_PASTE
            val originalHandler = manager.getActionHandler(actionId)
            manager.setActionHandler(actionId, ImagePasteHandler(originalHandler))
            logger.info("Installed ImagePasteHandler for $actionId")
        } catch (e: Exception) {
            logger.warn("Failed to install ImagePasteHandler", e)
            pasteHandlerInstalled.set(false)
        }
    }
}

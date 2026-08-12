package cc.carm.plugin.intellij.quarkdown

import cc.carm.plugin.intellij.quarkdown.action.image.ImagePasteHandler
import cc.carm.plugin.intellij.quarkdown.settings.QuarkdownPathDetector
import cc.carm.plugin.intellij.quarkdown.settings.QuarkdownSettings
import cc.carm.plugin.intellij.quarkdown.ui.floating.FloatingToolbarCustomizer
import com.intellij.openapi.actionSystem.IdeActions
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

        // Install the floating formatting toolbar via the public EditorFactoryListener API
        // (replaces the internal TextEditorCustomizer extension point).
        FloatingToolbarCustomizer.install()

        installPasteHandlerIfNeeded()

        val settings = QuarkdownSettings.getInstance(project)
        val path = settings.state.quarkdownPath

        // The official Quarkdown Language Server is launched lazily when a .qd file is
        // opened (see QuarkdownLspServerSupportProvider); the startup activity only
        // persists the detected installation path so the LSP descriptor can find it.
        if (path.isNullOrEmpty()) {
            val detected = QuarkdownPathDetector.detect()
            if (detected != null) {
                logger.info("Auto-detected Quarkdown at: $detected")
                settings.state.quarkdownPath = detected
            }
        } else {
            logger.info("Using existing Quarkdown path: $path")
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

package cc.carm.plugin.intellij.quarkdown

import cc.carm.plugin.intellij.quarkdown.lang.completion.QuarkdownCompletionRegistrar
import cc.carm.plugin.intellij.quarkdown.lang.function.FunctionRegistry
import cc.carm.plugin.intellij.quarkdown.settings.QuarkdownPathDetector
import cc.carm.plugin.intellij.quarkdown.settings.QuarkdownSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class QuarkdownStartupActivity : ProjectActivity {

    private val logger = Logger.getInstance(QuarkdownStartupActivity::class.java)

    override suspend fun execute(project: Project) {
        ApplicationManager.getApplication()
            .getService(QuarkdownCompletionRegistrar::class.java)

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
}

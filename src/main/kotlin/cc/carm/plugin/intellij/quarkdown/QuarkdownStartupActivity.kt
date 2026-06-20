package cc.carm.plugin.intellij.quarkdown

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import cc.carm.plugin.intellij.quarkdown.settings.QuarkdownPathDetector
import cc.carm.plugin.intellij.quarkdown.settings.QuarkdownSettings

class QuarkdownStartupActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        val settings = QuarkdownSettings.getInstance(project)
        val path = settings.state.quarkdownPath
        if (path.isNullOrEmpty()) {
            val detected = QuarkdownPathDetector.detect()
            if (detected != null) {
                settings.state.quarkdownPath = detected
            }
        }
    }
}

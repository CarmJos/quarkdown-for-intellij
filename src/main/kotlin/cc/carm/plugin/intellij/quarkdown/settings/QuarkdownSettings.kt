package cc.carm.plugin.intellij.quarkdown.settings

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
@State(
    name = "QuarkdownSettings",
    storages = [Storage("quarkdown.xml")]
)
class QuarkdownSettings : SimplePersistentStateComponent<QuarkdownSettings.State>(State()) {

    class State : BaseState() {
        var quarkdownPath by string()
        var compileCliArgs by string()
        var outputDirectory by string("quarkdown-output")
        var previewBrowser by string()
        var previewPort by property(8989)
        var previewCliArgs by string()
        var watchChanges by property(true)
        var autoSavePreviewFiles by property(true)
    }

    companion object {
        fun getInstance(project: Project): QuarkdownSettings = project.getService(QuarkdownSettings::class.java)
    }
}

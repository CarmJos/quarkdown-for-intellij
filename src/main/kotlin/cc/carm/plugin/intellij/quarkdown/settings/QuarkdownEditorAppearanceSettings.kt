package cc.carm.plugin.intellij.quarkdown.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project

/**
 * Settings for Quarkdown editor appearance.
 * Stores font family, size, line height, line wrap,
 * and per-component styling options.
 */
@State(
    name = "QuarkdownEditorAppearanceSettings",
    storages = [Storage("quarkdown-editor-appearance.xml")]
)
@Service(Service.Level.APP)
class QuarkdownEditorAppearanceSettings : SimplePersistentStateComponent<QuarkdownEditorAppearanceSettings.State>(State()) {

    class State : BaseState() {
        // Font settings
        var fontFamily by string("Default")
        var fontSize by property(14)
        var lineHeight by property(100) // percentage

        // Editor behavior
        var enableLineWrap by property(false)
        var showLineNumbers by property(false)

        // Per-component styling
        var headingBold by property(true)
        var headingItalic by property(false)
        var codeBlockBackground by property(true)
        var codeBlockBorder by property(true)
        var tableBorder by property(true)
        var tableStriped by property(false)
    }

    companion object {
        fun getInstance(): QuarkdownEditorAppearanceSettings {
            return ApplicationManager.getApplication().getService(QuarkdownEditorAppearanceSettings::class.java)
        }
    }
}

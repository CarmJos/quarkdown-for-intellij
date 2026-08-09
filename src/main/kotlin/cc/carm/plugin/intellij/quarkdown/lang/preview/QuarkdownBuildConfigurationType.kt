package cc.carm.plugin.intellij.quarkdown.lang.preview

import cc.carm.plugin.intellij.quarkdown.QuarkdownBundle
import cc.carm.plugin.intellij.quarkdown.QuarkdownIcons
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.project.Project

/**
 * Run configuration type for "Quarkdown Build".
 *
 * The plugin registers this type so the *Run* tool window can display and re-run a
 * Quarkdown compilation (PDF export) as a standard run configuration.
 */
class QuarkdownBuildConfigurationType : ConfigurationTypeBase(
    ID,
    QuarkdownBundle.message("quarkdown.preview.build.configuration.name"),
    QuarkdownBundle.message("quarkdown.preview.build.configuration.description"),
    QuarkdownIcons.PREVIEW_BUILD,
) {

    init {
        addFactory(object : ConfigurationFactory(this) {
            override fun createTemplateConfiguration(project: Project): RunConfiguration =
                QuarkdownBuildRunConfiguration(project, this, "Quarkdown Build")

            override fun getName(): String = "Quarkdown Build"
            override fun getId(): String = "Quarkdown.Build"
        })
    }

    companion object {
        const val ID = "Quarkdown.Build.Configuration"

        @JvmStatic
        fun getInstance(): QuarkdownBuildConfigurationType =
            ConfigurationTypeUtil.findConfigurationType(QuarkdownBuildConfigurationType::class.java)
    }
}


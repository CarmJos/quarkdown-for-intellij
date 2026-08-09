package cc.carm.plugin.intellij.quarkdown.lang.preview

import com.intellij.execution.Executor
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.LocatableConfigurationBase
import com.intellij.execution.configurations.LocatableRunConfigurationOptions
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessHandlerFactory
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project

/**
 * A minimal [RunConfiguration] that executes a Quarkdown CLI command line through the
 * standard IDE *Run* tool window (console output, progress, stop button).
 *
 * It is created on-the-fly by [QuarkdownBuildAction]; no persistent state is needed.
 */
class QuarkdownBuildRunConfiguration(
    project: Project,
    factory: com.intellij.execution.configurations.ConfigurationFactory,
    name: String,
) : LocatableConfigurationBase<QuarkdownBuildRunConfiguration.QuarkdownBuildOptions>(project, factory, name) {

    /** Options holder required by the platform (kept empty; state is transient). */
    class QuarkdownBuildOptions : LocatableRunConfigurationOptions()

    /** Full command line (executable + arguments) executed by the *Run* tool window. */
    @Volatile
    var commandLine: List<String> = emptyList()

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState {
        val line = GeneralCommandLine(commandLine)
        project.basePath?.let { line.setWorkDirectory(it) }
        return object : CommandLineState(environment) {
            override fun startProcess(): ProcessHandler =
                ProcessHandlerFactory.getInstance().createProcessHandler(line)
        }
    }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> =
        object : SettingsEditor<RunConfiguration>() {
            override fun resetEditorFrom(s: RunConfiguration) = Unit
            override fun applyEditorTo(s: RunConfiguration) = Unit
            override fun createEditor(): javax.swing.JComponent = com.intellij.ui.components.JBLabel("Quarkdown Build")
        }
}

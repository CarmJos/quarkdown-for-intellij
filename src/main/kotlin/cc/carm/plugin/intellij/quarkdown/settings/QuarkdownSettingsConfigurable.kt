package cc.carm.plugin.intellij.quarkdown.settings

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import java.nio.charset.Charset
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import cc.carm.plugin.intellij.quarkdown.lang.function.FunctionRegistry
import com.intellij.ui.components.JBTextField
import java.io.File
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.SwingUtilities

class QuarkdownSettingsConfigurable(private val project: Project) :
    BoundSearchableConfigurable(
        "Quarkdown",
        "Quarkdown",
        "Settings.Language.Quarkdown"
    ) {

    val settings: QuarkdownSettings
        get() = QuarkdownSettings.getInstance(project)

    private var checkButton: JButton? = null
    private var checkResultLabel: JLabel? = null
    private var cacheInfoLabel: JLabel? = null
    private var refreshCacheButton: JButton? = null
    private var homeField: TextFieldWithBrowseButton? = null

    override fun createPanel(): DialogPanel {
        return panel {
            group("Quarkdown Installation") {
                row("Quarkdown Home:") {
                    textFieldWithBrowseButton(
                        project = project,
                        fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
                    )
                        .bindText(
                            { settings.state.quarkdownPath.orEmpty() },
                            { settings.state.quarkdownPath = it }
                        )
                        .align(AlignX.FILL)
                        .applyToComponent {
                            homeField = this
                            textField.columns = 40
                            if (text.isNullOrEmpty()) {
                                val detected = QuarkdownPathDetector.detect()
                                if (detected != null) {
                                    (textField as? JBTextField)?.emptyText?.text = "Auto-detected: $detected"
                                } else {
                                    (textField as? JBTextField)?.emptyText?.text = "Quarkdown Home not found, please install or select manually"
                                }
                            }
                        }
                    button("Check") {
                        doVersionCheck()
                    }.applyToComponent {
                        checkButton = this
                    }
                }
                row {
                    label("").applyToComponent {
                        checkResultLabel = this
                        isVisible = false
                    }
                }
                row {
                    label("Function cache: " + project.service<FunctionRegistry>().getCacheInfo())
                        .applyToComponent { cacheInfoLabel = this }
                }
                row {
                    button("Installation Guide") {
                        BrowserUtil.browse("https://quarkdown.com/#install")
                    }
                    button("Refresh Cache") {
                        val path = homeField?.text.orEmpty()
                        if (QuarkdownPathDetector.isValidQuarkdownHome(path)) {
                            val registry = project.service<FunctionRegistry>()
                            refreshCacheButton?.isEnabled = false
                            cacheInfoLabel?.text = "Function cache: Refreshing..."
                            registry.refreshAsync(path, force = true) { _ ->
                                refreshCacheButton?.isEnabled = true
                                cacheInfoLabel?.text = "Function cache: " + registry.getCacheInfo()
                            }
                        }
                    }.applyToComponent {
                        refreshCacheButton = this
                        isEnabled = QuarkdownPathDetector.isValidQuarkdownHome(homeField?.text.orEmpty())
                    }
                }
            }

            group("Build") {
                row("CLI arguments (Run tool window):") {
                    textField()
                        .bindText(
                            { settings.state.compileCliArgs.orEmpty() },
                            { settings.state.compileCliArgs = it }
                        )
                        .align(AlignX.FILL)
                        .comment("Appended to: quarkdown compile <file> --pdf --timeout 60 --allow all --out-name main -o <out>")
                }
                row("Output directory:") {
                    textFieldWithBrowseButton(
                        project = project,
                        fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
                    )
                        .bindText(
                            { settings.state.outputDirectory.orEmpty() },
                            { settings.state.outputDirectory = it }
                        )
                        .align(AlignX.FILL)
                }
            }

            group("Preview") {
                row("CLI arguments (server mode):") {
                    textField()
                        .bindText(
                            { settings.state.previewCliArgs.orEmpty() },
                            { settings.state.previewCliArgs = it }
                        )
                        .align(AlignX.FILL)
                        .comment("Appended to: quarkdown compile <file> -p -w --server-port <port> --allow all -o <out>")
                }
                row("Browser:") {
                    textFieldWithBrowseButton(
                        project = project,
                        fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFileOrExecutableAppDescriptor()
                    )
                        .bindText(
                            { settings.state.previewBrowser.orEmpty() },
                            { settings.state.previewBrowser = it }
                        )
                        .align(AlignX.FILL)
                        .comment("Leave empty to use built-in preview panel")
                }
                row("Port:") {
                    intTextField().bindIntText(settings.state::previewPort)
                        .applyToComponent { columns = 6 }
                }
                row {
                    checkBox("Watch changes").bindSelected(settings.state::watchChanges)
                }
            }

            group("Documentation") {
                row {
                    link("Homepage") {
                        BrowserUtil.browse("https://quarkdown.com/")
                    }
                    link("Wiki") {
                        BrowserUtil.browse("https://quarkdown.com/wiki")
                    }
                    link("Code Doc") {
                        BrowserUtil.browse("https://quarkdown.com/docs/")
                    }
                }
            }
        }
    }

    private fun doVersionCheck() {
        var path = homeField?.text.orEmpty()
        if (path.isBlank()) {
            path = QuarkdownPathDetector.detect().orEmpty()
        }
        checkButton?.isEnabled = false
        checkButton?.text = "Checking..."

        Thread {
            try {
                val homeDir = File(path)
                if (!homeDir.isDirectory) {
                    SwingUtilities.invokeLater {
                        showCheckResult(false, "Directory does not exist: $path")
                        resetCheckButton()
                    }
                    return@Thread
                }

                val executable = resolveExecutable(homeDir)
                if (executable == null) {
                    SwingUtilities.invokeLater {
                        showCheckResult(false, "No quarkdown executable found in $path")
                        resetCheckButton()
                    }
                    return@Thread
                }

                val process = ProcessBuilder(executable.absolutePath, "--version")
                    .redirectErrorStream(true)
                    .start()

                val output = process.inputStream.bufferedReader(Charset.forName("UTF-8")).readText().trim()
                val exitCode = process.waitFor()

                SwingUtilities.invokeLater {
                    try {
                        if (exitCode == 0 && output.isNotBlank()) {
                            val version = extractVersion(output)
                            showCheckResult(true, "Quarkdown version: $version")
                        } else {
                            showCheckResult(false, "Command failed (exit=$exitCode): ${output.ifBlank { "<no output>" }}")
                        }
                    } finally {
                        resetCheckButton()
                    }
                }
            } catch (e: Throwable) {
                SwingUtilities.invokeLater {
                    try {
                        showCheckResult(false, "Error: ${e.message}")
                    } finally {
                        resetCheckButton()
                    }
                }
            }
        }.apply { isDaemon = true }.start()
    }

    private fun resolveExecutable(homeDir: File): File? {
        for (name in listOf("quarkdown.bat", "quarkdown.cmd", "quarkdown")) {
            val f = File(File(homeDir, "bin"), name)
            if (f.isFile) return f
        }
        for (name in listOf("quarkdown.bat", "quarkdown.cmd", "quarkdown")) {
            val f = File(homeDir, name)
            if (f.isFile) return f
        }
        return null
    }

    private fun extractVersion(output: String): String {
        return output.split("\\s+".toRegex()).lastOrNull() ?: output
    }

    private fun showCheckResult(success: Boolean, message: String) {
        checkResultLabel?.let {
            it.icon = if (success) AllIcons.General.InspectionsOK else AllIcons.General.BalloonError
            it.text = message
            it.isVisible = true
        }
    }

    private fun resetCheckButton() {
        checkButton?.isEnabled = true
        checkButton?.text = "Check"
    }

    override fun disposeUIResources() {
        checkResultLabel = null
        cacheInfoLabel = null
        homeField = null
        checkButton = null
        refreshCacheButton = null
        super.disposeUIResources()
    }
}

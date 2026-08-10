package cc.carm.plugin.intellij.quarkdown.settings

import cc.carm.plugin.intellij.quarkdown.QuarkdownBundle
import cc.carm.plugin.intellij.quarkdown.lang.function.FunctionRegistry
import cc.carm.plugin.intellij.quarkdown.lang.preview.QuarkdownCli
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import java.io.File
import java.nio.charset.Charset
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.SwingUtilities

class QuarkdownSettingsConfigurable(private val project: Project) :
    BoundSearchableConfigurable(
        QuarkdownBundle.message("quarkdown.settings.name"),
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
            group(QuarkdownBundle.message("quarkdown.settings.installation")) {
                row(QuarkdownBundle.message("quarkdown.settings.installation.path")) {
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
                                    (textField as? JBTextField)?.emptyText?.text =
                                        QuarkdownBundle.message("quarkdown.settings.installation.auto.detected", detected)
                                } else {
                                    (textField as? JBTextField)?.emptyText?.text =
                                        QuarkdownBundle.message("quarkdown.settings.installation.not.found.detail")
                                }
                            }
                        }
                    button(QuarkdownBundle.message("quarkdown.settings.installation.check")) {
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
                    label(
                        QuarkdownBundle.message(
                            "quarkdown.settings.installation.function.cache",
                            project.service<FunctionRegistry>().getCacheInfo()
                        )
                    )
                        .applyToComponent { cacheInfoLabel = this }
                }
                row {
                    button(QuarkdownBundle.message("quarkdown.settings.installation.help")) {
                        BrowserUtil.browse("https://quarkdown.com/#install")
                    }
                    button(QuarkdownBundle.message("quarkdown.settings.installation.refresh.cache")) {
                        val path = homeField?.text.orEmpty()
                        if (QuarkdownPathDetector.isValidQuarkdownHome(path)) {
                            val registry = project.service<FunctionRegistry>()
                            refreshCacheButton?.isEnabled = false
                            cacheInfoLabel?.text =
                                QuarkdownBundle.message("quarkdown.settings.installation.function.cache.refreshing")
                            registry.refreshAsync(path, force = true) { _ ->
                                refreshCacheButton?.isEnabled = true
                                cacheInfoLabel?.text = QuarkdownBundle.message(
                                    "quarkdown.settings.installation.function.cache",
                                    registry.getCacheInfo()
                                )
                            }
                        }
                    }.applyToComponent {
                        refreshCacheButton = this
                        isEnabled = QuarkdownPathDetector.isValidQuarkdownHome(homeField?.text.orEmpty())
                    }
                }
            }

            group(QuarkdownBundle.message("quarkdown.settings.compile")) {
                row(QuarkdownBundle.message("quarkdown.settings.compile.cli.args")) {
                    textField()
                        .bindText(
                            { settings.state.compileCliArgs.orEmpty() },
                            { settings.state.compileCliArgs = it }
                        )
                        .align(AlignX.FILL)
                        .comment(QuarkdownBundle.message("quarkdown.settings.compile.cli.args.comment"))
                }
                row(QuarkdownBundle.message("quarkdown.settings.compile.output.dir")) {
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

            group(QuarkdownBundle.message("quarkdown.settings.preview")) {
                row(QuarkdownBundle.message("quarkdown.settings.preview.cli.args")) {
                    textField()
                        .bindText(
                            { settings.state.previewCliArgs.orEmpty() },
                            { settings.state.previewCliArgs = it }
                        )
                        .align(AlignX.FILL)
                        .comment(QuarkdownBundle.message("quarkdown.settings.preview.cli.args.comment"))
                }
                row(QuarkdownBundle.message("quarkdown.settings.preview.browser")) {
                    textFieldWithBrowseButton(
                        project = project,
                        fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFileOrExecutableAppDescriptor()
                    )
                        .bindText(
                            { settings.state.previewBrowser.orEmpty() },
                            { settings.state.previewBrowser = it }
                        )
                        .align(AlignX.FILL)
                        .comment(QuarkdownBundle.message("quarkdown.settings.preview.browser.hint"))
                }
                row(QuarkdownBundle.message("quarkdown.settings.preview.port")) {
                    intTextField().bindIntText(settings.state::previewPort)
                        .applyToComponent { columns = 6 }
                }
                row {
                    checkBox(QuarkdownBundle.message("quarkdown.settings.preview.watch.changes"))
                        .bindSelected(settings.state::watchChanges)
                }
            }

            group(QuarkdownBundle.message("quarkdown.settings.documentation")) {
                row {
                    link(QuarkdownBundle.message("quarkdown.settings.documentation.homepage")) {
                        BrowserUtil.browse("https://quarkdown.com/")
                    }
                    link(QuarkdownBundle.message("quarkdown.settings.documentation.wiki")) {
                        BrowserUtil.browse("https://quarkdown.com/wiki")
                    }
                    link(QuarkdownBundle.message("quarkdown.settings.documentation.code.doc")) {
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
        checkButton?.text = QuarkdownBundle.message("quarkdown.settings.installation.checking")

        Thread {
            try {
                val homeDir = File(path)
                if (!homeDir.isDirectory) {
                    SwingUtilities.invokeLater {
                        showCheckResult(
                            false,
                            QuarkdownBundle.message("quarkdown.settings.installation.error.directory", path)
                        )
                        resetCheckButton()
                    }
                    return@Thread
                }

                val executable = resolveExecutable(homeDir)
                if (executable == null) {
                    SwingUtilities.invokeLater {
                        showCheckResult(
                            false,
                            QuarkdownBundle.message("quarkdown.settings.installation.error.executable", path)
                        )
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
                            showCheckResult(
                                true,
                                QuarkdownBundle.message("quarkdown.settings.installation.version", version)
                            )
                        } else {
                            val noOutput = QuarkdownBundle.message("quarkdown.settings.installation.error.no.output")
                            showCheckResult(
                                false,
                                QuarkdownBundle.message(
                                    "quarkdown.settings.installation.error.command",
                                    exitCode,
                                    output.ifBlank { noOutput }
                                )
                            )
                        }
                    } finally {
                        resetCheckButton()
                    }
                }
            } catch (e: Throwable) {
                SwingUtilities.invokeLater {
                    try {
                        showCheckResult(
                            false,
                            QuarkdownBundle.message(
                                "quarkdown.settings.installation.error.generic",
                                e.message ?: e.javaClass.simpleName
                            )
                        )
                    } finally {
                        resetCheckButton()
                    }
                }
            }
        }.apply { isDaemon = true }.start()
    }

    private fun resolveExecutable(homeDir: File): File? {
        for (name in QuarkdownCli.LAUNCHER_NAMES) {
            val f = File(File(homeDir, "bin"), name)
            if (f.isFile) return f
        }
        for (name in QuarkdownCli.LAUNCHER_NAMES) {
            val f = File(homeDir, name)
            if (f.isFile) return f
        }
        return null
    }

    private fun extractVersion(output: String): String =
        output.split("\\s+".toRegex()).lastOrNull() ?: output

    private fun showCheckResult(success: Boolean, message: String) {
        checkResultLabel?.let {
            it.icon = if (success) AllIcons.General.InspectionsOK else AllIcons.General.BalloonError
            it.text = message
            it.isVisible = true
        }
    }

    private fun resetCheckButton() {
        checkButton?.isEnabled = true
        checkButton?.text = QuarkdownBundle.message("quarkdown.settings.installation.check")
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

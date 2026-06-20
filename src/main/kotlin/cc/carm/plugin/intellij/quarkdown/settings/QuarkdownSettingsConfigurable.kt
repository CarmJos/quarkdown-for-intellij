package cc.carm.plugin.intellij.quarkdown.settings

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.Timer
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class QuarkdownSettingsConfigurable(private val project: Project) :
    BoundSearchableConfigurable(
        "Quarkdown",
        "Quarkdown",
        "Settings.Tools.Quarkdown"
    ) {

    val settings: QuarkdownSettings
        get() = QuarkdownSettings.getInstance(project)

    private var validationTimer: Timer? = null
    private val loadingIcon: Icon = AllIcons.Actions.Refresh
    private var statusLabel: JLabel? = null

    override fun createPanel(): DialogPanel {
        lateinit var homeField: TextFieldWithBrowseButton

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
                    textField.document.addDocumentListener(object : DocumentListener {
                        override fun insertUpdate(e: DocumentEvent) {
                            showLoading()
                            scheduleValidation(homeField.text)
                        }

                        override fun removeUpdate(e: DocumentEvent) {
                            showLoading()
                            scheduleValidation(homeField.text)
                        }

                        override fun changedUpdate(e: DocumentEvent) {
                            showLoading()
                            scheduleValidation(homeField.text)
                        }
                    })
                        }
                    label("").applyToComponent {
                        statusLabel = this
                        setImmediateStatus(this, homeField.text)
                    }
                }
                row {
                    button("Installation Guide") {
                        BrowserUtil.browse("https://quarkdown.com/#install")
                    }
                    button("Auto-detect") {
                        val path = QuarkdownPathDetector.detect()
                        if (path != null) {
                            settings.state.quarkdownPath = path
                            homeField.text = path
                        }
                        setImmediateStatus(statusLabel!!, homeField.text)
                    }
                }
            }

            group("Compile") {
                row("CLI arguments:") {
                    textField()
                        .bindText(
                            { settings.state.compileCliArgs.orEmpty() },
                            { settings.state.compileCliArgs = it }
                        )
                        .align(AlignX.FILL)
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
                row("CLI arguments:") {
                    textField()
                        .bindText(
                            { settings.state.previewCliArgs.orEmpty() },
                            { settings.state.previewCliArgs = it }
                        )
                        .align(AlignX.FILL)
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

    private fun showLoading() {
        statusLabel?.icon = loadingIcon
    }

    private fun scheduleValidation(path: String) {
        validationTimer?.stop()
        validationTimer = Timer(500) {
            doValidation(path)
        }.apply {
            isRepeats = false
            start()
        }
    }

    private fun doValidation(path: String) {
        val label = statusLabel ?: return
        label.icon = if (QuarkdownPathDetector.isValidQuarkdownHome(path)) {
            AllIcons.General.InspectionsOK
        } else {
            AllIcons.General.BalloonError
        }
    }

    private fun setImmediateStatus(label: JLabel, path: String) {
        validationTimer?.stop()
        label.icon = if (QuarkdownPathDetector.isValidQuarkdownHome(path)) {
            AllIcons.General.InspectionsOK
        } else {
            AllIcons.General.BalloonError
        }
    }

    override fun disposeUIResources() {
        validationTimer?.stop()
        statusLabel = null
        super.disposeUIResources()
    }
}

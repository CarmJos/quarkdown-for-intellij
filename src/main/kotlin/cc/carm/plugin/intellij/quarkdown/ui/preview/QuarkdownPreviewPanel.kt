package cc.carm.plugin.intellij.quarkdown.ui.preview

import cc.carm.plugin.intellij.quarkdown.QuarkdownBundle
import cc.carm.plugin.intellij.quarkdown.lang.preview.QuarkdownPreviewService
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandler
import org.cef.network.CefRequest
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.DefaultComboBoxModel
import javax.swing.DefaultListCellRenderer
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.SwingConstants

/**
 * The Quarkdown live-preview panel shown inside the `Quarkdown` tool window.
 *
 * Layout:
 *  - toolbar row: preview actions (start/stop, view, refresh, clean, build, browser,
 *    watch) on the left and a **file selector** combo box on the right,
 *  - an indeterminate progress bar (visible while the server starts or the page loads),
 *  - a [JBCefBrowser] rendering `http://localhost:<port>/`,
 *  - a status bar reporting the current state and file.
 */
class QuarkdownPreviewPanel(private val project: Project) : Disposable {

    private val logger = Logger.getInstance(QuarkdownPreviewPanel::class.java)
    private val service = QuarkdownPreviewService.getInstance(project)

    private val root = JPanel(BorderLayout())

    private val browser: JBCefBrowser? = if (JBCefApp.isSupported()) JBCefBrowser() else null

    private val progressBar = JProgressBar().apply {
        isIndeterminate = true
        isVisible = false
    }

    private val statusLabel = JBLabel().apply {
        border = JBUI.Borders.empty(2, 8)
        foreground = UIUtil.getContextHelpForeground()
    }

    private val fileCombo = ComboBox<FileOption>()

    private var toolbar: ActionToolbar? = null
    @Volatile
    private var browserLoading = false
    private var updatingCombo = false

    private val listener = object : QuarkdownPreviewService.Listener {
        override fun onStateChanged(state: QuarkdownPreviewService.State) {
            updateForState(state)
        }

        override fun onPreviewFileChanged(file: VirtualFile?) {
            updateForFile(file)
        }

        override fun onBusyChanged(busy: Boolean) {
            updateProgressBar()
        }

        override fun onServerOutput(line: String) {
            logger.debug("Preview server: $line")
        }
    }

    init {
        // Toolbar row + progress bar strip on top, browser in the middle, status at the bottom.
        val north = JPanel(BorderLayout())
        north.add(createToolbarRow(), BorderLayout.NORTH)
        north.add(progressBar, BorderLayout.SOUTH)
        root.add(north, BorderLayout.NORTH)
        root.add(createContent(), BorderLayout.CENTER)
        root.add(statusLabel, BorderLayout.SOUTH)

        val jcefBrowser = browser
        if (jcefBrowser != null) {
            val client = jcefBrowser.getJBCefClient()
            val cefBrowser = jcefBrowser.getCefBrowser()
            client.addLoadHandler(object : CefLoadHandler {
                override fun onLoadingStateChange(
                    browser: CefBrowser,
                    isLoading: Boolean,
                    canGoBack: Boolean,
                    canGoForward: Boolean,
                ) {
                    browserLoading = isLoading
                    ApplicationManager.getApplication().invokeLater { updateProgressBar() }
                }

                override fun onLoadStart(browser: CefBrowser, frame: CefFrame, transitionType: CefRequest.TransitionType) = Unit

                override fun onLoadEnd(browser: CefBrowser, frame: CefFrame, httpStatusCode: Int) = Unit

                override fun onLoadError(
                    browser: CefBrowser,
                    frame: CefFrame,
                    errorCode: CefLoadHandler.ErrorCode,
                    errorText: String,
                    failedUrl: String,
                ) {
                    if (failedUrl.contains("localhost")) {
                        ApplicationManager.getApplication().invokeLater {
                            if (service.state != QuarkdownPreviewService.State.RUNNING) {
                                statusLabel.text = QuarkdownBundle.message(
                                    "quarkdown.preview.status.load.error",
                                    errorText.ifBlank { errorCode.name },
                                )
                            }
                        }
                    }
                }
            }, cefBrowser)
        }

        service.addListener(listener)
        browser?.let { Disposer.register(this, it) }
        Disposer.register(this) { service.removeListener(listener) }

        project.messageBus.connect(this).subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: MutableList<out VFileEvent>) {
                    if (events.any { it.path.endsWith(".qd", ignoreCase = true) }) {
                        ApplicationManager.getApplication().invokeLater { refreshFileModel() }
                    }
                }
            }
        )

        refreshFileModel()
        updateForState(service.state)
        updateForFile(service.previewFile)
        updateProgressBar()
    }

    /** The top-level component to embed into the tool window. */
    val component: JComponent get() = root

    // ------------------------------------------------------------------
    // UI construction
    // ------------------------------------------------------------------

    private fun createToolbarRow(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.add(createToolbar(), BorderLayout.WEST)
        panel.add(createFileSelector(), BorderLayout.EAST)
        return panel
    }

    private fun createToolbar(): JComponent {
        val actionManager = ActionManager.getInstance()
        val group = actionManager.getAction(PREVIEW_ACTIONS_ID) as? ActionGroup
            ?: run {
                logger.warn("Preview actions group not found: $PREVIEW_ACTIONS_ID")
                return JPanel()
            }
        toolbar = actionManager.createActionToolbar("QuarkdownPreviewToolbar", group, true)
        toolbar?.setTargetComponent(root)
        return toolbar?.component ?: JPanel()
    }

    private fun createFileSelector(): JComponent {
        val label = JBLabel(QuarkdownBundle.message("quarkdown.preview.file.selector"))
        label.border = JBUI.Borders.empty(0, 4, 0, 4)

        fileCombo.apply {
            isSwingPopup = true
            renderer = FileCellRenderer()
            preferredSize = JBUI.size(300, 28)
            maximumSize = JBUI.size(340, 28)
            addActionListener {
                if (updatingCombo) return@addActionListener
                val option = selectedItem as? FileOption
                service.setSelectedFile(option?.file)
            }
        }
        refreshFileModel()

        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.empty(0, 4, 0, 8)
        panel.add(label, BorderLayout.WEST)
        panel.add(fileCombo, BorderLayout.CENTER)
        return panel
    }

    private fun createContent(): JComponent {
        val b = browser
        if (b != null) {
            val wrapper = JPanel(BorderLayout())
            wrapper.add(b.component, BorderLayout.CENTER)
            return wrapper
        }
        // JCEF unavailable (headless / not supported): explain how to proceed.
        return JBLabel(QuarkdownBundle.message("quarkdown.preview.status.jcef.unavailable"), SwingConstants.CENTER).apply {
            border = JBUI.Borders.empty(24)
        }
    }

    // ------------------------------------------------------------------
    // File selector
    // ------------------------------------------------------------------

    private fun refreshFileModel() {
        val selected = service.pinnedFile
        val files = service.projectQdFiles()
        val model = DefaultComboBoxModel<FileOption>()
        model.addElement(FileOption(null)) // auto
        files.forEach { model.addElement(FileOption(it)) }

        updatingCombo = true
        try {
            fileCombo.model = model
            val option = selected?.let { s -> files.firstOrNull { it.path == s.path }?.let { FileOption(it) } }
            fileCombo.selectedItem = option ?: model.getElementAt(0)
        } finally {
            updatingCombo = false
        }
        fileCombo.repaint()
    }

    private fun autoFileText(): String {
        val current = service.previewFile
        return if (current == null) {
            QuarkdownBundle.message("quarkdown.preview.file.selector.auto")
        } else {
            QuarkdownBundle.message("quarkdown.preview.file.selector.auto.file", current.name)
        }
    }

    private inner class FileCellRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>?,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean,
        ): Component {
            val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus) as JLabel
            val option = value as? FileOption
            component.text = if (option?.file == null) autoFileText() else option.file.name
            component.toolTipText = option?.file?.path
            return component
        }
    }

    private data class FileOption(val file: VirtualFile?)

    // ------------------------------------------------------------------
    // State rendering
    // ------------------------------------------------------------------

    private fun updateForState(state: QuarkdownPreviewService.State) {
        val text = when (state) {
            QuarkdownPreviewService.State.STOPPED ->
                if (service.previewFile == null) {
                    QuarkdownBundle.message("quarkdown.preview.status.no.file")
                } else {
                    QuarkdownBundle.message("quarkdown.preview.status.stopped")
                }
            QuarkdownPreviewService.State.STARTING ->
                QuarkdownBundle.message("quarkdown.preview.status.starting", service.port)
            QuarkdownPreviewService.State.RUNNING ->
                QuarkdownBundle.message("quarkdown.preview.status.running", service.port)
            QuarkdownPreviewService.State.ERROR ->
                service.lastError ?: QuarkdownBundle.message("quarkdown.preview.status.error.generic")
        }
        statusLabel.text = text

        val b = browser ?: return
        when (state) {
            QuarkdownPreviewService.State.STOPPED -> b.loadHTML(placeholderHtml(text))
            QuarkdownPreviewService.State.STARTING -> b.loadHTML(startingHtml(service.port))
            QuarkdownPreviewService.State.RUNNING -> b.loadURL(service.viewUrl())
            QuarkdownPreviewService.State.ERROR -> b.loadHTML(placeholderHtml(text))
        }
        toolbar?.updateActionsImmediately()
    }

    private fun updateForFile(file: VirtualFile?) {
        fileCombo.repaint()
        if (service.state == QuarkdownPreviewService.State.STOPPED && file != null) {
            statusLabel.text = QuarkdownBundle.message("quarkdown.preview.status.stopped")
        }
        toolbar?.updateActionsImmediately()
    }

    private fun updateProgressBar() {
        progressBar.isVisible = service.busy || browserLoading
    }

    // ------------------------------------------------------------------
    // HTML helpers
    // ------------------------------------------------------------------

    /** Renders a friendly centered placeholder inside the browser. */
    private fun placeholderHtml(message: String): String = """
        <!DOCTYPE html>
        <html lang="en">
        <head>
          <meta charset="UTF-8">
          <style>
            html, body { height: 100%; margin: 0; }
            body {
              display: flex; align-items: center; justify-content: center;
              font-family: system-ui, -apple-system, 'Segoe UI', sans-serif;
              background: transparent; color: #9aa0a6;
            }
            p { max-width: 30em; text-align: center; line-height: 1.6; }
          </style>
        </head>
        <body><p>${escapeHtml(message)}</p></body>
        </html>
    """.trimIndent()

    /** Renders a spinner + "starting…" page while the preview server boots. */
    private fun startingHtml(port: Int): String = """
        <!DOCTYPE html>
        <html lang="en">
        <head>
          <meta charset="UTF-8">
          <style>
            html, body { height: 100%; margin: 0; }
            body {
              display: flex; flex-direction: column; align-items: center; justify-content: center;
              font-family: system-ui, -apple-system, 'Segoe UI', sans-serif;
              background: transparent; color: #9aa0a6; gap: 14px;
            }
            .spinner {
              width: 28px; height: 28px;
              border: 3px solid rgba(154, 160, 166, 0.25);
              border-top-color: #4c8bf5;
              border-radius: 50%;
              animation: spin 0.9s linear infinite;
            }
            @keyframes spin { to { transform: rotate(360deg); } }
            p { margin: 0; text-align: center; line-height: 1.5; }
          </style>
        </head>
        <body>
          <div class="spinner"></div>
          <p>${escapeHtml(QuarkdownBundle.message("quarkdown.preview.status.starting", port))}</p>
        </body>
        </html>
    """.trimIndent()

    private fun escapeHtml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    override fun dispose() {
        service.removeListener(listener)
    }

    companion object {
        private const val PREVIEW_ACTIONS_ID = "Quarkdown.PreviewActions"
    }
}

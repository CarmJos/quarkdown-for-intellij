package cc.carm.plugin.intellij.quarkdown.ui.preview

import cc.carm.plugin.intellij.quarkdown.QuarkdownBundle
import cc.carm.plugin.intellij.quarkdown.QuarkdownFileType
import cc.carm.plugin.intellij.quarkdown.QuarkdownIcons
import cc.carm.plugin.intellij.quarkdown.lang.preview.QuarkdownPreviewService
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.ui.TextBrowseFolderListener
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandler
import org.cef.network.CefRequest
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.FlowLayout
import java.awt.Font
import java.io.File
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.SwingConstants
import javax.swing.Timer
import kotlin.math.roundToInt

/**
 * The Quarkdown live-preview panel shown inside the `Quarkdown` tool window.
 *
 * Layout:
 *  - toolbar row: preview actions (start/stop, view, refresh, clean, build, watch) on
 *    the left and a **file selector** (text field + browse button) on the right,
 *  - an indeterminate progress bar (visible while the server starts or the page loads),
 *  - a [JBCefBrowser] rendering `http://localhost:<port>/`,
 *  - a bottom bar: log icon button (left), status label (center) and compact zoom
 *    controls (right). Ctrl+wheel over the preview zooms it.
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

    /** Text field + browse button for the previewed file. Empty text = "auto". */
    private val fileField = TextFieldWithBrowseButton()

    private var toolbar: ActionToolbar? = null

    @Volatile
    private var browserLoading = false
    private var updatingFileField = false

    // Debounce applying the typed path so intermediate keystrokes don't restart the server.
    private val fileApplyDebounce = Timer(400) { applyFileFieldText() }.apply { isRepeats = false }

    // Zoom state (JBCefBrowser.setZoomLevel, 1.0 = 100%).
    @Volatile
    private var zoomLevel = 1.0

    private val zoomLabel = JBLabel("100%").apply {
        border = JBUI.Borders.empty(0, 3)
        foreground = UIUtil.getContextHelpForeground()
    }

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
    }

    init {
        // Toolbar row + progress bar strip on top, browser in the middle, bottom bar at the bottom.
        val north = JPanel(BorderLayout())
        north.add(createToolbarRow(), BorderLayout.NORTH)
        north.add(progressBar, BorderLayout.SOUTH)
        root.add(north, BorderLayout.NORTH)
        root.add(createContent(), BorderLayout.CENTER)
        root.add(createBottomBar(), BorderLayout.SOUTH)

        val jcefBrowser = browser
        if (jcefBrowser != null) {
            val client = jcefBrowser.jbCefClient
            val cefBrowser = jcefBrowser.cefBrowser
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

                override fun onLoadStart(
                    browser: CefBrowser,
                    frame: CefFrame,
                    transitionType: CefRequest.TransitionType
                ) = Unit

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

            // Ctrl + wheel over the preview zooms it (Chromium's built-in gesture is
            // disabled in JCEF, so we implement it ourselves).
            jcefBrowser.component.addMouseWheelListener { e ->
                if (e.isControlDown) {
                    zoomBy(-e.wheelRotation * 0.1)
                    e.consume()
                }
            }
        }

        service.addListener(listener)
        browser?.let { Disposer.register(this, it) }
        Disposer.register(this) { service.removeListener(listener) }

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
        toolbar?.targetComponent = root
        return toolbar?.component ?: JPanel()
    }

    private fun createFileSelector(): JComponent {
        val label = JBLabel(QuarkdownBundle.message("quarkdown.preview.file.selector"))
        label.border = JBUI.Borders.empty(0, 4)

        fileField.apply {
            textField.columns = 30
            preferredSize = JBUI.size(300, 28)
            maximumSize = JBUI.size(340, 28)
            (textField as? JBTextField)?.emptyText?.text = autoFileText()
            addBrowseFolderListener(createFileBrowseListener())
            addActionListener { applyFileFieldText() }
            textField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
                override fun insertUpdate(e: javax.swing.event.DocumentEvent) = scheduleFileApply()
                override fun removeUpdate(e: javax.swing.event.DocumentEvent) = scheduleFileApply()
                override fun changedUpdate(e: javax.swing.event.DocumentEvent) = scheduleFileApply()
            })
        }

        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.empty(0, 4, 0, 8)
        panel.add(label, BorderLayout.WEST)
        panel.add(fileField, BorderLayout.CENTER)
        return panel
    }

    /**
     * Browse-listener for the file selector. The file chooser opens at the currently
     * selected file's directory when one is set; otherwise it defaults to the project
     * root instead of starting from the computer's root directory.
     */
    private fun createFileBrowseListener(): TextBrowseFolderListener =
        object : TextBrowseFolderListener(
            FileChooserDescriptorFactory.createSingleFileDescriptor(QuarkdownFileType.INSTANCE)
                .withTitle(QuarkdownBundle.message("quarkdown.preview.file.selector.browse")),
            project,
        ) {
            override fun getInitialFile(): VirtualFile? {
                super.getInitialFile()?.let { return it }
                val basePath = this@QuarkdownPreviewPanel.project.basePath ?: return null
                return LocalFileSystem.getInstance().findFileByPath(basePath)
            }
        }

    private fun createContent(): JComponent {
        val b = browser
        if (b != null) {
            val wrapper = JPanel(BorderLayout())
            wrapper.add(b.component, BorderLayout.CENTER)
            return wrapper
        }
        // JCEF unavailable (headless / not supported): explain how to proceed.
        return JBLabel(
            QuarkdownBundle.message("quarkdown.preview.status.jcef.unavailable"),
            SwingConstants.CENTER
        ).apply {
            border = JBUI.Borders.empty(24)
        }
    }

    private fun createBottomBar(): JComponent {
        val bar = JPanel(BorderLayout())
        bar.border = JBUI.Borders.empty(2, 0)
        bar.add(createLogButton(), BorderLayout.WEST)
        bar.add(statusLabel, BorderLayout.CENTER)
        bar.add(createZoomControls(), BorderLayout.EAST)
        return bar
    }

    private fun createLogButton(): JButton = JButton(QuarkdownIcons.PREVIEW_LOG).apply {
        toolTipText = QuarkdownBundle.message("quarkdown.preview.view.log")
        isContentAreaFilled = false
        isFocusPainted = false
        border = JBUI.Borders.empty(2, 2, 2, 6)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        addActionListener { showFullLog() }
    }

    private fun createZoomControls(): JComponent {
        val panel = JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0))
        panel.add(createSeparator())
        panel.add(createZoomButton(QuarkdownIcons.PREVIEW_ZOOM_OUT, QuarkdownBundle.message("quarkdown.preview.zoom.out")) { zoomBy(-0.1) })
        panel.add(zoomLabel)
        panel.add(createZoomButton(QuarkdownIcons.PREVIEW_ZOOM_IN, QuarkdownBundle.message("quarkdown.preview.zoom.in")) { zoomBy(0.1) })
        panel.add(createZoomButton(QuarkdownIcons.PREVIEW_ZOOM_RESET, QuarkdownBundle.message("quarkdown.preview.zoom.reset")) { setZoom(1.0) })
        return panel
    }

    private fun createSeparator(): JComponent =
        JBLabel("|").apply {
            border = JBUI.Borders.empty(0, 3)
            foreground = UIUtil.getContextHelpForeground()
        }

    private fun createZoomButton(icon: Icon, tooltip: String, action: () -> Unit): JButton =
        JButton(icon).apply {
            this.toolTipText = tooltip
            isContentAreaFilled = false
            isFocusPainted = false
            border = JBUI.Borders.empty(2, 1)
            addActionListener { action() }
        }

    // ------------------------------------------------------------------
    // File selector
    // ------------------------------------------------------------------

    private fun scheduleFileApply() {
        if (updatingFileField) return
        fileApplyDebounce.restart()
    }

    private fun applyFileFieldText() {
        if (updatingFileField) return
        val text = fileField.text.trim()
        if (text.isEmpty()) {
            // Empty = "auto" (follow the active editor); the hint shows the current file.
            service.setSelectedFile(null)
            return
        }
        val file = resolveQdFile(text)
        if (file == null) {
            statusLabel.text = QuarkdownBundle.message("quarkdown.preview.file.selector.invalid", text)
            return
        }
        service.setSelectedFile(file)
    }

    private fun resolveQdFile(text: String): VirtualFile? {
        var f = File(text)
        if (!f.isAbsolute) {
            val base = project.basePath
            f = if (base != null) File(base, text) else f
        }
        if (!f.isFile) return null
        val vf = LocalFileSystem.getInstance().findFileByIoFile(f) ?: return null
        return vf.takeIf { it.fileType == QuarkdownFileType.INSTANCE }
    }

    private fun autoFileText(): String {
        val current = service.previewFile
        return if (current == null) {
            QuarkdownBundle.message("quarkdown.preview.file.selector.auto")
        } else {
            QuarkdownBundle.message("quarkdown.preview.file.selector.auto.file", current.name)
        }
    }

    // ------------------------------------------------------------------
    // Zoom
    // ------------------------------------------------------------------

    private fun zoomBy(delta: Double) = setZoom(zoomLevel + delta)

    private fun setZoom(level: Double) {
        zoomLevel = level.coerceIn(0.25, 4.0)
        browser?.setZoomLevel(zoomLevel)
        zoomLabel.text = "${(zoomLevel * 100).roundToInt()}%"
    }

    // ------------------------------------------------------------------
    // Log dialog
    // ------------------------------------------------------------------

    private fun showFullLog() {
        val log = service.fullLogText().ifBlank { QuarkdownBundle.message("quarkdown.preview.view.log.empty") }
        val textArea = JTextArea(log).apply {
            isEditable = false
            font = Font(Font.MONOSPACED, Font.PLAIN, 12)
            caretPosition = 0
        }
        val scroll = JScrollPane(textArea).apply { preferredSize = JBUI.size(720, 420) }
        val builder = DialogBuilder(project)
        builder.setTitle(QuarkdownBundle.message("quarkdown.preview.view.log.title"))
        builder.setCenterPanel(scroll)
        builder.addOkAction()
        builder.show()
    }

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
                QuarkdownBundle.message("quarkdown.preview.status.starting", service.port.toString())

            QuarkdownPreviewService.State.RUNNING ->
                QuarkdownBundle.message("quarkdown.preview.status.running", service.port.toString())

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
        toolbar?.updateActionsAsync()
    }

    private fun updateForFile(file: VirtualFile?) {
        updatingFileField = true
        try {
            val pinned = service.pinnedFile
            fileField.text = pinned?.path ?: ""
            (fileField.textField as? JBTextField)?.emptyText?.text = autoFileText()
        } finally {
            updatingFileField = false
        }
        if (service.state == QuarkdownPreviewService.State.STOPPED && file != null) {
            statusLabel.text = QuarkdownBundle.message("quarkdown.preview.status.stopped")
        }
        toolbar?.updateActionsAsync()
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
          <p>${escapeHtml(QuarkdownBundle.message("quarkdown.preview.status.starting", port.toString()))}</p>
        </body>
        </html>
    """.trimIndent()

    private fun escapeHtml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    override fun dispose() {
        fileApplyDebounce.stop()
        service.removeListener(listener)
    }

    companion object {
        private const val PREVIEW_ACTIONS_ID = "Quarkdown.PreviewActions"
    }
}

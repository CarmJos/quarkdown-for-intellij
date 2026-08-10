package cc.carm.plugin.intellij.quarkdown.lang.preview

import cc.carm.plugin.intellij.quarkdown.QuarkdownBundle
import cc.carm.plugin.intellij.quarkdown.QuarkdownFileType
import cc.carm.plugin.intellij.quarkdown.settings.QuarkdownSettings
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.io.File
import java.nio.charset.Charset
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Project-level coordinator for the live Quarkdown preview.
 *
 * The preview is a **single long-lived Quarkdown server process**:
 * ```
 * quarkdown compile <file> --preview [--watch] --server-port <port> --browser none -o <out> <extraArgs>
 * ```
 * - `--preview` starts the embedded web server; the JCEF panel (or an external browser)
 *   loads `http://localhost:<port>/`.
 * - `--watch` (enabled via the "Watch changes" setting) makes the CLI watch the source
 *   and recompile automatically; the served page hot-reloads in the browser.
 * - `--browser none` ensures the CLI never opens a browser by itself — opening the
 *   preview is always user-triggered.
 * - *Refresh* / *Clean &amp; Refresh* restart that process.
 *
 * Responsibilities:
 *  - Track the previewed `.qd` file: a pinned selection from the file selector, or the
 *    active editor file when no file is pinned ("auto").
 *  - Start / stop / restart the server and report lifecycle events to [Listener]s.
 *  - Keep the complete output log of the current run (for the "View Full Log" dialog).
 *  - Open the server URL in an external browser (port-based preview).
 *  - Run one-shot builds (`--pdf`) through the standard IDE *Run* tool window.
 */
@Service(Service.Level.PROJECT)
class QuarkdownPreviewService(private val project: Project) : Disposable {

    enum class State { STOPPED, STARTING, RUNNING, ERROR }

    /** UI listener notified about preview lifecycle events (always on the EDT). */
    interface Listener {
        fun onStateChanged(state: State) {}
        fun onPreviewFileChanged(file: VirtualFile?) {}
        fun onBusyChanged(busy: Boolean) {}
        fun onServerOutput(line: String) {}
    }

    private val logger = Logger.getInstance(QuarkdownPreviewService::class.java)
    private val listeners = CopyOnWriteArrayList<Listener>()

    /** File pinned via the file selector; `null` means "auto" (follow the active editor). */
    @Volatile
    var pinnedFile: VirtualFile? = null

    /** The `.qd` file currently selected in the editor (drives "auto" mode). */
    @Volatile
    private var activeEditorFile: VirtualFile? = null

    /** The effective preview target: pinned file, or the active editor file. */
    val previewFile: VirtualFile?
        get() = pinnedFile ?: activeEditorFile

    /** Lifecycle state of the preview server. */
    @Volatile
    var state: State = State.STOPPED
        private set

    /** Whether a (re)start / recompilation is in progress (drives the progress bar). */
    @Volatile
    var busy: Boolean = false
        private set

    /**
     * Watch mode. Always reads from the "Watch changes" setting so the panel toggle and
     * the Settings page stay in sync (single source of truth).
     */
    val watchEnabled: Boolean
        get() = QuarkdownSettings.getInstance(project).state.watchChanges

    /** Port of the preview web server. */
    val port: Int
        get() = QuarkdownSettings.getInstance(project).state.previewPort

    private val serverProcessLock = Any()
    private var serverProcess: Process? = null
    private var serverGeneration = 0

    /** Last N lines of server output, kept for error reporting. */
    private val recentOutput = ArrayDeque<String>()

    /** Every output line of the current server run (for the "View Full Log" dialog). */
    private val fullLog = ArrayDeque<String>()

    /** Detail text of the last [State.ERROR], surfaced to the panel status bar. */
    @Volatile
    private var errorDetail: String? = null

    /** The error detail of the last failure, or `null`. */
    val lastError: String?
        get() = errorDetail

    private var pendingOpenBrowser = false

    private val documentListener = object : DocumentListener {
        override fun documentChanged(event: com.intellij.openapi.editor.event.DocumentEvent) {
            val file = FileDocumentManager.getInstance().getFile(event.document) ?: return
            if (file == previewFile && state == State.RUNNING) {
                // `-w` inside the CLI handles the recompilation; just surface a status hint.
                onServerOutput(QuarkdownBundle.message("quarkdown.preview.status.file.changed", file.name))
            }
        }
    }

    init {

        EditorFactory.getInstance().eventMulticaster.addDocumentListener(documentListener, this)

        project.messageBus.connect(this).subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun selectionChanged(event: FileEditorManagerEvent) {
                    val file = event.newFile
                    if (file != null && file.fileType == QuarkdownFileType.INSTANCE) {
                        updateActiveEditorFile(file)
                    }
                }
            }
        )

        // Seed "auto" mode from the currently selected editor.
        val selected = FileEditorManager.getInstance(project).selectedEditor?.file
        if (selected != null && selected.fileType == QuarkdownFileType.INSTANCE) {
            activeEditorFile = selected
        }
    }

    // ----------------------------------------------------------------------
    // Public API (called from UI / actions)
    // ----------------------------------------------------------------------

    fun addListener(listener: Listener) = listeners.add(listener)

    fun removeListener(listener: Listener) = listeners.remove(listener)

    /** Starts the preview server for the current file. */
    fun startPreview() {
        if (state != State.STOPPED) return
        if (previewFile == null) return
        restartServer(clean = false)
    }

    /** Stops the preview server (background process). */
    fun stopPreview() {
        serverGeneration++
        pendingOpenBrowser = false
        executeOnPooledThread {
            stopCurrentProcess()
            if (!QuarkdownCli.waitForPortClosed(port, 8)) {
                logger.warn("Port $port is still busy after stopping the preview server")
            }
            setState(State.STOPPED)
            setBusy(false)
        }
    }

    /** Manually recompiles the active file by restarting the preview server. */
    fun refresh() {
        if (state == State.STOPPED) {
            startPreview()
        } else {
            restartServer(clean = false)
        }
    }

    /** Clears the output cache (`--clean`) and restarts the preview server. */
    fun cleanAndRefresh() {
        restartServer(clean = true)
    }

    /** Pins (or unpins with `null`) the previewed file. */
    fun setSelectedFile(file: VirtualFile?) {
        if (pinnedFile?.path == file?.path) return
        pinnedFile = file
        notifyPreviewFileChanged()
        if (state != State.STOPPED) {
            restartServer(clean = false)
        }
    }

    /** Enables/disables watch mode (persisted to the shared "Watch changes" setting). */
    fun setWatchEnabled(enabled: Boolean) {
        if (watchEnabled == enabled) return
        QuarkdownSettings.getInstance(project).state.watchChanges = enabled
        if (state != State.STOPPED) {
            restartServer(clean = false)
        }
    }

    /** URL served by the preview server. */
    fun viewUrl(): String = "http://localhost:${port}/"

    /** Complete output log of the current server run (for the "View Full Log" dialog). */
    fun fullLogText(): String = synchronized(recentOutput) { fullLog.joinToString("\n") }

    /**
     * Opens the port-based preview in an external browser. Starts the server first when
     * it is not running, and opens the URL as soon as it becomes ready.
     */
    fun openInBrowser() {
        val file = previewFile ?: return
        if (state == State.RUNNING) {
            openUrlInBrowser()
        } else {
            pendingOpenBrowser = true
            if (state == State.STOPPED) {
                restartServer(clean = false)
            }
        }
    }

    /** Opens the running preview URL in the external browser (used by the "view" button). */
    fun openUrlInBrowser() {
        if (state != State.RUNNING) return
        val url = viewUrl()
        val browserPath = QuarkdownSettings.getInstance(project).state.previewBrowser
        try {
            if (browserPath.isNullOrBlank()) {
                BrowserUtil.browse(url)
            } else {
                launchExternalBrowser(File(browserPath.trim()), url)
            }
        } catch (e: Exception) {
            logger.warn("Custom browser failed (${browserPath}), falling back to default", e)
            BrowserUtil.browse(url)
        }
    }

    /**
     * Runs a one-shot build (`--pdf`) through the standard IDE *Run* tool window:
     * the build command is shown in a run dialog and executed with console output.
     */
    fun buildDocument() {
        val file = previewFile ?: return
        val executable = QuarkdownCli.resolveExecutable(project)
        if (executable == null) {
            showCliMissingNotification()
            return
        }
        val settings = QuarkdownSettings.getInstance(project)
        val args = QuarkdownCli.buildRunArgs(
            executable,
            File(file.path),
            extraArgs = settings.state.compileCliArgs.orEmpty(),
        )
        ApplicationManager.getApplication().invokeLater {
            try {
                val runManager = RunManager.getInstance(project)
                val type = QuarkdownBuildConfigurationType.getInstance()
                val factory = type.configurationFactories.first()
                val configuration = runManager.createConfiguration(
                    QuarkdownBundle.message("quarkdown.preview.build.configuration.name") + ": " + file.name,
                    factory,
                )
                configuration.isTemporary = true
                val runConfiguration = configuration.configuration as QuarkdownBuildRunConfiguration
                runConfiguration.commandLine = args
                runManager.setTemporaryConfiguration(configuration)
                ProgramRunnerUtil.executeConfiguration(
                    project, configuration, DefaultRunExecutor.getRunExecutorInstance()
                )
            } catch (e: Throwable) {
                logger.warn("Failed to launch Quarkdown build", e)
                NotificationGroupManager.getInstance()
                    .getNotificationGroup("Quarkdown")
                    .createNotification(
                        QuarkdownBundle.message("quarkdown.preview.build.title"),
                        QuarkdownBundle.message("quarkdown.preview.build.failed", e.message ?: e.javaClass.simpleName),
                        NotificationType.ERROR,
                    )
                    .notify(project)
            }
        }
    }

    // ----------------------------------------------------------------------
    // Internal helpers
    // ----------------------------------------------------------------------

    private fun updateActiveEditorFile(file: VirtualFile) {
        val changed = activeEditorFile?.path != file.path
        activeEditorFile = file
        if (!changed) return
        if (pinnedFile == null) {
            notifyPreviewFileChanged()
            if (state != State.STOPPED) {
                restartServer(clean = false)
            }
        }
    }

    private fun restartServer(clean: Boolean) {
        val file = previewFile ?: return
        val generation = ++serverGeneration
        pendingOpenBrowser = pendingOpenBrowser && state != State.STOPPED
        setState(State.STARTING)
        setBusy(true)
        synchronized(recentOutput) { fullLog.clear() } // a new run starts with a fresh log

        executeOnPooledThread {
            try {
                stopCurrentProcess()
                if (!QuarkdownCli.waitForPortClosed(port, 8)) {
                    logger.warn("Port $port is still busy, forcing a short wait")
                    Thread.sleep(1500)
                }
                if (generation != serverGeneration) return@executeOnPooledThread

                val executable = QuarkdownCli.resolveExecutable(project)
                if (executable == null) {
                    if (generation == serverGeneration) {
                        setState(State.ERROR, QuarkdownBundle.message("quarkdown.preview.cli.not.found"))
                        setBusy(false)
                    }
                    return@executeOnPooledThread
                }

                val outputDir = resolveOutputDir(file)
                if (clean) outputDir.deleteRecursively()
                outputDir.mkdirs()

                val settings = QuarkdownSettings.getInstance(project)
                val args = QuarkdownCli.previewServerArgs(
                    executable = executable,
                    source = File(file.path),
                    port = port,
                    outputDir = outputDir,
                    watch = watchEnabled,
                    extraArgs = settings.state.previewCliArgs.orEmpty(),
                )
                logger.info("Starting preview server: ${args.joinToString(" ")}")

                val process = try {
                    ProcessBuilder(args)
                        .also { pb ->
                            pb.redirectErrorStream(true)
                            project.basePath?.let { pb.directory(File(it)) }
                        }
                        .start()
                } catch (e: Exception) {
                    logger.warn("Failed to start preview server", e)
                    if (generation == serverGeneration) {
                        setState(
                            State.ERROR,
                            QuarkdownBundle.message(
                                "quarkdown.preview.status.start.failed",
                                e.message ?: e.javaClass.simpleName
                            )
                        )
                        setBusy(false)
                    }
                    return@executeOnPooledThread
                }

                synchronized(serverProcessLock) {
                    if (generation != serverGeneration) {
                        QuarkdownCli.killProcessTree(process)
                        return@executeOnPooledThread
                    }
                    serverProcess = process
                }

                // Stream the server output (for debugging / error reporting).
                val reader = process.inputStream.bufferedReader(Charset.forName("UTF-8"))
                executeOnPooledThread {
                    try {
                        reader.forEachLine { line -> onServerOutput(line) }
                    } catch (e: Exception) {
                        logger.debug("Preview server output stream closed", e)
                    }
                }

                // Watch for unexpected termination.
                executeOnPooledThread {
                    val exitCode = process.waitFor()
                    synchronized(serverProcessLock) {
                        if (serverProcess === process) serverProcess = null
                    }
                    if (generation == serverGeneration) {
                        val lastError = synchronized(recentOutput) { recentOutput.lastOrNull() }
                        if (exitCode != 0) {
                            setState(
                                State.ERROR,
                                QuarkdownBundle.message(
                                    "quarkdown.preview.status.exited",
                                    exitCode,
                                    lastError ?: "",
                                ),
                            )
                        } else {
                            setState(State.STOPPED)
                        }
                        setBusy(false)
                    }
                }

                // Wait until the server is reachable.
                executeOnPooledThread {
                    val ready = QuarkdownCli.waitForPortReady(port)
                    if (generation != serverGeneration) return@executeOnPooledThread
                    setBusy(false)
                    if (ready) {
                        setState(State.RUNNING)
                        if (pendingOpenBrowser) {
                            pendingOpenBrowser = false
                            openUrlInBrowser()
                        }
                    } else {
                        val lastError = synchronized(recentOutput) { recentOutput.lastOrNull() }
                        setState(
                            State.ERROR,
                            QuarkdownBundle.message(
                                "quarkdown.preview.status.start.timeout",
                                port,
                                lastError ?: "",
                            ),
                        )
                    }
                }
            } catch (e: Exception) {
                logger.warn("Preview server restart failed", e)
                if (generation == serverGeneration) {
                    setState(
                        State.ERROR,
                        QuarkdownBundle.message(
                            "quarkdown.preview.status.internal.error",
                            e.message ?: e.javaClass.simpleName
                        )
                    )
                    setBusy(false)
                }
            }
        }
    }

    private fun stopCurrentProcess() {
        synchronized(serverProcessLock) {
            val process = serverProcess ?: return
            serverProcess = null
            QuarkdownCli.killProcessTree(process)
        }
    }

    private fun onServerOutput(line: String) {
        if (line.isBlank()) return
        synchronized(recentOutput) {
            recentOutput.addLast(line)
            while (recentOutput.size > 200) recentOutput.removeFirst()
            fullLog.addLast(line)
            while (fullLog.size > 10_000) fullLog.removeFirst()
        }
        ApplicationManager.getApplication().invokeLater {
            listeners.forEach { it.onServerOutput(line) }
        }
    }

    private fun setState(state: State, errorDetail: String? = null) {
        ApplicationManager.getApplication().invokeLater {
            this.state = state
            this.errorDetail = errorDetail
            listeners.forEach { it.onStateChanged(state) }
        }
    }

    private fun setBusy(busy: Boolean) {
        ApplicationManager.getApplication().invokeLater {
            this.busy = busy
            listeners.forEach { it.onBusyChanged(busy) }
        }
    }

    private fun notifyPreviewFileChanged() {
        ApplicationManager.getApplication().invokeLater {
            listeners.forEach { it.onPreviewFileChanged(previewFile) }
        }
    }

    private fun executeOnPooledThread(runnable: () -> Unit) {
        ApplicationManager.getApplication().executeOnPooledThread(runnable)
    }

    /**
     * Per-file output directory. Files are isolated in their own sub-folder so
     * `--clean` never wipes another document's output.
     */
    private fun resolveOutputDir(file: VirtualFile): File {
        val settings = QuarkdownSettings.getInstance(project)
        val configured = settings.state.outputDirectory
        val root = if (configured.isNullOrBlank()) {
            File(PathManager.getSystemPath(), "quarkdown/preview/${sanitize(project.name)}")
        } else {
            val f = File(configured)
            if (f.isAbsolute) f
            else File(project.basePath ?: System.getProperty("user.home"), configured)
        }
        return File(root, sanitize(file.nameWithoutExtension))
    }

    /** Launches a browser executable directly (custom browser path from settings). */
    private fun launchExternalBrowser(browser: File, url: String) {
        if (browser.isDirectory) {
            for (candidate in listOf("chrome", "chrome.exe", "msedge", "msedge.exe", "firefox", "firefox.exe")) {
                val f = File(browser, candidate)
                if (f.isFile) {
                    ProcessBuilder(f.absolutePath, url).start()
                    return
                }
            }
            throw IllegalArgumentException("No browser executable found in ${browser.path}")
        }
        ProcessBuilder(browser.absolutePath, url).start()
    }

    private fun showCliMissingNotification() {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Quarkdown")
            .createNotification(
                QuarkdownBundle.message("quarkdown.preview.build.title"),
                QuarkdownBundle.message("quarkdown.preview.cli.not.found"),
                NotificationType.WARNING,
            )
            .notify(project)
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("[^\\p{Alnum}._-]"), "-").ifBlank { "document" }

    override fun dispose() {
        stopPreview()
        listeners.clear()
    }

    companion object {

        fun getInstance(project: Project): QuarkdownPreviewService =
            project.getService(QuarkdownPreviewService::class.java)
    }
}

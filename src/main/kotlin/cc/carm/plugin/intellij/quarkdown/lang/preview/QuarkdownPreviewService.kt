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
import com.intellij.openapi.editor.Document
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
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

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

    /**
     * Whether the plugin auto-saves the previewed document while the preview is running
     * (persisted to the "Auto-save while previewing" setting).
     */
    val autoSaveEnabled: Boolean
        get() = QuarkdownSettings.getInstance(project).state.autoSavePreviewFiles

    /**
     * Port of the preview web server.
     *
     * Returns the *effective* port (the configured one, or the auto-shifted port when the
     * configured one was occupied at start time). Falls back to the configured value when
     * no server has started yet.
     */
    val port: Int
        get() = if (activePort > 0) activePort else configuredPort

    /** The port configured in Settings. */
    private val configuredPort: Int
        get() = QuarkdownSettings.getInstance(project).state.previewPort

    /** The port actually used by the running (or last) preview server; 0 = not started. */
    @Volatile
    private var activePort: Int = 0

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

    /**
     * Debounced auto-save for the previewed `.qd` document.
     *
     * IntelliJ only saves documents to disk when the application loses focus (or on idle),
     * which is why the user had to press Ctrl+S manually to update the preview. Unlike VS
     * Code there is no built-in "save every N ms" option, so the plugin implements it here:
     * while a preview is running in watch mode, the previewed document is written to disk
     * shortly after typing stops. The Quarkdown CLI's own `--watch` file watcher then picks
     * up the change and recompiles, hot-reloading the preview.
     */
    private val autoSaveExecutor: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "quarkdown-preview-autosave").apply { isDaemon = true }
        }

    /** The pending auto-save task (cancel + reschedule on every keystroke). */
    private val pendingAutoSave = AtomicReference<ScheduledFuture<*>>()

    private val documentListener = object : DocumentListener {
        override fun documentChanged(event: com.intellij.openapi.editor.event.DocumentEvent) {
            val file = FileDocumentManager.getInstance().getFile(event.document) ?: return
            if (file != previewFile) return
            if (state == State.RUNNING || state == State.STARTING) {
                // `-w` inside the CLI handles the recompilation; just surface a status hint.
                onServerOutput(QuarkdownBundle.message("quarkdown.preview.status.file.changed", file.name))
                scheduleAutoSave(event.document)
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

    /**
     * URL served by the preview server.
     *
     * In watch mode the page must be loaded through the `/live` endpoint: Quarkdown wraps it
     * in a live-preview wrapper that subscribes to `/reload` events, which the CLI broadcasts
     * after each recompilation. The plain `/` endpoint serves the raw HTML with no reload
     * mechanism, so it would never refresh.
     */
    fun viewUrl(): String =
        if (watchEnabled) "http://localhost:${port}/live" else "http://localhost:${port}/"

    /** Complete output log of the current server run (for the "View Full Log" dialog). */
    fun fullLogText(): String = synchronized(recentOutput) { fullLog.joinToString("\n") }

    /**
     * The directory where one-shot builds (PDF export) write their output.
     *
     * The Quarkdown CLI writes the produced PDF (and intermediate HTML) into the
     * configured output directory; this returns that directory so UI actions can
     * reveal it in the OS file manager.
     */
    fun buildOutputDirectory(): File = resolveBuildOutputDir()

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
            outputDir = resolveBuildOutputDir(),
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
                    configuration, DefaultRunExecutor.getRunExecutorInstance()
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

    /**
     * (Re)schedules the debounced auto-save of the previewed document. Typing keeps
     * cancelling the previous task, so the file is only written once the user pauses for
     * [AUTO_SAVE_DELAY_MS] milliseconds - matching the `afterDelay` semantics of VS Code's
     * autosave (a pause of ~100 ms, not a save every 100 ms).
     */
    private fun scheduleAutoSave(document: Document) {
        if (!autoSaveEnabled) return
        if (!watchEnabled) return
        pendingAutoSave.getAndSet(
            autoSaveExecutor.schedule({
                ApplicationManager.getApplication().invokeLater {
                    savePreviewDocument(document)
                }
            }, AUTO_SAVE_DELAY_MS, TimeUnit.MILLISECONDS)
        )?.cancel(false)
    }

    /** Writes the previewed document to disk so the CLI's `--watch` recompiles it. */
    private fun savePreviewDocument(document: Document) {
        val file = FileDocumentManager.getInstance().getFile(document) ?: return
        if (file != previewFile) return
        if (state != State.RUNNING && state != State.STARTING) return
        if (FileDocumentManager.getInstance().isDocumentUnsaved(document)) {
            logger.debug("Auto-saving previewed document ${file.name}")
            FileDocumentManager.getInstance().saveDocument(document)
        }
    }

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

                // Resolve an available port: keep the configured one when free, otherwise
                // shift upward until a free port is found (and notify the user).
                val resolvedPort = resolveAvailablePort()
                if (resolvedPort != configuredPort) {
                    activePort = resolvedPort
                    notifyPortShifted(configuredPort, resolvedPort)
                } else {
                    activePort = resolvedPort
                }
                if (!QuarkdownCli.waitForPortClosed(resolvedPort, 8)) {
                    logger.warn("Port $resolvedPort is still busy, forcing a short wait")
                    Thread.sleep(1500)
                }
                if (generation != serverGeneration) return@executeOnPooledThread

                val process = startServerProcess(file, clean, generation, resolvedPort) ?: return@executeOnPooledThread

                synchronized(serverProcessLock) {
                    if (generation != serverGeneration) {
                        QuarkdownCli.killProcessTree(process)
                        return@executeOnPooledThread
                    }
                    serverProcess = process
                }

                // Stream the server output (for debugging / error reporting).
                streamServerOutput(process)

                // Watch for unexpected termination.
                watchServerExit(process, generation)

                // Wait until the server is reachable.
                waitForServerReady(generation, resolvedPort)
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

    /**
     * Returns a free port to use for the preview server. Prefers the configured port;
     * when it is already occupied, scans upward until a free one is found.
     */
    private fun resolveAvailablePort(): Int {
        var candidate = configuredPort
        if (!QuarkdownCli.isPortReady(candidate)) return candidate
        // The configured port is busy — scan up to a safe upper bound.
        while (candidate < MAX_PORT && QuarkdownCli.isPortReady(candidate)) candidate++
        return candidate
    }

    /** Notifies the user that the configured port was occupied and a new one was chosen. */
    private fun notifyPortShifted(oldPort: Int, newPort: Int) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Quarkdown")
            .createNotification(
                QuarkdownBundle.message("quarkdown.preview.build.title"),
                QuarkdownBundle.message("quarkdown.preview.status.port.shifted", oldPort, newPort),
                NotificationType.WARNING,
            )
            .notify(project)
    }

    /**
     * Resolves the CLI, prepares the output directory and starts the preview server
     * process. Reports failure state and returns `null` when the server cannot start.
     */
    private fun startServerProcess(file: VirtualFile, clean: Boolean, generation: Int, serverPort: Int): Process? {
        val executable = QuarkdownCli.resolveExecutable(project)
        if (executable == null) {
            if (generation == serverGeneration) {
                setState(State.ERROR, QuarkdownBundle.message("quarkdown.preview.cli.not.found"))
                setBusy(false)
            }
            return null
        }

        val outputDir = resolveOutputDir(file)
        if (clean) outputDir.deleteRecursively()
        outputDir.mkdirs()

        val settings = QuarkdownSettings.getInstance(project)
        val args = QuarkdownCli.previewServerArgs(
            executable = executable,
            source = File(file.path),
            port = serverPort,
            outputDir = outputDir,
            watch = watchEnabled,
            extraArgs = settings.state.previewCliArgs.orEmpty(),
        )
        logger.info("Starting preview server: ${args.joinToString(" ")}")

        return try {
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
            null
        }
    }

    /** Streams the server output (for debugging / error reporting). */
    private fun streamServerOutput(process: Process) {
        val reader = process.inputStream.bufferedReader(Charset.forName("UTF-8"))
        executeOnPooledThread {
            try {
                reader.forEachLine { line -> onServerOutput(line) }
            } catch (_: Exception) {
                // Closing the stream is expected when the server process exits;
                // the exit-code watcher below reports unexpected termination.
            }
        }
    }

    /** Watches for unexpected termination of the server process. */
    private fun watchServerExit(process: Process, generation: Int) {
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
                            exitCode.toString(),
                            lastError ?: "",
                        ),
                    )
                } else {
                    setState(State.STOPPED)
                }
                setBusy(false)
            }
        }
    }

    /** Waits until the server is reachable and updates the state / opens the browser. */
    private fun waitForServerReady(generation: Int, serverPort: Int) {
        executeOnPooledThread {
            val ready = QuarkdownCli.waitForPortReady(serverPort)
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
                        serverPort.toString(),
                        lastError ?: "",
                    ),
                )
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
        val clean = stripAnsi(line)
        if (clean.isBlank()) return
        synchronized(recentOutput) {
            recentOutput.addLast(clean)
            while (recentOutput.size > 200) recentOutput.removeFirst()
            fullLog.addLast(clean)
            while (fullLog.size > 10_000) fullLog.removeFirst()
        }
        ApplicationManager.getApplication().invokeLater {
            listeners.forEach { it.onServerOutput(clean) }
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
    private fun resolveOutputDir(file: VirtualFile): File =
        File(resolveOutputRoot(), sanitize(file.nameWithoutExtension))

    /** Output root for one-shot builds (PDF export): the configured directory itself. */
    private fun resolveBuildOutputDir(): File = resolveOutputRoot()

    /** Resolves the configured output directory (relative paths are based on the project dir). */
    private fun resolveOutputRoot(): File {
        val settings = QuarkdownSettings.getInstance(project)
        val configured = settings.state.outputDirectory
        return if (configured.isNullOrBlank()) {
            File(PathManager.getSystemPath(), "quarkdown/${sanitize(project.name)}")
        } else {
            val f = File(configured)
            if (f.isAbsolute) f
            else File(project.basePath ?: System.getProperty("user.home"), configured)
        }
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
        pendingAutoSave.getAndSet(null)?.cancel(false)
        autoSaveExecutor.shutdown()
        stopPreview()
        listeners.clear()
    }

    companion object {

        /** Debounce delay before the previewed document is auto-saved after typing stops. */
        private const val AUTO_SAVE_DELAY_MS = 100L

        /** Upper bound for auto-shifting the preview port when the configured one is busy. */
        private const val MAX_PORT = 65535

        fun getInstance(project: Project): QuarkdownPreviewService =
            project.getService(QuarkdownPreviewService::class.java)

        /**
         * ANSI escape sequences (color codes etc.) emitted by the Quarkdown CLI.
         * Strips CSI (`ESC[...m`), OSC (`ESC]...BEL`), and other `ESC`-led sequences
         * so logs render as plain text.
         */
        private val ANSI_ESCAPE =
            Regex("\\u001B(?:\\[[0-9;?]*[ -/]*[@-~]|\\][^\\u0007]*(?:\\u0007|\\u001B\\\\)|[@-Z\\\\-_])")

        private fun stripAnsi(text: String): String = ANSI_ESCAPE.replace(text, "")
    }
}

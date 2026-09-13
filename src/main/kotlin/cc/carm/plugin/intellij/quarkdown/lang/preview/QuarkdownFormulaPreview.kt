package cc.carm.plugin.intellij.quarkdown.lang.preview

import cc.carm.plugin.intellij.quarkdown.QuarkdownBundle
import cc.carm.plugin.intellij.quarkdown.lang.latex.QuarkdownEquationRegions
import cc.carm.plugin.intellij.quarkdown.lang.latex.QuarkdownFormulaPreviewSource
import cc.carm.plugin.intellij.quarkdown.ui.preview.QuarkdownFormulaPreviewDialog
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import java.io.File
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

/**
 * Renders a single formula by compiling a throwaway document with the Quarkdown CLI.
 *
 * The preview deliberately goes through the CLI rather than rendering TeX on its own:
 *
 *  - the CLI bundles KaTeX (CSS, JS and fonts) into its output, so the preview works offline;
 *  - it applies the same KaTeX version and theme the final document uses;
 *  - `.texmacro` declarations of the document are copied into the throwaway document, so
 *    formulas built on custom commands render exactly as they will in the real output.
 *
 * The trade-off is a process launch per preview, which is why the compile runs as a
 * cancellable background task and the result is shown in a dialog only once it is ready.
 */
@Service(Service.Level.PROJECT)
class QuarkdownFormulaPreview(private val project: Project) {

    private val logger = Logger.getInstance(QuarkdownFormulaPreview::class.java)

    /** Compiles [region] of [documentText] and shows the rendered result. */
    fun preview(documentText: CharSequence, region: QuarkdownEquationRegions.Region) {
        val executable = QuarkdownCli.resolveExecutable(project)
        if (executable == null) {
            notify(
                QuarkdownBundle.message("quarkdown.math.preview.title"),
                QuarkdownBundle.message("quarkdown.preview.cli.not.found"),
                NotificationType.WARNING,
            )
            return
        }

        val source = QuarkdownFormulaPreviewSource.build(documentText, region)
        ProgressManager.getInstance().run(
            object : Task.Backgroundable(
                project,
                QuarkdownBundle.message("quarkdown.math.preview.progress"),
                true,
            ) {
                private var html: File? = null
                private var error: String? = null

                override fun run(indicator: ProgressIndicator) {
                    val result = compile(executable, source, indicator)
                    html = result.first
                    error = result.second
                }

                override fun onSuccess() {
                    val page = html
                    if (page != null) {
                        QuarkdownFormulaPreviewDialog.show(project, page)
                    } else {
                        notify(
                            QuarkdownBundle.message("quarkdown.math.preview.title"),
                            QuarkdownBundle.message(
                                "quarkdown.math.preview.failed",
                                error ?: QuarkdownBundle.message("quarkdown.math.preview.no.output"),
                            ),
                            NotificationType.ERROR,
                        )
                    }
                }
            }
        )
    }

    /**
     * Writes the throwaway document, runs `quarkdown compile` on it and returns the generated
     * `index.html` — or the reason it could not be produced.
     */
    private fun compile(
        executable: File,
        source: String,
        indicator: ProgressIndicator,
    ): Pair<File?, String?> {
        val workDir = try {
            FileUtil.createTempDirectory("quarkdown-formula", "", true)
        } catch (e: Exception) {
            logger.warn("Failed to create a temp directory for the formula preview", e)
            return null to (e.message ?: e.javaClass.simpleName)
        }

        val sourceFile = File(workDir, "formula.qd")
        val outputDir = File(workDir, "out")
        try {
            sourceFile.writeText(source, Charsets.UTF_8)
        } catch (e: Exception) {
            logger.warn("Failed to write the formula preview document", e)
            return null to (e.message ?: e.javaClass.simpleName)
        }

        val args = listOf(
            executable.absolutePath,
            "compile",
            sourceFile.absolutePath,
            "-o",
            outputDir.absolutePath,
        )
        logger.info("Rendering formula preview: ${args.joinToString(" ")}")

        return try {
            val process = ProcessBuilder(args)
                .also { builder ->
                    builder.redirectErrorStream(true)
                    project.basePath?.let { builder.directory(File(it)) }
                }
                .start()

            val output = StringBuilder()
            // Stream the CLI output while waiting, so a cancellation can stop the process
            // instead of leaving it running behind the dialog.
            val reader = process.inputStream.bufferedReader(Charset.forName("UTF-8"))
            val readerThread = Thread {
                try {
                    reader.forEachLine { line -> synchronized(output) { output.appendLine(line) } }
                } catch (_: Exception) {
                    // The stream closes when the process ends; that is expected.
                }
            }.apply { isDaemon = true; start() }

            val finished = waitFor(process, indicator)
            readerThread.join(1000)
            if (!finished) {
                QuarkdownCli.killProcessTree(process)
                return null to QuarkdownBundle.message("quarkdown.math.preview.cancelled")
            }

            val exitCode = process.exitValue()
            val text = synchronized(output) { output.toString().trim() }
            if (exitCode != 0) {
                return null to text.ifBlank { "exit $exitCode" }
            }

            val page = findIndexHtml(outputDir)
            if (page == null) {
                return null to text.ifBlank { QuarkdownBundle.message("quarkdown.math.preview.no.output") }
            }
            page to null
        } catch (e: Exception) {
            logger.warn("Failed to render the formula preview", e)
            null to (e.message ?: e.javaClass.simpleName)
        }
    }

    /** Waits for the process to finish, killing it when the task is cancelled. */
    private fun waitFor(process: Process, indicator: ProgressIndicator): Boolean {
        val deadline = System.currentTimeMillis() + COMPILE_TIMEOUT_SECONDS * 1000
        while (System.currentTimeMillis() < deadline) {
            if (process.waitFor(200, TimeUnit.MILLISECONDS)) return true
            if (indicator.isCanceled) return false
        }
        return false
    }

    /** The generated entry page, searched breadth-first so nested outputs are found too. */
    private fun findIndexHtml(outputDir: File): File? {
        val queue = ArrayDeque<File>()
        queue.add(outputDir)
        while (queue.isNotEmpty()) {
            val dir = queue.removeFirst()
            val children = dir.listFiles() ?: continue
            children.firstOrNull { it.isFile && it.name.equals("index.html", ignoreCase = true) }
                ?.let { return it }
            children.filter { it.isDirectory }.forEach { queue.addLast(it) }
        }
        return null
    }

    private fun notify(title: String, content: String, type: NotificationType) {
        ApplicationManager.getApplication().invokeLater {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Quarkdown")
                .createNotification(title, content, type)
                .notify(project)
        }
    }

    companion object {

        /** Upper bound for a single formula render (the CLI boots a JVM). */
        private const val COMPILE_TIMEOUT_SECONDS = 120L

        fun getInstance(project: Project): QuarkdownFormulaPreview =
            project.getService(QuarkdownFormulaPreview::class.java)
    }
}


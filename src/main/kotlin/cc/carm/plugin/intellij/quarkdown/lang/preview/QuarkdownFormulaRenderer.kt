package cc.carm.plugin.intellij.quarkdown.lang.preview

import cc.carm.plugin.intellij.quarkdown.QuarkdownBundle
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import java.io.File
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

/**
 * Renders a throwaway Quarkdown document with the Quarkdown CLI and locates the generated page.
 *
 * Rendering through the CLI (instead of, say, KaTeX on its own) is what makes the preview
 * faithful and offline: the CLI bundles KaTeX (CSS, JS and fonts) into its output and applies
 * the same theme as the final document, and it resolves the document's own `.texmacro`
 * declarations into `window.texMacros`.
 *
 * The work is synchronous and therefore meant to run on a background thread; a progress
 * indicator is honoured so a long render can be cancelled.
 */
object QuarkdownFormulaRenderer {

    private val logger = Logger.getInstance(QuarkdownFormulaRenderer::class.java)

    /** The generated page, or the reason it could not be produced. */
    data class Result(val page: File?, val error: String?)

    /** Upper bound for a single render (the CLI boots a JVM). */
    private const val COMPILE_TIMEOUT_SECONDS = 120L

    /**
     * Compiles [source] (a complete `.qd` document) and returns its `index.html`.
     *
     * Every call gets a fresh temporary directory, so the produced page URL is never reused and
     * the embedded browser cannot serve a stale cached render.
     */
    fun render(project: Project, executable: File, source: String, indicator: ProgressIndicator?): Result {
        val workDir = try {
            FileUtil.createTempDirectory("quarkdown-formula", "", true)
        } catch (e: Exception) {
            logger.warn("Failed to create a temp directory for the formula preview", e)
            return Result(null, e.message ?: e.javaClass.simpleName)
        }

        val sourceFile = File(workDir, "formula.qd")
        val outputDir = File(workDir, "out")
        try {
            sourceFile.writeText(source, Charsets.UTF_8)
        } catch (e: Exception) {
            logger.warn("Failed to write the formula preview document", e)
            return Result(null, e.message ?: e.javaClass.simpleName)
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
                // Cancelled (or timed out): make sure the JVM does not linger behind the UI.
                QuarkdownCli.killProcessTree(process)
                return Result(null, QuarkdownBundle.message("quarkdown.math.preview.cancelled"))
            }

            val exitCode = process.exitValue()
            val text = synchronized(output) { output.toString().trim() }
            if (exitCode != 0) {
                return Result(null, text.ifBlank { "exit $exitCode" })
            }

            val page = findIndexHtml(outputDir)
            if (page == null) {
                return Result(null, text.ifBlank { QuarkdownBundle.message("quarkdown.math.preview.no.output") })
            }
            Result(page, null)
        } catch (e: Exception) {
            logger.warn("Failed to render the formula preview", e)
            Result(null, e.message ?: e.javaClass.simpleName)
        }
    }

    /** Waits for the process to finish, killing it when the task is cancelled. */
    private fun waitFor(process: Process, indicator: ProgressIndicator?): Boolean {
        val deadline = System.currentTimeMillis() + COMPILE_TIMEOUT_SECONDS * 1000
        while (System.currentTimeMillis() < deadline) {
            if (process.waitFor(200, TimeUnit.MILLISECONDS)) return true
            if (indicator != null && indicator.isCanceled) return false
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
}


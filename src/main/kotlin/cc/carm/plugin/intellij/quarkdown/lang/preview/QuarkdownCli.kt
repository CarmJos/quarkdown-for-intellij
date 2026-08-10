package cc.carm.plugin.intellij.quarkdown.lang.preview

import cc.carm.plugin.intellij.quarkdown.settings.QuarkdownSettings
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * Thin wrapper around the Quarkdown CLI.
 *
 * Two modes are supported:
 *
 * **Live preview (server mode)** — a single long-lived process keeps a web server alive.
 * The base command is kept minimal (`quarkdown compile <source> --preview`); the plugin
 * only appends the flags it needs to operate:
 * ```
 * quarkdown compile <source> --preview [--watch] --server-port <port> --browser none -o <out> <extraArgs>
 * ```
 * - `--watch` is added only when the "watch changes" setting is enabled.
 * - `--browser none` prevents the CLI from opening a browser on its own; opening the
 *   preview in a browser is always user-triggered.
 * - `--server-port` and `-o` are internal to the plugin and are never shown in the
 *   settings page description of the base command.
 *
 * **Build / PDF (one-shot)** — used together with the IDE *Run* tool window. The base
 * command is `quarkdown compile <source> --pdf`; every other option (`--timeout`,
 * `--allow`, `--out-name`, `-o`, ...) is supplied by the user's CLI arguments setting.
 */
object QuarkdownCli {

    private val logger = Logger.getInstance(QuarkdownCli::class.java)


    /** How long to wait for the preview web server to become reachable. */
    const val SERVER_START_TIMEOUT_SECONDS = 60L

    /**
     * Resolves the `quarkdown` launcher for the configured Quarkdown home.
     *
     * The configured path may be a directory (the installation root or a `bin/` folder)
     * or the launcher file itself. Falls back to scanning `PATH`.
     */
    fun resolveExecutable(project: Project): File? {
        val configured = QuarkdownSettings.getInstance(project).state.quarkdownPath
        if (!configured.isNullOrBlank()) {
            val path = File(configured.trim())
            if (path.isFile && path.name.startsWith("quarkdown")) return path
            if (path.isDirectory) {
                findInDirectory(path)?.let { return it }
            }
        }
        return findOnPath()
    }

    /**
     * Builds the argument list for the long-lived **preview server** process.
     *
     * The base command is minimal (`quarkdown compile <source> --preview`); the plugin
     * only appends what it needs to operate: `--watch` (when enabled), `--server-port`,
     * `--browser none` (so the CLI never opens a browser on its own) and `-o`.
     * Everything else comes from the user's preview CLI arguments setting.
     */
    fun previewServerArgs(
        executable: File,
        source: File,
        port: Int,
        outputDir: File,
        watch: Boolean,
        extraArgs: String?,
    ): List<String> {
        val args = mutableListOf(
            executable.absolutePath,
            "compile",
            source.absolutePath,
            "--preview",
        )
        if (watch) args.add("--watch")
        args.add("--server-port")
        args.add(port.toString())
        args.add("-o")
        args.add(outputDir.absolutePath)
        // Never auto-open a browser when starting the preview — opening is user-triggered.
        if (!hasBrowserOption(extraArgs)) {
            args.add("--browser")
            args.add("none")
        }
        args.addAll(tokenizeArguments(extraArgs))
        return args
    }

    /**
     * Builds the argument list for a one-shot **build** (PDF export) that is executed
     * through the IDE *Run* tool window. The base command is minimal —
     * `quarkdown compile <source> --pdf` — and every other option (`--timeout`,
     * `--allow`, `--out-name`, `-o`, ...) is supplied by the user's build CLI
     * arguments setting.
     */
    fun buildRunArgs(
        executable: File,
        source: File,
        extraArgs: String?,
    ): List<String> {
        val args = mutableListOf(
            executable.absolutePath,
            "compile",
            source.absolutePath,
            "--pdf",
        )
        args.addAll(tokenizeArguments(extraArgs))
        return args
    }

    /** True when the extra arguments already specify a `--browser` option. */
    private fun hasBrowserOption(extraArgs: String?): Boolean =
        tokenizeArguments(extraArgs).any { it == "--browser" || it == "-b" || it.startsWith("--browser=") }

    /** True when an HTTP server answers on [port] (any status code means "ready"). */
    fun isPortReady(port: Int): Boolean = try {
        val conn = URI("http://127.0.0.1:$port/").toURL().openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 1000
            conn.readTimeout = 1000
            conn.requestMethod = "GET"
            conn.responseCode
            true
        } finally {
            conn.disconnect()
        }
    } catch (e: Exception) {
        false
    }

    /** Polls [port] until an HTTP server answers, up to [timeoutSeconds]. */
    fun waitForPortReady(port: Int, timeoutSeconds: Long = SERVER_START_TIMEOUT_SECONDS): Boolean {
        val deadline = System.currentTimeMillis() + timeoutSeconds * 1000
        while (System.currentTimeMillis() < deadline) {
            if (isPortReady(port)) return true
            Thread.sleep(250)
        }
        return isPortReady(port)
    }

    /** Polls [port] until it stops answering, up to [timeoutSeconds]. */
    fun waitForPortClosed(port: Int, timeoutSeconds: Long = 10): Boolean {
        val deadline = System.currentTimeMillis() + timeoutSeconds * 1000
        while (System.currentTimeMillis() < deadline) {
            if (!isPortReady(port)) return true
            Thread.sleep(250)
        }
        return !isPortReady(port)
    }

    /**
     * Forcefully terminates [process] including its child processes.
     *
     * On Windows the launcher is a `.bat`/`.cmd`; killing just the shell would leave the
     * child JVM (and thus the bound port) alive, so the whole tree is killed via `taskkill`.
     */
    fun killProcessTree(process: Process) {
        if (SystemInfo.isWindows) {
            try {
                ProcessBuilder(
                    "taskkill", "/PID", process.pid().toString(), "/T", "/F"
                )
                    .redirectErrorStream(true)
                    .start()
                    .waitFor(10, TimeUnit.SECONDS)
                return
            } catch (e: Exception) {
                logger.warn("taskkill failed, falling back to destroyForcibly", e)
            }
        }
        try {
            process.destroyForcibly()
        } catch (e: Exception) {
            logger.warn("Failed to destroy Quarkdown process", e)
        }
    }

    private fun findInDirectory(dir: File): File? {
        for (name in LAUNCHER_NAMES) {
            File(File(dir, "bin"), name).takeIf { it.isFile }?.let { return it }
        }
        for (name in LAUNCHER_NAMES) {
            File(dir, name).takeIf { it.isFile }?.let { return it }
        }
        return null
    }

    private fun findOnPath(): File? {
        val pathEnv = System.getenv("PATH") ?: return null
        for (dir in pathEnv.split(File.pathSeparator)) {
            if (dir.isBlank()) continue
            for (name in LAUNCHER_NAMES) {
                File(dir, name).takeIf { it.isFile }?.let { return it }
            }
        }
        return null
    }

    /** Splits a raw argument string into tokens, honoring single/double quotes. */
    fun tokenizeArguments(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null
        for (c in raw.trim()) {
            when {
                quote != null -> if (c == quote) quote = null else current.append(c)
                c == '"' || c == '\'' -> quote = c
                c.isWhitespace() -> if (current.isNotEmpty()) {
                    tokens.add(current.toString())
                    current.setLength(0)
                }

                else -> current.append(c)
            }
        }
        if (current.isNotEmpty()) tokens.add(current.toString())
        return tokens
    }

    /** Platform-specific launcher names of the `quarkdown` command (order = preference). */
    internal val LAUNCHER_NAMES = listOf("quarkdown.cmd", "quarkdown.bat", "quarkdown")
}

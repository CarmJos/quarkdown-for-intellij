package cc.carm.plugin.intellij.quarkdown.lang.preview

import cc.carm.plugin.intellij.quarkdown.settings.QuarkdownSettings
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Thin wrapper around the Quarkdown CLI.
 *
 * Two modes are supported:
 *
 * **Live preview (server mode)** — a single long-lived process keeps a web server alive:
 * ```
 * quarkdown compile <source> -p [-w] --server-port <port> --allow all -o <out> <extraArgs>
 * ```
 * With `-w` the CLI watches the source directory and recompiles automatically; the
 * built-in browser page hot-reloads the changes.
 *
 * **Build / PDF (one-shot)** — used together with the IDE *Run* tool window:
 * ```
 * quarkdown compile <source> --pdf --timeout 60 --allow all --out-name main -o <out> <extraArgs>
 * ```
 */
object QuarkdownCli {

    private val logger = Logger.getInstance(QuarkdownCli::class.java)

    /** Fixed output resource name for one-shot builds, so the artifact is easy to locate. */
    const val OUT_NAME = "main"

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
     * The produced command follows the reference:
     * `quarkdown compile <source> -p [-w] --server-port <port> --allow all -o <out> <extraArgs>`
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
            "-p",
        )
        if (watch) args.add("-w")
        args.add("--server-port")
        args.add(port.toString())
        args.add("--allow")
        args.add("all")
        args.add("-o")
        args.add(outputDir.absolutePath)
        args.addAll(tokenizeArguments(extraArgs))
        return args
    }

    /**
     * Builds the argument list for a one-shot **build** (PDF export) that is executed
     * through the IDE *Run* tool window:
     * `quarkdown compile <source> --pdf --timeout 60 --allow all --out-name main -o <out> <extraArgs>`
     */
    fun buildRunArgs(
        executable: File,
        source: File,
        outputDir: File,
        extraArgs: String?,
    ): List<String> {
        val args = mutableListOf(
            executable.absolutePath,
            "compile",
            source.absolutePath,
            "--pdf",
            "--timeout", "60",
            "--allow", "all",
            "--out-name", OUT_NAME,
            "-o", outputDir.absolutePath,
        )
        args.addAll(tokenizeArguments(extraArgs))
        return args
    }

    /** True when an HTTP server answers on [port] (any status code means "ready"). */
    fun isPortReady(port: Int): Boolean = try {
        val conn = URL("http://127.0.0.1:$port/").openConnection() as HttpURLConnection
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

    private val LAUNCHER_NAMES = listOf("quarkdown.cmd", "quarkdown.bat", "quarkdown")
}


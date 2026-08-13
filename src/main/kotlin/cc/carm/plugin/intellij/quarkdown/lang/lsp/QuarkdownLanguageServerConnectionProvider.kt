package cc.carm.plugin.intellij.quarkdown.lang.lsp

import cc.carm.plugin.intellij.quarkdown.settings.QuarkdownPathDetector
import cc.carm.plugin.intellij.quarkdown.settings.QuarkdownSettings
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.redhat.devtools.lsp4ij.server.OSProcessStreamConnectionProvider
import java.io.File

/**
 * Launches the official `quarkdown language-server` as a JVM child process (not through
 * the `.cmd`/`.sh` launcher shim) so the stdio channel stays clean. The classpath uses
 * the JVM wildcard form `<home>/lib/` + `*` (the literal `*` glob).
 *
 * A bundled JRE shipped with the Quarkdown installation is preferred; otherwise the
 * `java` executable found on `PATH` is used. When the Quarkdown home cannot be resolved
 * (or lacks a JRE / LSP libraries), the command line is left unset so LSP4IJ reports a
 * clean "cannot start process" error; [QuarkdownLspServerManager] then surfaces a
 * user-facing balloon with the precise reason.
 */
class QuarkdownLanguageServerConnectionProvider(
    project: Project,
) : OSProcessStreamConnectionProvider() {

    init {
        val home = resolveQuarkdownHome(project)
        if (home != null) {
            val java = resolveJavaExecutable(home)
            val libDir = File(home, "lib")
            if (java != null && hasLspLibraries(home)) {
                val classpath = "${libDir.absolutePath}${File.separator}*"
                val commandLine = GeneralCommandLine(
                    java.absolutePath,
                    "-classpath", classpath,
                    MAIN_CLASS,
                    "language-server",
                )
                commandLine.charset = Charsets.UTF_8
                project.basePath?.let { commandLine.workDirectory = File(it) }
                setCommandLine(commandLine)
                // Forward unexpected process terminations to the lifecycle manager so it
                // can run its bounded retry and show a failure balloon.
                addUnexpectedServerStopHandler {
                    QuarkdownLspServerManager.getInstance(project).onServerStopped(shutdownNormally = false)
                }
            }
        }
    }

    companion object {
        private val LOG = Logger.getInstance(QuarkdownLanguageServerConnectionProvider::class.java)

        private const val MAIN_CLASS = "com.quarkdown.cli.QuarkdownCliKt"

        /**
         * Resolves the Quarkdown installation home from settings, or auto-detects it.
         *
         * The configured path may point at the *launcher* location (`bin/`, the Homebrew
         * keg wrapper, …) instead of the real installation home. It is resolved back to
         * the home whose `lib` folder holds the `*.jar` files (see
         * [QuarkdownPathDetector.resolveHome]); otherwise the LSP is launched with a
         * broken classpath and dies with `ClassNotFoundException`.
         */
        fun resolveQuarkdownHome(project: Project): String? {
            val configured = QuarkdownSettings.getInstance(project).state.quarkdownPath
            if (!configured.isNullOrBlank()) {
                val resolved = QuarkdownPathDetector.resolveHome(configured)
                if (resolved != null) {
                    if (resolved != File(configured.trim()).absolutePath) {
                        LOG.info("Resolved Quarkdown home '$configured' -> '$resolved'")
                    }
                    return resolved
                }
                LOG.warn("Configured Quarkdown path '$configured' is not a valid installation; falling back to auto-detection")
            }
            return QuarkdownPathDetector.detect()
        }

        /** True when `<home>/lib` exists and contains at least one `.jar` (the LSP classpath). */
        fun hasLspLibraries(home: String): Boolean =
            QuarkdownPathDetector.hasStdlibJars(File(home))

        /**
         * Resolves the JVM used to launch the LSP server.
         *
         * The bundled JRE shipped inside the Quarkdown installation
         * (`<home>/runtime/bin/java(.exe)`) is preferred; otherwise the `java` executable
         * found on `PATH` is used.
         */
        fun resolveJavaExecutable(home: String): File? {
            val bundled = File(File(home, "runtime"), File("bin", if (SystemInfo.isWindows) "java.exe" else "java").path)
            if (bundled.isFile) return bundled

            val pathEnv = System.getenv("PATH") ?: return null
            val names = if (SystemInfo.isWindows) listOf("java.exe", "java") else listOf("java")
            for (dir in pathEnv.split(File.pathSeparator)) {
                if (dir.isBlank()) continue
                for (name in names) {
                    File(dir, name).takeIf { it.isFile }?.let { return it }
                }
            }
            return null
        }
    }
}

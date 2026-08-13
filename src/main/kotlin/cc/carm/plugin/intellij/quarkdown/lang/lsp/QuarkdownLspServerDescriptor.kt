package cc.carm.plugin.intellij.quarkdown.lang.lsp

import cc.carm.plugin.intellij.quarkdown.QuarkdownFileType
import cc.carm.plugin.intellij.quarkdown.settings.QuarkdownPathDetector
import cc.carm.plugin.intellij.quarkdown.settings.QuarkdownSettings
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServerDescriptor
import com.intellij.platform.lsp.api.LspServerListener
import com.intellij.platform.lsp.api.customization.LspCompletionSupport
import com.intellij.platform.lsp.api.customization.LspSemanticTokensSupport
import java.io.File

/**
 * Descriptor for the official `quarkdown language-server` LSP process.
 *
 * The server is launched as a JVM child process (not through the `.cmd`/`.sh` launcher
 * shim) so the stdio channel stays clean. The classpath uses the JVM wildcard form
 * `<home>/lib/asterisk` (the literal `*` glob).
 *
 * A bundled JRE shipped with the Quarkdown installation is preferred; otherwise the
 * `java` executable found on `PATH` is used.
 */
class QuarkdownLspServerDescriptor(
    project: Project,
    vararg roots: VirtualFile,
) : LspServerDescriptor(project, "Quarkdown Language Server", *roots) {

    override fun isSupportedFile(file: VirtualFile): Boolean =
        file.fileType == QuarkdownFileType.INSTANCE

    override fun getLanguageId(file: VirtualFile): String = "quarkdown"

    override fun createCommandLine(): GeneralCommandLine {
        val home = resolveQuarkdownHome(project)
            ?: throw ExecutionException(
                "Quarkdown installation not found. Configure the Quarkdown home in " +
                    "Settings → Languages & Frameworks → Quarkdown."
            )

        val java = resolveJavaExecutable(home)
            ?: throw ExecutionException(
                "No Java executable found for the Quarkdown LSP server (home: $home)."
            )

        if (!hasLspLibraries(home)) {
            throw ExecutionException(
                "The Quarkdown installation at '$home' does not contain the LSP " +
                    "libraries (lib/*.jar). Please check the Quarkdown home path in Settings."
            )
        }

        val libDir = File(home, "lib")
        val classpath = "${libDir.absolutePath}${File.separator}*"

        val commandLine = GeneralCommandLine(
            java.absolutePath,
            "-classpath", classpath,
            MAIN_CLASS,
            "language-server"
        )
        commandLine.charset = Charsets.UTF_8
        project.basePath?.let { commandLine.workDirectory = File(it) }
        return commandLine
    }

    // The platform's default LspCustomization already enables hover, diagnostics,
    // completion and semantic tokens; we only override the semantic-token color mapping
    // (quarkdown-lsp's legend → this plugin's existing highlight attributes) and the
    // completion support (to auto-trigger the parameter-info popup after insertion).
    // `LspServerDescriptor` is an experimental platform API
    // (see https://plugins.jetbrains.com/docs/intellij/language-server-protocol.html).
    @Suppress("OVERRIDE_DEPRECATION")
    override val lspSemanticTokensSupport: LspSemanticTokensSupport get() =
        QuarkdownLspSemanticTokensSupport()

    @Suppress("OVERRIDE_DEPRECATION")
    override val lspCompletionSupport: LspCompletionSupport get() =
        QuarkdownLspCompletionSupport()

    /**
     * Forwards server lifecycle events to [QuarkdownLspServerManager] so it can retry
     * a crashed/failed server and restart it against a new Quarkdown home.
     *
     * The platform invokes [LspServerListener.serverInitialized] once the server reaches
     * the `Running` state, and [LspServerListener.serverStopped] with `shutdownNormally`
     * set to `false` when the server stopped unexpectedly. This is the public LSP API
     * replacement for the (internal) `LspServerManagerListener`, which the plugin
     * verifier rejects.
     */
    override val lspServerListener: LspServerListener = object : LspServerListener {
        override fun serverInitialized(params: org.eclipse.lsp4j.InitializeResult) {
            QuarkdownLspServerManager.getInstance(project).onServerInitialized()
        }

        override fun serverStopped(shutdownNormally: Boolean) {
            QuarkdownLspServerManager.getInstance(project).onServerStopped(shutdownNormally)
        }
    }

    companion object {
        private val LOG = Logger.getInstance(QuarkdownLspServerDescriptor::class.java)

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
        fun hasLspLibraries(home: String): Boolean {
            val libDir = File(File(home), "lib")
            if (!libDir.isDirectory) return false
            return libDir.listFiles { f -> f.name.endsWith(".jar") }?.isNotEmpty() == true
        }

        /** Returns the running LSP server for this plugin, or `null` when none is active. */
        fun currentServer(project: Project): com.intellij.platform.lsp.api.LspServer? =
            try {
                com.intellij.platform.lsp.api.LspServerManager.getInstance(project)
                    .getServersForProvider(QuarkdownLspServerSupportProvider::class.java)
                    .firstOrNull()
            } catch (e: Exception) {
                null
            }

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

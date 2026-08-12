package cc.carm.plugin.intellij.quarkdown.lang.lsp

import cc.carm.plugin.intellij.quarkdown.QuarkdownFileType
import cc.carm.plugin.intellij.quarkdown.settings.QuarkdownPathDetector
import cc.carm.plugin.intellij.quarkdown.settings.QuarkdownSettings
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServerDescriptor
import com.intellij.platform.lsp.api.customization.LspCompletionSupport
import com.intellij.platform.lsp.api.customization.LspDiagnosticsSupport
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
            ?: throw IllegalStateException("Quarkdown installation not found")

        val java = resolveJavaExecutable(home)
            ?: throw IllegalStateException("No Java executable found for Quarkdown LSP")

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

    override val lspHoverSupport: Boolean get() = true

    override val lspCompletionSupport: LspCompletionSupport get() = LspCompletionSupport()

    override val lspDiagnosticsSupport: LspDiagnosticsSupport get() = LspDiagnosticsSupport()

    override val lspSemanticTokensSupport: LspSemanticTokensSupport get() =
        QuarkdownLspSemanticTokensSupport()

    companion object {
        private const val MAIN_CLASS = "com.quarkdown.cli.QuarkdownCliKt"

        /** Resolves the Quarkdown installation home from settings, or auto-detects it. */
        fun resolveQuarkdownHome(project: Project): String? {
            val configured = QuarkdownSettings.getInstance(project).state.quarkdownPath
            if (!configured.isNullOrBlank() && QuarkdownPathDetector.isValidQuarkdownHome(configured)) {
                return File(configured.trim()).absolutePath
            }
            return QuarkdownPathDetector.detect()
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

package cc.carm.plugin.intellij.quarkdown.lang.lsp

import cc.carm.plugin.intellij.quarkdown.settings.QuarkdownSettings
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.api.LspServerManager
import java.io.File

/**
 * Central switch for the hybrid architecture (see `docs/LSP-integration-plan.md`).
 *
 * When the official Quarkdown Language Server is available (a Quarkdown installation is
 * found and the `use LSP semantics` setting is enabled), the semantic layer — hover,
 * diagnostics, function completion, semantic highlighting — is delegated to LSP. The
 * legacy reflective implementation (FunctionRegistry & friends) is then kept as an
 * offline fallback only.
 *
 * The `fileOpened` hooks of the platform LSP plugin call this to decide whether to start
 * the server; the legacy annotators / completion contributors consult it to skip their
 * work while LSP is active (avoiding duplicated diagnostics & completion items).
 */
object QuarkdownLspSupport {

    private val logger = Logger.getInstance(QuarkdownLspSupport::class.java)

    /** Returns `true` when LSP semantics should be active for [project]. */
    fun isEnabled(project: Project): Boolean {
        val settings = QuarkdownSettings.getInstance(project).state
        if (!settings.useLspSemantics) return false
        return resolveHome(project) != null
    }

    /**
     * Returns `true` when an LSP server is actually running for [project] (i.e. the
     * platform has started the `quarkdown language-server` process). This is the
     * "switch point" between the LSP semantic layer and the legacy reflective fallback:
     * legacy annotators / completion contributors defer only while the server is live,
     * avoiding duplicated diagnostics and completion items.
     */
    fun isServerRunning(project: Project): Boolean = try {
        val manager = LspServerManager.getInstance(project)
        manager.getServersForProvider(QuarkdownLspServerSupportProvider::class.java).isNotEmpty()
    } catch (e: Throwable) {
        logger.debug("LSP server manager unavailable: ${e.message}")
        false
    }

    /** Resolves the Quarkdown installation home used for LSP, or `null`. */
    fun resolveHome(project: Project): String? =
        QuarkdownLspServerDescriptor.resolveQuarkdownHome(project)
}

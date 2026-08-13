package cc.carm.plugin.intellij.quarkdown.lang.lsp

import com.intellij.openapi.project.Project
import com.redhat.devtools.lsp4ij.ServerStatus
import com.redhat.devtools.lsp4ij.client.LanguageClientImpl

/**
 * Quarkdown-specific LSP client.
 *
 * Forwards the server lifecycle status changes to [QuarkdownLspServerManager] so it can
 * track successful initialization and surface recovery notifications. Unexpected process
 * terminations are reported through the connection provider's unexpected-stop handler
 * (see [QuarkdownLanguageServerConnectionProvider]); this client only needs the `started`
 * transition to mark the server as healthy.
 */
class QuarkdownLanguageClient(project: Project) : LanguageClientImpl(project) {

    override fun handleServerStatusChanged(serverStatus: ServerStatus) {
        if (serverStatus == ServerStatus.started) {
            QuarkdownLspServerManager.getInstance(project).onServerInitialized()
        }
    }
}

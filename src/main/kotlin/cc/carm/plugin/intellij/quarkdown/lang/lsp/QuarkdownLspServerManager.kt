package cc.carm.plugin.intellij.quarkdown.lang.lsp

import cc.carm.plugin.intellij.quarkdown.QuarkdownBundle
import cc.carm.plugin.intellij.quarkdown.settings.QuarkdownPathDetector
import cc.carm.plugin.intellij.quarkdown.settings.QuarkdownSettings
import cc.carm.plugin.intellij.quarkdown.settings.QuarkdownSettingsConfigurable
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.api.LspServer
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.platform.lsp.api.LspServerManagerListener
import com.intellij.platform.lsp.api.LspServerState
import java.util.concurrent.ConcurrentHashMap

/**
 * Watches the `quarkdown language-server` lifecycle and keeps it usable.
 *
 * The IntelliJ platform LSP integration does **not** automatically restart a server
 * that crashed or failed to initialize; it only reports the unexpected shutdown to
 * [LspServerManagerListener]s and otherwise leaves the Quarkdown features dead. This
 * service:
 *
 *  - listens for unexpected shutdowns and retries the server with a short backoff
 *    (bounded, so a genuinely broken installation is never retried forever);
 *  - raises an alarm balloon when the server still cannot start, with an "Open
 *    Settings" action so the user can fix the Quarkdown home path;
 *  - exposes [restart] so the server can be restarted against a *new* Quarkdown path
 *    right after the user changes it in the settings.
 */
@Service(Service.Level.PROJECT)
class QuarkdownLspServerManager(private val project: Project) : Disposable {

    private val retryAttempts = ConcurrentHashMap<Class<*>, Int>()

    init {
        try {
            // `true` → the listener is also notified about servers that already stopped
            // unexpectedly before this service was created (e.g. after a plugin reload).
            LspServerManager.getInstance(project)
                .addLspServerManagerListener(LspListener(), this, true)
        } catch (e: Throwable) {
            // The LSP platform module may not be fully initialized yet; the listener will
            // simply not be active, which only disables auto-retry (not core editing).
            LOG.warn("Failed to subscribe to LSP server lifecycle events", e)
        }
    }

    /**
     * Stops and restarts the Quarkdown LSP server against the *current* settings.
     *
     * Called when the user changes the Quarkdown home path so the server is restarted
     * with the new installation. Any pending retry state is cleared first, so the fresh
     * start is not mistaken for an automatic retry.
     */
    fun restart() {
        retryAttempts.clear()
        try {
            LspServerManager.getInstance(project)
                .stopAndRestartIfNeeded(QuarkdownLspServerSupportProvider::class.java)
            LOG.info("Restarted the Quarkdown LSP server")
        } catch (e: Throwable) {
            LOG.warn("Failed to restart the Quarkdown LSP server", e)
        }
    }

    private fun onServerStateChanged(server: LspServer) {
        if (server.providerClass != QuarkdownLspServerSupportProvider::class.java) return
        when (server.state) {
            LspServerState.ShutdownUnexpectedly -> handleUnexpectedStop()
            LspServerState.Running -> handleServerRunning()
            else -> Unit
        }
    }

    /** A Quarkdown LSP server stopped without being asked to — try to bring it back. */
    private fun handleUnexpectedStop() {
        val key = QuarkdownLspServerSupportProvider::class.java
        val attempts = retryAttempts.merge(key, 1, Int::plus) ?: 1

        // A broken installation (missing home / Java / libraries) is guaranteed to fail
        // again — do not retry it, alarm right away instead.
        val validationError = validateLaunch()
        if (validationError != null) {
            retryAttempts.remove(key)
            notifyFailure(validationError)
            return
        }

        if (attempts > MAX_RETRIES) {
            retryAttempts.remove(key)
            notifyFailure(QuarkdownBundle.message("quarkdown.lsp.failed.after.retries", MAX_RETRIES))
            return
        }

        val delayMs = RETRY_DELAY_MS * attempts
        LOG.info("Quarkdown LSP server stopped unexpectedly (attempt $attempts/$MAX_RETRIES); restarting in ${delayMs}ms")
        scheduleRestart(delayMs)
    }

    /** The server reached `Running` — if it was restarted after a failure, say so. */
    private fun handleServerRunning() {
        val attempts = retryAttempts.remove(QuarkdownLspServerSupportProvider::class.java)
        if (attempts != null && attempts > 0) {
            LOG.info("Quarkdown LSP server recovered after $attempts retries")
            notifyRecovered(attempts)
        }
    }

    /**
     * Validates that the Quarkdown LSP server *can* be launched with the current
     * settings. Returns a user-facing error message, or `null` when everything is ready.
     */
    private fun validateLaunch(): String? {
        val configured = QuarkdownSettings.getInstance(project).state.quarkdownPath
        val home = QuarkdownLspServerDescriptor.resolveQuarkdownHome(project)
        if (home == null) {
            return QuarkdownBundle.message("quarkdown.lsp.error.home.not.found")
        }
        if (!configured.isNullOrBlank() && QuarkdownPathDetector.resolveHome(configured) == null) {
            return QuarkdownBundle.message("quarkdown.lsp.error.home.invalid", configured)
        }
        val java = QuarkdownLspServerDescriptor.resolveJavaExecutable(home)
        if (java == null) {
            return QuarkdownBundle.message("quarkdown.lsp.error.java.not.found", home)
        }
        if (!QuarkdownLspServerDescriptor.hasLspLibraries(home)) {
            return QuarkdownBundle.message("quarkdown.lsp.error.lib.missing", home)
        }
        return null
    }

    private fun scheduleRestart(delayMs: Long) {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                Thread.sleep(delayMs)
            } catch (e: InterruptedException) {
                return@executeOnPooledThread
            }
            if (project.isDisposed) return@executeOnPooledThread
            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) return@invokeLater
                try {
                    LspServerManager.getInstance(project)
                        .stopAndRestartIfNeeded(QuarkdownLspServerSupportProvider::class.java)
                } catch (e: Throwable) {
                    LOG.warn("Failed to restart the Quarkdown LSP server after an unexpected stop", e)
                }
            }
        }
    }

    private fun notifyFailure(detail: String) {
        LOG.warn("Quarkdown LSP server failed: $detail")
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP)
            .createNotification(
                QuarkdownBundle.message("quarkdown.lsp.failed.title"),
                detail,
                NotificationType.ERROR,
            )
            .addAction(NotificationAction.createSimple(QuarkdownBundle.message("quarkdown.lsp.open.settings")) {
                ShowSettingsUtil.getInstance()
                    .showSettingsDialog(project, QuarkdownSettingsConfigurable::class.java)
            })
            .notify(project)
    }

    private fun notifyRecovered(attempts: Int) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP)
            .createNotification(
                QuarkdownBundle.message("quarkdown.lsp.recovered.title"),
                QuarkdownBundle.message("quarkdown.lsp.recovered", attempts),
                NotificationType.INFORMATION,
            )
            .notify(project)
    }

    private inner class LspListener : LspServerManagerListener {
        override fun serverStateChanged(lspServer: LspServer) = onServerStateChanged(lspServer)
    }

    override fun dispose() {
        retryAttempts.clear()
    }

    companion object {
        private val LOG = Logger.getInstance(QuarkdownLspServerManager::class.java)

        private const val NOTIFICATION_GROUP = "Quarkdown"
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 3_000L

        fun getInstance(project: Project): QuarkdownLspServerManager =
            project.getService(QuarkdownLspServerManager::class.java)
    }
}

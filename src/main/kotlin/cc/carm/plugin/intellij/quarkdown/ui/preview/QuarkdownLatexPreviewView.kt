package cc.carm.plugin.intellij.quarkdown.ui.preview

import cc.carm.plugin.intellij.quarkdown.QuarkdownBundle
import cc.carm.plugin.intellij.quarkdown.lang.latex.QuarkdownLatexPreviewSource
import cc.carm.plugin.intellij.quarkdown.lang.latex.QuarkdownLatexWebAssets
import cc.carm.plugin.intellij.quarkdown.settings.QuarkdownPathDetector
import cc.carm.plugin.intellij.quarkdown.settings.QuarkdownSettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBLabel
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.util.ui.JBUI
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandler
import org.cef.network.CefRequest
import java.io.File
import javax.swing.JComponent
import javax.swing.SwingConstants

/**
 * Typesets LaTeX live using the KaTeX build that ships inside the Quarkdown installation.
 *
 * The page is loaded once into the embedded browser and every update is a single JavaScript call
 * ([QuarkdownLatexPreviewSource.renderCall]), so typing re-renders in milliseconds — no CLI process,
 * no compile, nothing bundled in this plugin. Rendering is driven by [render] and needs no
 * sequencing or debouncing of its own; callers may still debounce to avoid redundant calls.
 *
 * The view degrades into an explanatory label (never an exception) when the prerequisites are
 * missing:
 *
 *  - JCEF, which is an optional dependency of this plugin;
 *  - the KaTeX assets, when the configured Quarkdown home does not contain them.
 */
class QuarkdownLatexPreviewView(private val project: Project) : Disposable {

    private val logger = Logger.getInstance(QuarkdownLatexPreviewView::class.java)

    private val assetRoot: File? = resolveAssetRoot()

    private val assetServer: QuarkdownLatexAssetServer? = assetRoot?.let { root ->
        try {
            QuarkdownLatexAssetServer(root, QuarkdownLatexPreviewSource.pageHtml(SCRIPT_PATH, STYLE_PATH))
                .also { Disposer.register(this, it) }
        } catch (e: Exception) {
            logger.warn("Failed to start the formula preview asset server", e)
            null
        }
    }

    private val browser: JBCefBrowser? = if (assetServer != null) JcefSupport.createBrowser() else null

    @Volatile
    private var pageReady = false

    /** The most recent call requested before the page finished loading. */
    @Volatile
    private var pendingCall: String? = null

    val component: JComponent = createComponent()

    private fun createComponent(): JComponent {
        val current = browser
        val server = assetServer
        if (current != null && server != null) {
            current.jbCefClient.addLoadHandler(
                object : CefLoadHandler {
                    override fun onLoadingStateChange(
                        browser: CefBrowser,
                        isLoading: Boolean,
                        canGoBack: Boolean,
                        canGoForward: Boolean,
                    ) = Unit

                    override fun onLoadStart(
                        browser: CefBrowser,
                        frame: CefFrame,
                        transitionType: CefRequest.TransitionType,
                    ) = Unit

                    override fun onLoadEnd(browser: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
                        pageReady = true
                        // Replay whatever was requested while the page was loading.
                        pendingCall?.let { call ->
                            pendingCall = null
                            browser.executeJavaScript(call, server.baseUrl, 0)
                        }
                    }

                    override fun onLoadError(
                        browser: CefBrowser,
                        frame: CefFrame,
                        errorCode: CefLoadHandler.ErrorCode,
                        errorText: String,
                        failedUrl: String,
                    ) {
                        logger.warn("Failed to load the formula preview page: $errorText ($failedUrl)")
                    }
                },
                current.cefBrowser,
            )
            Disposer.register(this, current)
            current.loadURL(server.baseUrl)
            return current.component
        }

        val message = when {
            assetRoot == null -> QuarkdownBundle.message("quarkdown.math.preview.assets.missing")
            else -> QuarkdownBundle.message("quarkdown.math.preview.jcef.unavailable")
        }
        return JBLabel(message, SwingConstants.CENTER).apply { border = JBUI.Borders.empty(16) }
    }

    /**
     * Typesets [tex]. [macros] comes from the document's `.texmacro` declarations (see
     * [QuarkdownLatexPreviewSource.macros]) and [displayMode] selects block layout.
     *
     * Calls are safe at any time: before the page is ready the latest one is remembered and
     * replayed on load.
     */
    fun render(tex: String, macros: Map<String, String>, displayMode: Boolean) {
        val call = QuarkdownLatexPreviewSource.renderCall(tex, macros, displayMode)
        val current = browser ?: return
        if (!pageReady) {
            pendingCall = call
            return
        }
        // CefBrowser is the API that exposes script evaluation; JBCefBrowser only wraps it.
        val serverUrl = assetServer?.baseUrl
        current.cefBrowser.executeJavaScript(call, serverUrl, 0)
    }

    /** The Quarkdown home whose KaTeX build should be used, or `null` when it has none. */
    private fun resolveAssetRoot(): File? {
        val configured = QuarkdownSettings.getInstance(project).state.quarkdownPath
        val home = QuarkdownPathDetector.resolveHome(configured) ?: QuarkdownPathDetector.detect()
        return QuarkdownLatexWebAssets.assetRoot(home?.let { File(it) })
    }

    override fun dispose() {
        pageReady = false
        pendingCall = null
    }

    private companion object {
        /**
         * Relative to the served root, so the page, the renderer and its fonts share one origin
         * (see [QuarkdownLatexAssetServer] for why that matters).
         */
        const val SCRIPT_PATH = QuarkdownLatexWebAssets.SCRIPT_PATH
        const val STYLE_PATH = QuarkdownLatexWebAssets.STYLE_PATH
    }
}

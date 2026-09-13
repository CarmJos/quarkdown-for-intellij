package cc.carm.plugin.intellij.quarkdown.ui.preview

import cc.carm.plugin.intellij.quarkdown.QuarkdownBundle
import cc.carm.plugin.intellij.quarkdown.lang.preview.QuarkdownCli
import cc.carm.plugin.intellij.quarkdown.lang.preview.QuarkdownFormulaRenderer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBLabel
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.Timer
import java.util.concurrent.atomic.AtomicInteger

/**
 * Live formula preview: compiles the given Quarkdown source with the CLI and renders the result
 * in an embedded browser.
 *
 * Typing in the editor dialog calls [render] on every keystroke, so the work is
 * **debounced** — only after the user pauses is the CLI started. Renders are additionally
 * guarded by a generation counter: a compile that finishes after a newer one was scheduled is
 * discarded, which keeps the shown preview in sync with the current input (the CLI takes long
 * enough that out-of-order results would otherwise be visible).
 *
 * When JCEF is unavailable the pane degrades to a hint instead of failing; JCEF is an optional
 * dependency of this plugin.
 */
class QuarkdownFormulaPreviewPane(private val project: Project) : Disposable {

    private val browser: JBCefBrowser? = JcefSupport.createBrowser()
    private val status = JBLabel("").apply { foreground = UIUtil.getContextHelpForeground() }

    /** Bumped on every scheduled render so late results of older renders are ignored. */
    private val generation = AtomicInteger()

    @Volatile
    private var closed = false

    private val debounce = Timer(DEBOUNCE_MS) { compileScheduled() }.apply { isRepeats = false }

    val component: JComponent = JPanel(BorderLayout()).apply {
        preferredSize = JBUI.size(PREFERRED_WIDTH, PREFERRED_HEIGHT)
        border = JBUI.Borders.empty(0, 0, 2, 0)
        add(createContent(), BorderLayout.CENTER)
        add(status, BorderLayout.SOUTH)
    }

    private fun createContent(): JComponent {
        val current = browser
        if (current != null) return current.component
        return JBLabel(
            QuarkdownBundle.message("quarkdown.math.preview.jcef.unavailable"),
            SwingConstants.CENTER,
        )
    }

    /**
     * Schedules a render of [qdSource] (a complete `.qd` document). Rapid successive calls
     * collapse into a single compile of the last source.
     */
    fun render(qdSource: String) {
        pendingSource = qdSource
        generation.incrementAndGet()
        if (closed) return
        if (qdSource.isBlank()) {
            // Nothing to render (empty content): stop instead of starting the CLI pointlessly.
            debounce.stop()
            status.text = ""
            return
        }
        debounce.restart()
    }

    @Volatile
    private var pendingSource: String = ""

    private fun compileScheduled() {
        val source = pendingSource
        val currentGeneration = generation.get()
        if (closed || source.isBlank()) return

        val executable = QuarkdownCli.resolveExecutable(project)
        if (executable == null) {
            status.text = QuarkdownBundle.message("quarkdown.preview.cli.not.found")
            return
        }

        status.text = QuarkdownBundle.message("quarkdown.math.preview.progress")
        ApplicationManager.getApplication().executeOnPooledThread {
            val result = QuarkdownFormulaRenderer.render(project, executable, source, null)
            ApplicationManager.getApplication().invokeLater {
                // A newer render superseded this one, or the dialog is gone: drop the result.
                if (closed || currentGeneration != generation.get()) return@invokeLater
                val page = result.page
                if (page != null) {
                    status.text = ""
                    browser?.loadURL(page.toURI().toString())
                } else {
                    status.text = QuarkdownBundle.message(
                        "quarkdown.math.preview.failed",
                        result.error ?: QuarkdownBundle.message("quarkdown.math.preview.no.output"),
                    )
                }
            }
        }
    }

    override fun dispose() {
        closed = true
        debounce.stop()
        // Invalidate any in-flight render so it can neither touch the browser nor the label.
        generation.incrementAndGet()
        browser?.let { Disposer.dispose(it) }
    }

    private companion object {
        /** Pause after the last keystroke before the CLI is started. */
        const val DEBOUNCE_MS = 700

        const val PREFERRED_WIDTH = 620
        const val PREFERRED_HEIGHT = 220
    }
}

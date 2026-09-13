package cc.carm.plugin.intellij.quarkdown.ui.preview

import cc.carm.plugin.intellij.quarkdown.QuarkdownBundle
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBLabel
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.io.File
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants

/**
 * Shows the page produced by [cc.carm.plugin.intellij.quarkdown.lang.preview.QuarkdownFormulaPreview]
 * — a throwaway document containing a single formula, compiled by the Quarkdown CLI.
 *
 * The page is loaded in the IDE's embedded browser (JCEF). JCEF is an optional dependency
 * (since 2026.2 it lives in the bundled *Web Browser (JCEF)* plugin), so when it is not
 * available the dialog degrades to a short hint plus a button opening the page in the
 * system browser instead of failing.
 */
class QuarkdownFormulaPreviewDialog private constructor(
    private val project: Project,
    private val page: File,
    private val browser: JBCefBrowser?,
) : DialogWrapper(project) {

    init {
        title = QuarkdownBundle.message("quarkdown.math.preview.title")
        setModal(false)
        init()
    }

    override fun createCenterPanel(): JComponent {
        val center = JPanel(BorderLayout())
        center.preferredSize = JBUI.size(PREVIEW_WIDTH, PREVIEW_HEIGHT)

        if (browser != null) {
            center.add(browser.component, BorderLayout.CENTER)
            // The page is local, so no navigation is needed; a plain file URL is enough.
            browser.loadURL(page.toURI().toString())
        } else {
            center.add(createFallbackPanel(), BorderLayout.CENTER)
        }
        return center
    }

    /** Shown when JCEF is unavailable: explain and offer the external browser. */
    private fun createFallbackPanel(): JComponent {
        val message = JBLabel(
            QuarkdownBundle.message("quarkdown.math.preview.jcef.unavailable"),
            SwingConstants.CENTER,
        )
        val openButton = JButton(QuarkdownBundle.message("quarkdown.math.preview.open.browser")).apply {
            addActionListener { BrowserUtil.browse(page.toURI()) }
        }

        val column = JPanel(BorderLayout())
        column.add(message, BorderLayout.CENTER)
        column.add(openButton, BorderLayout.SOUTH)
        return column
    }

    override fun createActions(): Array<javax.swing.Action> = arrayOf(okAction)

    override fun dispose() {
        browser?.let { Disposer.dispose(it) }
        super.dispose()
    }

    companion object {

        private const val PREVIEW_WIDTH = 760
        private const val PREVIEW_HEIGHT = 360

        /**
         * Opens the dialog on the EDT. [browser] creation may fail when JCEF is missing; the
         * dialog then shows its fallback panel.
         */
        fun show(project: Project, page: File) {
            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) return@invokeLater
                // JCEF is optional: `createBrowser` returns null when it is unavailable, and
                // the dialog then shows its external-browser fallback.
                QuarkdownFormulaPreviewDialog(project, page, JcefSupport.createBrowser()).show()
            }
        }
    }
}

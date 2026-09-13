package cc.carm.plugin.intellij.quarkdown.ui.preview

import cc.carm.plugin.intellij.quarkdown.QuarkdownBundle
import cc.carm.plugin.intellij.quarkdown.lang.latex.QuarkdownLatexPreviewSource
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Read-only typeset preview of one formula, opened from the gutter.
 *
 * It is a thin shell around [QuarkdownLatexPreviewView]: the formula is typeset by the KaTeX build
 * from the Quarkdown installation, so the dialog appears (and updates) instantly — there is no
 * compile step behind it.
 */
class QuarkdownLatexPreviewDialog private constructor(
    project: Project,
    tex: String,
    macros: Map<String, String>,
    displayMode: Boolean,
) : DialogWrapper(project) {

    private val view = QuarkdownLatexPreviewView(project, disposable)

    init {
        title = QuarkdownBundle.message("quarkdown.math.preview.title")
        setModal(false)
        init()
        view.render(tex, macros, displayMode)
    }

    override fun createCenterPanel(): JComponent = JPanel().apply {
        layout = java.awt.BorderLayout()
        preferredSize = JBUI.size(PREVIEW_WIDTH, PREVIEW_HEIGHT)
        // A border separates the typeset formula from the dialog, whose background the page shares.
        border = JBUI.Borders.customLine(JBColor.border(), 1)
        add(view.component, java.awt.BorderLayout.CENTER)
    }

    /** Only a close button: this dialog shows a formula, it does not edit anything. */
    override fun createActions(): Array<Action> = arrayOf(cancelAction)

    override fun dispose() {
        // The view is a child of the dialog's disposable, so the platform disposes it (and the
        // embedded browser it owns) first; this covers the paths that dispose the dialog directly.
        Disposer.dispose(view)
        super.dispose()
    }

    companion object {

        private const val PREVIEW_WIDTH = 720
        private const val PREVIEW_HEIGHT = 260

        /**
         * Shows [tex] typeset. [documentText] is used for its `.texmacro` declarations, so custom
         * commands in the formula resolve.
         */
        fun show(project: Project, tex: String, documentText: CharSequence, displayMode: Boolean) {
            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) return@invokeLater
                QuarkdownLatexPreviewDialog(
                    project,
                    tex,
                    QuarkdownLatexPreviewSource.macros(documentText),
                    displayMode,
                ).show()
            }
        }
    }
}


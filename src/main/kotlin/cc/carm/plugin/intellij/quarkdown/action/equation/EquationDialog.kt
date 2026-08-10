package cc.carm.plugin.intellij.quarkdown.action.equation

import cc.carm.plugin.intellij.quarkdown.QuarkdownBundle
import cc.carm.plugin.intellij.quarkdown.lang.equation.QuarkdownEquationSyntax
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.uiDesigner.core.GridConstraints
import com.intellij.uiDesigner.core.GridLayoutManager
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Dialog for editing a Quarkdown equation's cross-reference id.
 *
 * Depending on [kind] the changes are written back either to a standalone `$ ... $ {#id}`
 * equation line or to a fenced `$$$ {#id}` equation opening line:
 * ```
 * $ E = mc^2 $ {#energy}
 * $$$
 *     E = mc^2
 * $$$
 * ```
 *
 * Equations do not have a language (unlike code blocks) and Quarkdown does not define a
 * caption for them, so the id is the only editable attribute.
 */
class EquationDialog(
    private val project: Project?,
    private val kind: QuarkdownEquationSyntax.Kind
) : DialogWrapper(project) {

    /** The original line (EDIT mode, to preserve indentation/delimiters). */
    private var originalLine: String = ""

    private var idField: JBTextField? = null

    init {
        title = QuarkdownBundle.message("quarkdown.dialog.equation.title")
        init()
    }

    /** Pre-populate from an existing `$ ... $ {#id}` equation line. */
    fun parseInline(info: QuarkdownEquationSyntax.InlineInfo) {
        originalLine = info.line
        idField?.text = info.id
    }

    /** Pre-populate from an existing fenced `$$$ {#id}` equation opening line. */
    fun parseFence(info: QuarkdownEquationSyntax.FenceInfo) {
        originalLine = info.line
        idField?.text = info.id
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridLayoutManager(1, 2, JBUI.insets(10), -1, -1))

        val idLabel = JBLabel(QuarkdownBundle.message("quarkdown.dialog.equation.id"))
        val idf = JBTextField()
        idField = idf
        panel.add(
            idLabel, GridConstraints(
                0, 0, 1, 1,
                GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
                null, null, null
            )
        )
        panel.add(
            idf, GridConstraints(
                0, 1, 1, 1,
                GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED,
                null, null, null
            )
        )

        // Keep a comfortable width, matching the code block dialog.
        val natural = panel.preferredSize
        panel.preferredSize = Dimension(maxOf(natural.width, 460), natural.height)

        return panel
    }

    /** Builds the replacement line, preserving indentation and delimiter style. */
    fun buildLine(): String {
        val id = idField?.text?.trim().orEmpty()
        return when (kind) {
            QuarkdownEquationSyntax.Kind.INLINE ->
                QuarkdownEquationSyntax.buildInlineLine(originalLine, id)

            QuarkdownEquationSyntax.Kind.FENCED ->
                QuarkdownEquationSyntax.buildFenceLine(originalLine, id)
        }
    }

    // ------------------------------------------------------------------
    // Test helpers
    // ------------------------------------------------------------------

    fun setIdForTest(id: String) {
        idField?.text = id
    }

    internal fun getIdForTest(): String = idField?.text?.trim().orEmpty()
}

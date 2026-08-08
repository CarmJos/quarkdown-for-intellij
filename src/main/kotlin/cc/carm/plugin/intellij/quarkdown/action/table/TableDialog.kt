package cc.carm.plugin.intellij.quarkdown.action.table

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.uiDesigner.core.GridConstraints
import com.intellij.uiDesigner.core.GridLayoutManager
import com.intellij.util.ui.JBUI
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Dialog for editing a Quarkdown table's label and ID.
 *
 * The label/id are written as a line right below the table, sharing its indentation:
 * ```
 * | name   | address        |
 * |:------:|:-------------:|
 * | Jason   | 192.168.1.100 |
 * "Name with address" {#ip-table}
 * ```
 */
class TableDialog(
    private val project: Project?
) : DialogWrapper(project) {

    /** The original table source lines (for EDIT mode, to compute indent). */
    private var originalLines: List<String> = emptyList()

    private var labelField: JBTextField? = null
    private var idField: JBTextField? = null

    init {
        title = "Table Properties"
        init()
    }

    /** Pre-populate from an existing table and its label/id line (if any). */
    fun parseExistingTable(lines: List<String>, labelLine: String?) {
        originalLines = lines
        val labelMatch = labelLine?.let { Regex("""^\s*"([^"]*)"\s*(?:\{#([^}]+)}\s*)?$""").find(it.trim()) }
        labelField?.text = labelMatch?.groupValues?.get(1)?.trim().orEmpty()
        idField?.text = labelMatch?.groupValues?.get(2)?.trim().orEmpty()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridLayoutManager(2, 2, JBUI.insets(10), -1, -1))
        var row = 0

        val labelLabel = JBLabel("Label:")
        val lf = JBTextField()
        labelField = lf
        panel.add(labelLabel, GridConstraints(row, 0, 1, 1,
            GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
            null, null, null))
        panel.add(lf, GridConstraints(row, 1, 1, 1,
            GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
            GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED,
            null, null, null))
        row++

        val idLabel = JBLabel("ID:")
        val idf = JBTextField()
        idField = idf
        panel.add(idLabel, GridConstraints(row, 0, 1, 1,
            GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
            null, null, null))
        panel.add(idf, GridConstraints(row, 1, 1, 1,
            GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
            GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED,
            null, null, null))
        row++

        return panel
    }

    override fun doOKAction() {
        super.doOKAction()
    }

    /**
     * Builds the full table source lines, plus the label/id line (if either is set),
     * preserving the original indentation.
     */
    fun buildTableLines(): List<String> {
        val label = labelField?.text?.trim().orEmpty()
        val id = idField?.text?.trim().orEmpty()

        val indent = computeIndent()
        val lines = originalLines.toMutableList()
        if (label.isNotEmpty() || id.isNotEmpty()) {
            val sb = StringBuilder(indent)
            if (label.isNotEmpty()) sb.append("\"").append(label).append("\"")
            if (id.isNotEmpty()) {
                if (label.isNotEmpty()) sb.append(" ")
                sb.append("{#").append(id).append("}")
            }
            lines += sb.toString()
        }
        return lines
    }

    /** Test helper: set the label field directly. */
    fun setLabelForTest(label: String) {
        labelField?.text = label
    }

    /** Test helper: set the ID field directly. */
    fun setIdForTest(id: String) {
        idField?.text = id
    }

    /** Recover leading whitespace from the original first line. */
    fun computeIndent(): String {
        val first = originalLines.firstOrNull() ?: return ""
        val idx = first.indexOfFirst { it != ' ' && it != '\t' }
        return if (idx > 0) first.substring(0, idx) else ""
    }
}

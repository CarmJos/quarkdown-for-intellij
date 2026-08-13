package cc.carm.plugin.intellij.quarkdown.action.heading

import cc.carm.plugin.intellij.quarkdown.QuarkdownBundle
import cc.carm.plugin.intellij.quarkdown.lang.heading.QuarkdownHeadingSyntax
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.uiDesigner.core.GridConstraints
import com.intellij.uiDesigner.core.GridLayoutManager
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Dialog for editing a Quarkdown heading's level, content and cross-reference id:
 *
 * ```
 * # Introduction            →    ## Introduction {#intro}
 * ```
 *
 * The "快速提取" button next to the ID field fills it with an id automatically
 * extracted from the heading content, as a starting point the user can then refine.
 */
class HeadingDialog(
    private val project: Project?
) : DialogWrapper(project) {

    /** The original heading line (EDIT mode, to preserve indentation). */
    private var originalLine: String = ""

    private var levelCombo: ComboBox<String>? = null
    private var contentField: JBTextField? = null
    private var idField: JBTextField? = null

    init {
        title = QuarkdownBundle.message("quarkdown.dialog.heading.title")
        init()
    }

    /** Pre-populate from an existing heading line. */
    fun parseHeading(info: QuarkdownHeadingSyntax.HeadingInfo) {
        originalLine = info.line
        levelCombo?.selectedItem = info.level.toString()
        contentField?.text = info.content
        idField?.text = info.id
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridLayoutManager(3, 2, JBUI.insets(10), -1, -1))
        var row = 0

        val levelLabel = JBLabel(QuarkdownBundle.message("quarkdown.dialog.heading.level"))
        val combo = ComboBox(CollectionComboBoxModel(LEVELS, "1"))
        levelCombo = combo
        panel.add(
            levelLabel, GridConstraints(
                row, 0, 1, 1,
                GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
                null, null, null
            )
        )
        panel.add(
            combo, GridConstraints(
                row, 1, 1, 1,
                GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED,
                null, null, null
            )
        )
        row++

        val contentLabel = JBLabel(QuarkdownBundle.message("quarkdown.dialog.heading.content"))
        val cf = JBTextField()
        contentField = cf
        panel.add(
            contentLabel, GridConstraints(
                row, 0, 1, 1,
                GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
                null, null, null
            )
        )
        panel.add(
            cf, GridConstraints(
                row, 1, 1, 1,
                GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED,
                null, null, null
            )
        )
        row++

        val idLabel = JBLabel(QuarkdownBundle.message("quarkdown.dialog.heading.id"))
        val idf = JBTextField()
        idField = idf

        val extract = JButton(QuarkdownBundle.message("quarkdown.dialog.heading.extract"))
        extract.toolTipText = QuarkdownBundle.message("quarkdown.dialog.heading.extract.tooltip")
        extract.addActionListener { fillExtractedId() }

        val idRow = JPanel(GridLayoutManager(1, 2, JBUI.emptyInsets(), 4, -1))
        idRow.add(
            idf, GridConstraints(
                0, 0, 1, 1,
                GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED,
                null, null, null
            )
        )
        idRow.add(
            extract, GridConstraints(
                0, 1, 1, 1,
                GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
                null, null, null
            )
        )

        panel.add(
            idLabel, GridConstraints(
                row, 0, 1, 1,
                GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
                null, null, null
            )
        )
        panel.add(
            idRow, GridConstraints(
                row, 1, 1, 1,
                GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED,
                null, null, null
            )
        )

        // Make the dialog a bit wider so the heading content field is comfortable.
        val natural = panel.preferredSize
        panel.preferredSize = Dimension(maxOf(natural.width, 460), natural.height)

        return panel
    }

    /** Fills the ID field with an id extracted from the current heading content. */
    private fun fillExtractedId() {
        val content = contentField?.text.orEmpty()
        idField?.text = QuarkdownHeadingSyntax.extractIdFromContent(content)
    }

    /** Builds the replacement heading line, preserving indentation. */
    fun buildLine(): String {
        val level = levelCombo?.selectedItem?.toString()?.toIntOrNull() ?: 1
        val content = contentField?.text?.trim().orEmpty()
        val id = idField?.text?.trim().orEmpty()
        return QuarkdownHeadingSyntax.buildHeadingLine(originalLine, level, content, id)
    }

    /** Builds a fresh heading line when inserting a new heading (no original line). */
    fun buildInsertLine(): String {
        val level = levelCombo?.selectedItem?.toString()?.toIntOrNull() ?: 1
        val content = contentField?.text?.trim().orEmpty()
        val id = idField?.text?.trim().orEmpty()
        return QuarkdownHeadingSyntax.buildHeadingInsert(level, content, id)
    }

    // ------------------------------------------------------------------
    // Test helpers
    // ------------------------------------------------------------------

    internal fun getIdForTest(): String =
        idField?.text?.trim().orEmpty()

    companion object {
        private val LEVELS = listOf("1", "2", "3", "4", "5", "6")
    }
}

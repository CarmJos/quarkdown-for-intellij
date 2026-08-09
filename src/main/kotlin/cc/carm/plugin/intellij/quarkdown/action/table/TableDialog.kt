package cc.carm.plugin.intellij.quarkdown.action.table

import cc.carm.plugin.intellij.quarkdown.lang.table.QuarkdownTableModificationUtils
import cc.carm.plugin.intellij.quarkdown.lang.table.QuarkdownTableParser
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.uiDesigner.core.GridConstraints
import com.intellij.uiDesigner.core.GridLayoutManager
import com.intellij.util.ui.JBUI
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Action
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

    /** Live document/block binding so the "Format" button can re-align the source immediately. */
    private var targetDocument: Document? = null
    private var targetBlock: QuarkdownTableModificationUtils.TableBlock? = null

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

    /** Binds the live document so "Format Table" writes the re-aligned table immediately. */
    fun setTarget(document: Document, block: QuarkdownTableModificationUtils.TableBlock) {
        targetDocument = document
        targetBlock = block
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridLayoutManager(2, 2, JBUI.insets(10), -1, -1))
        var row = 0

        val labelLabel = JBLabel("Label:")
        val lf = JBTextField()
        labelField = lf
        panel.add(
            labelLabel, GridConstraints(
                row, 0, 1, 1,
                GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
                null, null, null
            )
        )
        panel.add(
            lf, GridConstraints(
                row, 1, 1, 1,
                GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED,
                null, null, null
            )
        )
        row++

        val idLabel = JBLabel("ID:")
        val idf = JBTextField()
        idField = idf
        panel.add(
            idLabel, GridConstraints(
                row, 0, 1, 1,
                GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
                null, null, null
            )
        )
        panel.add(
            idf, GridConstraints(
                row, 1, 1, 1,
                GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED,
                null, null, null
            )
        )

        return panel
    }

    /**
     * Puts the "Format Table" button on the same row as the OK/Cancel buttons.
     *
     * The platform places actions returned here on the left side of the dialog's
     * bottom button bar, right next to OK/Cancel.
     */
    override fun createLeftSideActions(): Array<Action> {
        return arrayOf(
            object : AbstractAction("Format Table") {
                init {
                    putValue(SHORT_DESCRIPTION, "Re-align the table cells")
                }

                override fun actionPerformed(e: ActionEvent) {
                    formatTable()
                }
            }
        )
    }

    override fun doOKAction() {
        super.doOKAction()
    }

    /**
     * Re-aligns the table (pads every cell to a common column width).
     *
     * When a live document is bound (via [setTarget]) the formatted source is written
     * into the editor right away, so the change is visible while the dialog stays open.
     * Otherwise the internal lines are re-formatted and applied on OK.
     *
     * The table is re-resolved against the current document text on every click: the
     * previous write changes the table's length, so reusing the block captured at
     * dialog-open time would replace a stale offset range and corrupt the source.
     */
    fun formatTable() {
        val document = targetDocument ?: run {
            // No live binding (standalone dialog / unit tests): format the internal lines only.
            originalLines = formatLines(originalLines)
            return
        }
        val block = resolveBlock(document) ?: return
        WriteCommandAction.runWriteCommandAction(project) {
            QuarkdownTableModificationUtils.formatTable(project, document, block)
        }
        refreshFromDocument()
        // The write happens while this modal dialog is open; nudge the daemon so the
        // table bars re-collect immediately instead of only when the dialog closes.
        project?.let { DaemonCodeAnalyzer.getInstance(it).restart() }
    }

    /**
     * Re-resolves the edited table against the current document text so its absolute
     * offsets are never stale. The live "Format" write changes the table's length, so
     * the block captured at dialog-open time must not be reused for later writes.
     */
    private fun resolveBlock(document: Document): QuarkdownTableModificationUtils.TableBlock? {
        val anchor = targetBlock ?: return null
        return QuarkdownTableModificationUtils.findTableBlocks(document.immutableCharSequence)
            .firstOrNull { it.startOffset == anchor.startOffset }
    }

    /** Builds the full table source lines, plus the label/id line (if either is set),
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
    fun computeIndent(): String = indentOf(originalLines)

    /** Re-reads the (possibly re-aligned) table block after a live format. */
    private fun refreshFromDocument() {
        val document = targetDocument ?: return
        val block = targetBlock ?: return
        val text = document.immutableCharSequence
        val refreshed = QuarkdownTableModificationUtils.findTableBlocks(text)
            .firstOrNull { it.startOffset == block.startOffset } ?: return
        originalLines = refreshed.lines
        // Keep the anchor in sync so later formats re-resolve to the current offsets.
        targetBlock = refreshed
    }

    /** Re-parses and rebuilds [lines] into an aligned table, preserving indentation. */
    private fun formatLines(lines: List<String>): List<String> {
        val parsed = QuarkdownTableParser.parse(lines) ?: return lines
        val indent = indentOf(lines)
        return QuarkdownTableParser.build(parsed).map { indent + it }
    }

    private fun indentOf(lines: List<String>): String {
        val first = lines.firstOrNull() ?: return ""
        val idx = first.indexOfFirst { it != ' ' && it != '\t' }
        return if (idx > 0) first.substring(0, idx) else ""
    }
}

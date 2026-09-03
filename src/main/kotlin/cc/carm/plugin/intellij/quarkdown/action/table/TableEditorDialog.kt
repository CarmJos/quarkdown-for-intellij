package cc.carm.plugin.intellij.quarkdown.action.table

import cc.carm.plugin.intellij.quarkdown.QuarkdownBundle
import cc.carm.plugin.intellij.quarkdown.QuarkdownIcons
import cc.carm.plugin.intellij.quarkdown.lang.table.QuarkdownTableByRows
import cc.carm.plugin.intellij.quarkdown.lang.table.QuarkdownTableModificationUtils
import cc.carm.plugin.intellij.quarkdown.lang.table.QuarkdownTableParser
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import net.miginfocom.swing.MigLayout
import java.awt.Component
import java.awt.Cursor
import java.awt.Font
import java.awt.FontMetrics
import java.awt.Graphics
import java.awt.Point
import java.awt.event.ActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.ButtonGroup
import javax.swing.DefaultCellEditor
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JTable
import javax.swing.JTextArea
import javax.swing.ScrollPaneConstants
import javax.swing.SwingConstants
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.table.AbstractTableModel
import javax.swing.table.TableCellRenderer
import javax.swing.table.TableModel

/**
 * Spreadsheet-style editor for Quarkdown tables, opened from the table gutter icon.
 *
 * Edits the table data in a grid (first row = header) and writes the result back in
 * one of two formats, converting between them as needed:
 *  - **Markdown** pipe tables (`| a | b |`), which support captions (`"label" {#id}`)
 *    and per-column alignment;
 *  - **Quarkdown `.tablebyrows`** calls, whose headers are a literal list or a
 *    reference to a `.var`-defined list (the reference is preserved while unchanged).
 *
 * Captions and ids are a pipe-table-only feature: when the output format is
 * `.tablebyrows` the fields stay editable (switching back restores them) but are not
 * written, and a warning is shown while they are non-empty.
 */
class TableEditorDialog(private val project: Project?) : DialogWrapper(project) {

    /** The source syntax the edited table is written back as. */
    enum class OutputFormat { MARKDOWN, TABLE_BY_ROWS }

    private companion object {
        const val RESIZE_NONE = 0
        const val RESIZE_COLUMN = 1
        const val RESIZE_ROW = 2

        /** Pixel tolerance for grabbing a column/row border. */
        const val EDGE_TOLERANCE = 4

        /** A cell wider than this many characters wraps instead of stretching the column. */
        const val MAX_CELL_CHARS = 16

        val MIN_COLUMN_WIDTH: Int = JBUI.scale(24)
        val MIN_ROW_HEIGHT: Int = JBUI.scale(18)

        val HEADER_BACKGROUND = JBColor(0xF5F5F5, 0x3C3F41)
        val HEADER_SEPARATOR = JBColor(0x9AA0A6, 0x5E6060)
    }

    // ------------------------------------------------------------------
    // State
    // ------------------------------------------------------------------

    /** Grid data; row 0 is always the header row. */
    private val gridRows = mutableListOf<MutableList<String>>()

    /** Named to avoid clashing with [GridTableModel.getColumnCount] (virtual dispatch recursion). */
    private var gridColumnCount = 1
    private var outputFormat = OutputFormat.MARKDOWN

    /** Column alignments (Markdown output only). */
    private val alignments = mutableListOf<QuarkdownTableParser.Alignment>()

    /** Name of a `.var` headers reference to preserve, with its original content. */
    private var headersReference: String? = null
    private var originalHeaderSignature: List<String> = emptyList()

    /** Indentation of the original block, re-applied to every written line. */
    private var originalIndent = ""

    /** Live document binding so "Format Table" can apply the edits immediately. */
    private var targetDocument: Document? = null
    private var targetStartOffset: Int = -1

    // ------------------------------------------------------------------
    // UI
    // ------------------------------------------------------------------

    private val gridModel = GridTableModel()
    private val grid = EditorGrid(gridModel)
    private val markdownRadio = JBRadioButton(QuarkdownBundle.message("quarkdown.dialog.table.format.markdown"))
    private val tableByRowsRadio = JBRadioButton(QuarkdownBundle.message("quarkdown.dialog.table.format.tablebyrows"))
    private val labelField = JBTextField()
    private val idField = JBTextField()
    private val captionWarning = JBLabel(
        QuarkdownBundle.message("quarkdown.dialog.table.caption.warning"),
        AllIcons.General.Warning,
        SwingConstants.LEADING
    )

    // Manual column-width / row-height resizing state.
    private var resizeMode: Int = RESIZE_NONE
    private var resizeIndex: Int = -1
    private var resizeOrigin: Int = 0
    private var resizeOriginalSize: Int = 0

    private val captionWatcher = object : DocumentListener {
        private fun update() = updateCaptionWarning()
        override fun insertUpdate(e: DocumentEvent) = update()
        override fun removeUpdate(e: DocumentEvent) = update()
        override fun changedUpdate(e: DocumentEvent) = update()
    }

    init {
        title = QuarkdownBundle.message("quarkdown.dialog.table.title")
        markdownRadio.addActionListener { onFormatChanged() }
        tableByRowsRadio.addActionListener { onFormatChanged() }
        ButtonGroup().apply {
            add(markdownRadio)
            add(tableByRowsRadio)
        }
        markdownRadio.isSelected = true
        labelField.document.addDocumentListener(captionWatcher)
        idField.document.addDocumentListener(captionWatcher)
        // Must run before DialogWrapper.init()/createCenterPanel(): it removes the
        // A/B/C column header so the scroll pane does not re-attach it.
        installGridUi()
        init()
        isResizable = true
    }

    override fun getPreferredFocusedComponent(): JComponent = grid

    override fun createCenterPanel(): JComponent {
        grid.preferredScrollableViewportSize = JBUI.size(620, 380)
        grid.setVisibleRowCount(10)

        val formatRow = JPanel(MigLayout("insets 0"))
        formatRow.add(JBLabel(QuarkdownBundle.message("quarkdown.dialog.table.output.format")))
        formatRow.add(markdownRadio)
        formatRow.add(tableByRowsRadio, "gapleft 8")

        val scrollPane = JBScrollPane(grid)
        scrollPane.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
        scrollPane.verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
        scrollPane.border = JBUI.Borders.customLine(JBColor.border(), 1)

        val metaRow = JPanel(MigLayout("insets 0"))
        metaRow.add(JBLabel(QuarkdownBundle.message("quarkdown.dialog.table.label")))
        metaRow.add(labelField, "width 240!, gapright 16")
        metaRow.add(JBLabel(QuarkdownBundle.message("quarkdown.dialog.table.id")))
        metaRow.add(idField, "width 180!")

        captionWarning.isVisible = false

        val panel = JPanel(MigLayout("insets 10, wrap 1, fillx"))
        panel.add(formatRow, "growx")
        panel.add(createOperationsToolbar(), "growx")
        panel.add(scrollPane, "grow, push")
        panel.add(metaRow, "growx")
        panel.add(captionWarning, "growx")
        return panel
    }

    /**
     * A horizontal toolbar of explicit row/column operations: insert/delete/move rows
     * and insert/delete columns, acting on the current grid selection.
     */
    private fun createOperationsToolbar(): JComponent {
        val group = DefaultActionGroup().apply {
            add(gridAction("quarkdown.dialog.table.insert.row.above", QuarkdownIcons.TABLE_ADD_ROW_ABOVE) {
                insertRowAt((grid.selectedRow.takeIf { it > 0 } ?: gridRows.size).coerceAtLeast(1))
            })
            add(gridAction("quarkdown.dialog.table.insert.row.below", QuarkdownIcons.TABLE_ADD_ROW_BELOW) {
                insertRowAt((grid.selectedRow.takeIf { it >= 0 } ?: gridRows.size - 1) + 1)
            })
            add(gridAction("quarkdown.dialog.table.delete.row", QuarkdownIcons.TABLE_REMOVE) {
                deleteRow(grid.selectedRow)
            })
            add(gridAction("quarkdown.table.move.row.up", QuarkdownIcons.TABLE_MOVE_ROW_UP) {
                moveSelectedRow(-1)
            })
            add(gridAction("quarkdown.table.move.row.down", QuarkdownIcons.TABLE_MOVE_ROW_DOWN) {
                moveSelectedRow(1)
            })
            addSeparator()
            add(gridAction("quarkdown.dialog.table.insert.column.left", QuarkdownIcons.TABLE_ADD_COLUMN_LEFT) {
                addColumnAt(grid.selectedColumn.takeIf { it >= 0 } ?: gridColumnCount)
            })
            add(gridAction("quarkdown.dialog.table.insert.column.right", QuarkdownIcons.TABLE_ADD_COLUMN_RIGHT) {
                addColumnAt((grid.selectedColumn.takeIf { it >= 0 } ?: gridColumnCount - 1) + 1)
            })
            add(gridAction("quarkdown.dialog.table.delete.column", QuarkdownIcons.TABLE_REMOVE) {
                removeColumnAt(grid.selectedColumn)
            })
        }
        val toolbar = ActionManager.getInstance().createActionToolbar("QuarkdownTableEditorGrid", group, true)
        toolbar.targetComponent = grid
        return toolbar.component
    }

    /** Builds a small toolbar action that invokes [action] with the given label and icon. */
    private fun gridAction(messageKey: String, icon: Icon, action: () -> Unit): AnAction =
        object : AnAction({ QuarkdownBundle.message(messageKey) }, { QuarkdownBundle.message(messageKey) }, icon) {
            override fun actionPerformed(e: AnActionEvent) = action()
            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
        }

    /**
     * Puts the "Format Table" button on the same row as the OK/Cancel buttons,
     * keeping the behaviour of the previous table dialog.
     */
    override fun createLeftSideActions(): Array<Action> {
        return arrayOf(
            object : AbstractAction(QuarkdownBundle.message("quarkdown.dialog.table.format")) {
                init {
                    putValue(SHORT_DESCRIPTION, QuarkdownBundle.message("quarkdown.dialog.table.format.description"))
                }

                override fun actionPerformed(e: ActionEvent) {
                    formatTable()
                }
            }
        )
    }

    // ------------------------------------------------------------------
    // Live document binding ("Format Table")
    // ------------------------------------------------------------------

    /** Binds the live document so [formatTable] writes the edited table immediately. */
    fun setTarget(document: Document, blockStartOffset: Int) {
        targetDocument = document
        targetStartOffset = blockStartOffset
    }

    /**
     * Applies the current grid content to the bound document without closing the
     * dialog. The block is re-resolved against the current document text on every
     * click (its length and even its kind may have changed through a previous live
     * write), so the replacement never uses stale offsets.
     *
     * Without a live binding (standalone dialog / unit tests) the grid cells are
     * normalized (trimmed) in place instead.
     */
    fun formatTable() {
        val document = targetDocument ?: run {
            for (row in gridRows.indices) {
                for (col in gridRows[row].indices) {
                    gridRows[row][col] = gridRows[row][col].trim()
                }
            }
            gridModel.fireTableDataChanged()
            return
        }
        val range = resolveTargetRange(document) ?: return
        val replacement = buildReplacementLines().joinToString("\n")
        WriteCommandAction.runWriteCommandAction(project) {
            document.replaceString(range.first, range.second, replacement)
        }
        // The write happens while this modal dialog is open; nudge the daemon so the
        // table bars (inlay hints) re-collect immediately instead of only on close.
        val targetProject = project ?: return
        val psiFile = PsiDocumentManager.getInstance(targetProject).getPsiFile(document) ?: return
        nudgeDaemonRestart(targetProject, psiFile)
    }

    /**
     * Re-resolves the edited block against the current document text. Both kinds are
     * checked: a live write may have converted the block (Markdown ⇄ `.tablebyrows`).
     */
    private fun resolveTargetRange(document: Document): Pair<Int, Int>? {
        if (targetStartOffset < 0) return null
        val text = document.immutableCharSequence
        QuarkdownTableModificationUtils.findTableBlocks(text)
            .firstOrNull { it.startOffset == targetStartOffset }
            ?.let { block ->
                val end = if (block.labelLineStart >= 0) block.fullEndOffset else block.endOffset
                return block.startOffset to end
            }
        QuarkdownTableByRows.findBlocks(text)
            .firstOrNull { it.startOffset == targetStartOffset }
            ?.let { block -> return block.startOffset to block.endOffset }
        return null
    }

    /**
     * Forces the daemon to re-highlight [psiFile] so the table bars (inlay hints)
     * re-collect immediately. IDEA 2026.2+ deprecated `DaemonCodeAnalyzer.restart(PsiFile)`
     * in favour of `restart(PsiFile, Object reason)`, but the new overload is not present
     * in the 2025.2 compile SDK. To stay compatible with both generations without
     * referencing a deprecated method in the bytecode, the matching `restart` overload is
     * resolved and invoked reflectively: `restart(PsiFile, Object)` when available, else
     * `restart(PsiFile)`.
     */
    private fun nudgeDaemonRestart(project: Project, psiFile: PsiFile) {
        // Never restart the daemon in unit tests: the asynchronous re-highlight leaks
        // temp-file handles and breaks the test fixture teardown on Windows.
        if (com.intellij.openapi.application.ApplicationManager.getApplication().isUnitTestMode) return
        val daemon = DaemonCodeAnalyzer.getInstance(project)
        val methods = daemon.javaClass.methods
        val modern = methods.firstOrNull {
            it.name == "restart" && it.parameterCount == 2 && it.parameterTypes[0] == PsiFile::class.java
        }
        val legacy = methods.firstOrNull {
            it.name == "restart" && it.parameterCount == 1 && it.parameterTypes[0] == PsiFile::class.java
        }
        val method = modern ?: legacy ?: return
        val args: Array<Any> = if (method === modern) {
            arrayOf(psiFile, "quarkdown table format")
        } else {
            arrayOf(psiFile)
        }
        method.invoke(daemon, *args)
    }

    // ------------------------------------------------------------------
    // Loading
    // ------------------------------------------------------------------

    /** Populates the editor from a parsed Markdown pipe table and its optional caption line. */
    fun loadMarkdownTable(table: QuarkdownTableParser.Table, labelLine: String?, indent: String) {
        originalIndent = indent
        gridColumnCount = maxOf(1, table.columnCount)
        gridRows.clear()
        gridRows += padded(table.headers)
        for (row in table.rows) gridRows += padded(row)
        alignments.clear()
        alignments += table.alignments

        val labelMatch = labelLine?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { QuarkdownTableModificationUtils.labelLineRegex.matchEntire(it) }
        labelField.text = labelMatch?.groupValues?.get(1)?.trim().orEmpty()
        idField.text = labelMatch?.groupValues?.get(2)?.trim().orEmpty()

        selectFormat(OutputFormat.MARKDOWN)
        // Rebuild the column model: without this the grid keeps the column count it
        // was constructed with and only shows one column.
        gridModel.fireTableStructureChanged()
        autoSizeGrid()
    }

    /** Populates the editor from a parsed `.tablebyrows` table. */
    fun loadTableByRows(table: QuarkdownTableParser.Table, headersReference: String?, indent: String) {
        originalIndent = indent
        this.headersReference = headersReference
        originalHeaderSignature = table.headers
        gridColumnCount = maxOf(1, table.headers.size, table.rows.maxOfOrNull { it.size } ?: 0)
        gridRows.clear()
        // Row 0 is always the header: a headerless call gets a fresh empty header row
        // (leaving it blank keeps the output headerless).
        gridRows += padded(table.headers)
        for (row in table.rows) gridRows += padded(row)
        if (gridRows.size <= 1) gridRows += newEmptyRow()
        alignments.clear()

        selectFormat(OutputFormat.TABLE_BY_ROWS)
        gridModel.fireTableStructureChanged()
        autoSizeGrid()
    }

    // ------------------------------------------------------------------
    // Output
    // ------------------------------------------------------------------

    /** The format selected in the dialog. */
    fun getOutputFormat(): OutputFormat = outputFormat

    /** The caption/id field content (only written for Markdown output). */
    fun getResultId(): String = idField.text.trim()

    /** Builds the table source lines (without the caption line) in the selected format. */
    fun buildOutputLines(): List<String> {
        // Row 0 is always the header. A headerless `.tablebyrows` table is produced
        // when every header cell is left blank.
        val headerRow = currentRow(0)
        val dataRows = (1 until gridRows.size).map { currentRow(it) }
        return when (outputFormat) {
            OutputFormat.MARKDOWN -> {
                val table = QuarkdownTableParser.Table(headerRow, dataRows, alignments)
                QuarkdownTableParser.build(table).map { originalIndent + it }
            }

            OutputFormat.TABLE_BY_ROWS -> {
                // Keep the original `.var` wiring while the headers are untouched.
                val keepReference = headersReference?.takeIf { headerRow == originalHeaderSignature }
                // A body-less `.tablebyrows` call is not useful; keep one row minimum.
                val rows = if (dataRows.isEmpty()) listOf(List(gridColumnCount) { "" }) else dataRows
                val table = QuarkdownTableParser.Table(headerRow, rows, emptyList())
                QuarkdownTableByRows.build(table, originalIndent, keepReference)
            }
        }
    }

    /** Builds the trailing `"caption" {#id}` line, or `null` when absent or not applicable. */
    fun buildLabelLine(): String? {
        if (outputFormat != OutputFormat.MARKDOWN) return null
        val label = labelField.text.trim()
        val id = idField.text.trim()
        if (label.isEmpty() && id.isEmpty()) return null
        val sb = StringBuilder(originalIndent)
        if (label.isNotEmpty()) sb.append("\"").append(label).append("\"")
        if (id.isNotEmpty()) {
            if (label.isNotEmpty()) sb.append(" ")
            sb.append("{#").append(id).append("}")
        }
        return sb.toString()
    }

    /** The full replacement for the original block: table lines plus the caption line. */
    fun buildReplacementLines(): List<String> = buildOutputLines() + listOfNotNull(buildLabelLine())

    // ------------------------------------------------------------------
    // Format / headers switching
    // ------------------------------------------------------------------

    /** Selects the output format (radio buttons + state). */
    fun selectFormat(format: OutputFormat) {
        when (format) {
            OutputFormat.MARKDOWN -> markdownRadio.isSelected = true
            OutputFormat.TABLE_BY_ROWS -> tableByRowsRadio.isSelected = true
        }
        onFormatChanged()
    }

    private fun onFormatChanged() {
        outputFormat = if (markdownRadio.isSelected) OutputFormat.MARKDOWN else OutputFormat.TABLE_BY_ROWS
        updateCaptionWarning()
    }

    private fun updateCaptionWarning() {
        captionWarning.isVisible = outputFormat == OutputFormat.TABLE_BY_ROWS &&
                (labelField.text.isNotBlank() || idField.text.isNotBlank())
    }

    // ------------------------------------------------------------------
    // Grid UI
    // ------------------------------------------------------------------

    private fun installGridUi() {
        // Hide the A/B/C column-letter header: row 0 is the table's real header.
        grid.tableHeader = null
        grid.setShowGrid(true)
        grid.gridColor = JBColor(0xDDDDDD, 0x484A4D)
        grid.autoResizeMode = JTable.AUTO_RESIZE_OFF
        grid.setDefaultRenderer(Any::class.java, WrappingCellRenderer())
        grid.setDefaultEditor(Any::class.java, DefaultCellEditor(JBTextField()))

        // Right-click context menu (row/column/alignment operations).
        grid.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (startResize(e)) {
                    e.consume()
                    return
                }
                maybeShowPopup(e)
            }

            override fun mouseReleased(e: MouseEvent) {
                resizeMode = RESIZE_NONE
                resizeIndex = -1
                maybeShowPopup(e)
            }

            private fun maybeShowPopup(e: MouseEvent) {
                if (!e.isPopupTrigger) return
                val row = grid.rowAtPoint(e.point)
                val column = grid.columnAtPoint(e.point)
                if (row < 0 || column < 0) return
                if (!grid.isCellSelected(row, column)) grid.changeSelection(row, column, false, false)
                createGridPopup(row, column).show(grid, e.x, e.y)
            }
        })

        // Manual column-width / row-height resizing by dragging cell borders.
        grid.addMouseMotionListener(object : MouseMotionAdapter() {
            override fun mouseMoved(e: MouseEvent) = updateResizeCursor(e.point)
            override fun mouseDragged(e: MouseEvent) {
                if (resizeMode != RESIZE_NONE) applyResize(e.point)
            }
        })
    }

    // ------------------------------------------------------------------
    // Manual resizing
    // ------------------------------------------------------------------

    /** Starts a column/row resize if the press is on a cell border; returns true then. */
    private fun startResize(e: MouseEvent): Boolean {
        if (e.button != MouseEvent.BUTTON1) return false
        val columnEdge = columnEdgeAt(e.point)
        if (columnEdge >= 0) {
            resizeMode = RESIZE_COLUMN
            resizeIndex = columnEdge
            resizeOrigin = e.x
            resizeOriginalSize = grid.columnModel.getColumn(columnEdge).width
            return true
        }
        val rowEdge = rowEdgeAt(e.point)
        if (rowEdge >= 0) {
            resizeMode = RESIZE_ROW
            resizeIndex = rowEdge
            resizeOrigin = e.y
            resizeOriginalSize = grid.getRowHeight(rowEdge)
            return true
        }
        return false
    }

    private fun applyResize(p: Point) {
        when (resizeMode) {
            RESIZE_COLUMN -> {
                val newSize = (resizeOriginalSize + (p.x - resizeOrigin)).coerceAtLeast(MIN_COLUMN_WIDTH)
                val column = grid.columnModel.getColumn(resizeIndex)
                column.width = newSize
                column.preferredWidth = newSize
            }

            RESIZE_ROW -> {
                val newSize = (resizeOriginalSize + (p.y - resizeOrigin)).coerceAtLeast(MIN_ROW_HEIGHT)
                grid.setRowHeight(resizeIndex, newSize)
            }
        }
    }

    private fun updateResizeCursor(p: Point) {
        grid.cursor = when {
            columnEdgeAt(p) >= 0 -> Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR)
            rowEdgeAt(p) >= 0 -> Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR)
            else -> Cursor.getDefaultCursor()
        }
    }

    /** The column whose right border is under [p], or the one left of a left border. */
    private fun columnEdgeAt(p: Point): Int {
        if (gridColumnCount == 0 || grid.rowCount == 0) return -1
        val column = grid.columnAtPoint(p)
        if (column < 0) {
            val last = gridColumnCount - 1
            val rect = grid.getCellRect(0, last, false)
            return if (kotlin.math.abs(p.x - (rect.x + rect.width)) <= EDGE_TOLERANCE) last else -1
        }
        val rect = grid.getCellRect(0, column, false)
        if (kotlin.math.abs(p.x - (rect.x + rect.width)) <= EDGE_TOLERANCE) return column
        if (column > 0 && kotlin.math.abs(p.x - rect.x) <= EDGE_TOLERANCE) return column - 1
        return -1
    }

    /** The row whose bottom border is under [p], or the one above a top border. */
    private fun rowEdgeAt(p: Point): Int {
        if (gridRows.isEmpty()) return -1
        val row = grid.rowAtPoint(p)
        if (row < 0) {
            val last = gridRows.size - 1
            val rect = grid.getCellRect(last, 0, false)
            return if (kotlin.math.abs(p.y - (rect.y + rect.height)) <= EDGE_TOLERANCE) last else -1
        }
        val rect = grid.getCellRect(row, 0, false)
        if (kotlin.math.abs(p.y - (rect.y + rect.height)) <= EDGE_TOLERANCE) return row
        if (row > 0 && kotlin.math.abs(p.y - rect.y) <= EDGE_TOLERANCE) return row - 1
        return -1
    }

    // ------------------------------------------------------------------
    // Automatic sizing
    // ------------------------------------------------------------------

    /**
     * Sizes every column to fit its content and every row to fit its (wrapped) content.
     * Cells longer than [MAX_CELL_CHARS] characters do not stretch their column; they
     * wrap onto more lines and grow the row height instead.
     */
    private fun autoSizeGrid() {
        grid.autoResizeMode = JTable.AUTO_RESIZE_OFF
        val fontMetrics = grid.getFontMetrics(grid.font)
        val cellPadding = JBUI.scale(6)

        for (column in 0 until gridColumnCount) {
            var maxChars = 1
            for (row in gridRows.indices) {
                val value = gridRows[row].getOrNull(column).orEmpty()
                maxChars = maxOf(maxChars, minOf(value.length, MAX_CELL_CHARS))
            }
            val width = fontMetrics.stringWidth("0".repeat(maxChars)) + cellPadding * 2
            val tableColumn = grid.columnModel.getColumn(column)
            tableColumn.minWidth = MIN_COLUMN_WIDTH
            tableColumn.width = width.coerceAtLeast(MIN_COLUMN_WIDTH)
            tableColumn.preferredWidth = width.coerceAtLeast(MIN_COLUMN_WIDTH)
        }

        for (row in gridRows.indices) {
            var maxLines = 1
            for (column in 0 until gridColumnCount) {
                val value = gridRows[row].getOrNull(column).orEmpty()
                val columnWidth = grid.columnModel.getColumn(column).width - cellPadding
                maxLines = maxOf(maxLines, wrappedLineCount(value, columnWidth, fontMetrics))
            }
            grid.setRowHeight(row, maxLines * fontMetrics.height + JBUI.scale(4))
        }
    }

    /** Number of lines [text] occupies when wrapped to [widthPx] pixels. */
    private fun wrappedLineCount(text: String, widthPx: Int, fontMetrics: FontMetrics): Int {
        if (text.isEmpty() || widthPx <= 0) return 1
        var lines = 1
        var currentWidth = 0
        for (ch in text) {
            val charWidth = fontMetrics.charWidth(ch)
            if (currentWidth + charWidth > widthPx) {
                lines++
                currentWidth = charWidth
            } else {
                currentWidth += charWidth
            }
        }
        return lines
    }

    /** Context menu with row/column operations that the toolbar buttons do not cover. */
    private fun createGridPopup(row: Int, column: Int): JPopupMenu {
        val popup = JPopupMenu()
        popup.add(menuItem("quarkdown.dialog.table.insert.row.above", QuarkdownIcons.TABLE_ADD_ROW_ABOVE) {
            insertRowAt(row)
        })
        popup.add(menuItem("quarkdown.dialog.table.insert.row.below", QuarkdownIcons.TABLE_ADD_ROW_BELOW) {
            insertRowAt(row + 1)
        })
        popup.add(menuItem("quarkdown.dialog.table.delete.row", QuarkdownIcons.TABLE_REMOVE) {
            deleteRow(row)
        })
        popup.addSeparator()
        popup.add(menuItem("quarkdown.dialog.table.insert.column.left", QuarkdownIcons.TABLE_ADD_COLUMN_LEFT) {
            addColumnAt(column)
        })
        popup.add(menuItem("quarkdown.dialog.table.insert.column.right", QuarkdownIcons.TABLE_ADD_COLUMN_RIGHT) {
            addColumnAt(column + 1)
        })
        popup.add(menuItem("quarkdown.dialog.table.delete.column", QuarkdownIcons.TABLE_REMOVE) {
            removeColumnAt(column)
        })
        popup.addSeparator()
        val alignMenu = javax.swing.JMenu(QuarkdownBundle.message("quarkdown.dialog.table.align"))
        alignMenu.add(menuItem("quarkdown.dialog.table.align.left", QuarkdownIcons.TABLE_ALIGN_LEFT) {
            setColumnAlignment(column, QuarkdownTableParser.Alignment.LEFT)
        })
        alignMenu.add(menuItem("quarkdown.dialog.table.align.center", QuarkdownIcons.TABLE_ALIGN_CENTER) {
            setColumnAlignment(column, QuarkdownTableParser.Alignment.CENTER)
        })
        alignMenu.add(menuItem("quarkdown.dialog.table.align.right", QuarkdownIcons.TABLE_ALIGN_RIGHT) {
            setColumnAlignment(column, QuarkdownTableParser.Alignment.RIGHT)
        })
        alignMenu.add(menuItem("quarkdown.dialog.table.align.none", null) {
            setColumnAlignment(column, QuarkdownTableParser.Alignment.NONE)
        })
        popup.add(alignMenu)
        return popup
    }

    private fun menuItem(
        key: String,
        icon: javax.swing.Icon?,
        action: () -> Unit
    ): JMenuItem = JMenuItem(QuarkdownBundle.message(key), icon).apply {
        addActionListener { action() }
    }

    /**
     * The grid itself: no column-letter header, and a horizontal separator drawn
     * between the header row (row 0) and the data rows.
     */
    private inner class EditorGrid(model: TableModel) : JBTable(model) {
        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            if (rowCount > 0) {
                val separatorY = getRowHeight(0)
                g.color = HEADER_SEPARATOR
                g.fillRect(0, separatorY - JBUI.scale(1), width, JBUI.scale(2))
            }
        }
    }

    /**
     * A wrapping cell renderer: long text wraps onto multiple lines (the row heights
     * computed by [autoSizeGrid] make room for it). Row 0 is rendered in bold on a
     * subtle background to mark it as the table header.
     */
    private inner class WrappingCellRenderer : JTextArea(), TableCellRenderer {
        init {
            isEditable = false
            lineWrap = true
            wrapStyleWord = false
            border = JBUI.Borders.empty(2, 6)
        }

        override fun getTableCellRendererComponent(
            table: JTable,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int
        ): Component {
            text = value?.toString().orEmpty()
            val isHeaderRow = row == 0
            font = table.font.deriveFont(if (isHeaderRow) Font.BOLD else Font.PLAIN)
            if (isSelected) {
                background = table.selectionBackground
                foreground = table.selectionForeground
            } else {
                background = if (isHeaderRow) HEADER_BACKGROUND else table.background
                foreground = table.foreground
            }
            return this
        }
    }

    private inner class GridTableModel : AbstractTableModel() {
        override fun getRowCount(): Int = gridRows.size
        override fun getColumnCount(): Int = gridColumnCount
        override fun getColumnName(column: Int): String = spreadsheetColumnName(column)
        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = true
        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any =
            gridRows.getOrNull(rowIndex)?.getOrNull(columnIndex) ?: ""

        override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
            val row = gridRows.getOrNull(rowIndex) ?: return
            while (row.size <= columnIndex) row.add("")
            row[columnIndex] = aValue?.toString().orEmpty()
        }
    }

    // ------------------------------------------------------------------
    // Grid operations
    // ------------------------------------------------------------------

    private fun newEmptyRow(): MutableList<String> = MutableList(gridColumnCount) { "" }

    private fun padded(row: List<String>): MutableList<String> {
        val cells = row.take(gridColumnCount).toMutableList()
        while (cells.size < gridColumnCount) cells.add("")
        return cells
    }

    /** The trimmed content of grid row [row], normalized to [gridColumnCount] cells. */
    private fun currentRow(row: Int): List<String> =
        (0 until gridColumnCount).map { gridRows.getOrNull(row)?.getOrNull(it)?.trim().orEmpty() }

    private fun addRow() {
        gridRows += newEmptyRow()
        gridModel.fireTableRowsInserted(gridRows.size - 1, gridRows.size - 1)
        autoSizeGrid()
        grid.changeSelection(gridRows.size - 1, 0, false, false)
    }

    private fun insertRowAt(index: Int) {
        // Index 0 is the header row: rows are inserted below it at the earliest.
        val idx = index.coerceIn(1, gridRows.size)
        gridRows.add(idx, newEmptyRow())
        gridModel.fireTableDataChanged()
        autoSizeGrid()
        grid.changeSelection(idx, 0, false, false)
    }

    private fun deleteRow(row: Int) {
        if (row !in gridRows.indices) return
        // The header row is not removable, and one data row must always remain.
        if (row == 0) return
        if (gridRows.size <= 2) return
        gridRows.removeAt(row)
        gridModel.fireTableDataChanged()
        autoSizeGrid()
    }

    private fun moveSelectedRow(delta: Int) {
        val row = grid.selectedRow
        if (row < 0) return
        val other = row + delta
        if (other !in gridRows.indices) return
        // The header row never moves.
        if (row == 0 || other == 0) return
        val tmp = gridRows[row]
        gridRows[row] = gridRows[other]
        gridRows[other] = tmp
        gridModel.fireTableDataChanged()
        grid.changeSelection(other, grid.selectedColumn.coerceAtLeast(0), false, false)
    }

    private fun addColumnAt(index: Int) {
        val idx = index.coerceIn(0, gridColumnCount)
        gridColumnCount++
        for (row in gridRows) row.add(idx, "")
        alignments.add(idx.coerceAtMost(alignments.size), QuarkdownTableParser.Alignment.NONE)
        gridModel.fireTableStructureChanged()
        autoSizeGrid()
    }

    private fun removeColumnAt(index: Int) {
        if (gridColumnCount <= 1 || index !in 0 until gridColumnCount) return
        gridColumnCount--
        for (row in gridRows) {
            if (index < row.size) row.removeAt(index)
        }
        if (index < alignments.size) alignments.removeAt(index)
        gridModel.fireTableStructureChanged()
        autoSizeGrid()
    }

    private fun setColumnAlignment(column: Int, alignment: QuarkdownTableParser.Alignment) {
        while (alignments.size <= column) alignments.add(QuarkdownTableParser.Alignment.NONE)
        alignments[column] = alignment
    }

    /** Spreadsheet-style column name: 0 → A, 1 → B, ... 26 → AA. */
    private fun spreadsheetColumnName(index: Int): String {
        var n = index
        val sb = StringBuilder()
        do {
            sb.insert(0, ('A' + n % 26))
            n = n / 26 - 1
        } while (n >= 0)
        return sb.toString()
    }

    // ------------------------------------------------------------------
    // Test helpers
    // ------------------------------------------------------------------

    /** Test helper: set the caption field directly. */
    fun setLabelForTest(label: String) {
        labelField.text = label
    }

    /** Test helper: set the ID field directly. */
    fun setIdForTest(id: String) {
        idField.text = id
    }

    /** Test helper: the current grid content (row 0 is the header row). */
    fun getGridRowsForTest(): List<List<String>> = gridRows.map { it.toList() }

    /** Test helper: the number of columns shown by the grid. */
    fun getColumnCountForTest(): Int = grid.columnCount

    /** Test helper: drive the grid like a user editing a cell. */
    fun setCellForTest(row: Int, column: Int, value: String) {
        gridModel.setValueAt(value, row, column)
    }

    /** Test helper: expose the caption warning visibility. */
    fun isCaptionWarningVisibleForTest(): Boolean = captionWarning.isVisible

    /** Test helper: the A/B/C column header is hidden. */
    fun isColumnHeaderHiddenForTest(): Boolean = grid.tableHeader == null

    /** Test helper: drive the toolbar's insert/delete column operations. */
    fun insertColumnForTest(index: Int) = addColumnAt(index)

    /** Test helper: drive the toolbar's delete column operation. */
    fun removeColumnForTest(index: Int) = removeColumnAt(index)

    /** Test helper: drive the toolbar's insert row operation. */
    fun insertRowForTest(index: Int) = insertRowAt(index)

    /** Test helper: drive the toolbar's delete row operation. */
    fun deleteRowForTest(row: Int) = deleteRow(row)

    /** Test helper: the current width of a grid column. */
    fun getColumnWidthForTest(column: Int): Int = grid.columnModel.getColumn(column).width

    /** Test helper: the current height of a grid row. */
    fun getRowHeightForTest(row: Int): Int = grid.getRowHeight(row)
}

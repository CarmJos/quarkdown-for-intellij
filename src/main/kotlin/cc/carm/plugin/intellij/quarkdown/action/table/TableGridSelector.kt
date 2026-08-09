package cc.carm.plugin.intellij.quarkdown.action.table

import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.popup.AbstractPopup
import com.intellij.util.ui.UIUtil
import net.miginfocom.swing.MigLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Point
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import kotlin.math.floor

/**
 * Spreadsheet-style row/column picker shown when inserting an empty table
 * (mirrors the IntelliJ Markdown plugin's InsertEmptyTableAction grid).
 *
 * Rows count includes the header row (a "1×1" picker inserts the header + separator).
 */
class TableGridSelector(
    private var rows: Int = 4,
    private var columns: Int = 4,
    private val expandFactor: Int = 2,
    private val selectedCallback: (rows: Int, columns: Int) -> Unit,
    private val indicesUpdatedCallback: ((Int, Int) -> Unit)? = null
) : JPanel(MigLayout("insets 8")) {

    private var parentHint: JBPopup? = null
    private val cells = arrayListOf<ArrayList<Cell>>()
    var selectedCellRow: Int = 0
        private set
    var selectedCellColumn: Int = 0
        private set

    private val gridPanel: JComponent = JPanel(MigLayout("insets 0, gap 3"))
    private val label = JBLabel()

    private val mouseListener = MyMouseListener()
    private val childMouseListener = MyForwardingMouseListener(gridPanel)

    init {
        for (rowIndex in 0 until rows) {
            cells.add(generateSequence(::createCell).take(columns).toCollection(ArrayList(columns)))
        }
        fillGrid()
        add(gridPanel, "wrap")
        add(label, "align center")
        gridPanel.addMouseMotionListener(mouseListener)
        gridPanel.addMouseListener(mouseListener)
        registerAction(KeyEvent.VK_RIGHT, "selectRight", ArrowAction { 0 to 1 })
        registerAction(KeyEvent.VK_LEFT, "selectLeft", ArrowAction { 0 to -1 })
        registerAction(KeyEvent.VK_UP, "selectUp", ArrowAction { -1 to 0 })
        registerAction(KeyEvent.VK_DOWN, "selectDown", ArrowAction { 1 to 0 })
        registerAction(KeyEvent.VK_ENTER, "confirmSelection", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                indicesSelected(selectedCellRow, selectedCellColumn)
            }
        })
        updateSelection(0, 0)
    }

    fun show(editor: com.intellij.openapi.editor.Editor) {
        val popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(this, this)
            .setRequestFocus(true)
            .createPopup()
        parentHint = popup
        popup.showInBestPositionFor(editor)
    }

    private fun indicesSelected(selectedRow: Int, selectedColumn: Int) {
        parentHint?.closeOk(null)
        selectedCallback.invoke(selectedRow, selectedColumn + 1)
    }

    private fun registerAction(key: Int, actionKey: String, action: Action) {
        val inputMap = getInputMap(WHEN_IN_FOCUSED_WINDOW)
        inputMap.put(KeyStroke.getKeyStroke(key, 0), actionKey)
        actionMap.put(actionKey, action)
    }

    private fun fillGrid() {
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                val cell = cells[row][column]
                when {
                    column == columns - 1 && row != rows - 1 -> gridPanel.add(cell, "wrap")
                    else -> gridPanel.add(cell)
                }
            }
        }
    }

    private fun expandGrid(expandRows: Boolean, expandColumns: Boolean) {
        gridPanel.removeAll()
        if (expandRows) {
            repeat(expandFactor) {
                cells.add(generateSequence(::createCell).take(columns).toCollection(ArrayList(columns)))
            }
            rows += expandFactor
        }
        if (expandColumns) {
            for (row in cells) {
                repeat(expandFactor) {
                    row.add(createCell())
                }
            }
            columns += expandFactor
        }
        fillGrid()
    }

    private fun updateSelection(selectedRow: Int, selectedColumn: Int) {
        selectedCellRow = selectedRow
        selectedCellColumn = selectedColumn
        indicesUpdatedCallback?.invoke(selectedCellRow, selectedCellColumn)
        label.text = "${selectedRow + 1}×${selectedColumn + 1}"
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                cells[row][column].isSelected = row <= selectedCellRow && column <= selectedCellColumn
            }
        }
        repaint()
        val shouldExpandRows = rows < maxRows && selectedRow + 1 == rows
        val shouldExpandColumns = columns < maxColumns && selectedColumn + 1 == columns
        if (shouldExpandRows || shouldExpandColumns) {
            expandGrid(expandRows = shouldExpandRows, expandColumns = shouldExpandColumns)
            val hint = parentHint as? AbstractPopup ?: return
            hint.pack(true, true)
            hint.component.revalidate()
            hint.component.repaint()
        }
    }

    private fun createCell(): Cell {
        return Cell().apply {
            addMouseListener(childMouseListener)
            addMouseMotionListener(childMouseListener)
        }
    }

    private inner class ArrowAction(private val calcDiff: () -> Pair<Int, Int>) : AbstractAction() {
        override fun actionPerformed(e: ActionEvent) {
            val (rowDiff, columnDiff) = calcDiff.invoke()
            var row = selectedCellRow + rowDiff
            var column = selectedCellColumn + columnDiff
            row = row.coerceAtMost(rows - 1).coerceAtLeast(0)
            column = column.coerceAtMost(columns - 1).coerceAtLeast(0)
            updateSelection(row, column)
        }
    }

    private class MyForwardingMouseListener(private val targetComponent: Component) : MouseAdapter() {
        private fun dispatch(event: MouseEvent) {
            val translated = SwingUtilities.convertMouseEvent(event.component, event, targetComponent)
            targetComponent.dispatchEvent(translated)
        }

        override fun mouseMoved(event: MouseEvent) = dispatch(event)
        override fun mouseClicked(event: MouseEvent) = dispatch(event)
    }

    private inner class MyMouseListener : MouseAdapter() {
        private fun obtainIndices(point: Point): Pair<Int, Int> {
            val panelWidth = gridPanel.width.toFloat()
            val panelHeight = gridPanel.height.toFloat()
            val tileWidth = panelWidth / columns
            val tileHeight = panelHeight / rows
            val column = floor(point.x.toFloat() / tileWidth).toInt().coerceIn(0, columns - 1)
            val row = floor(point.y.toFloat() / tileHeight).toInt().coerceIn(0, rows - 1)
            return row to column
        }

        override fun mouseMoved(event: MouseEvent) {
            val (row, column) = obtainIndices(event.point)
            updateSelection(row, column)
        }

        override fun mouseClicked(event: MouseEvent) {
            if (SwingUtilities.isLeftMouseButton(event)) {
                val (row, column) = obtainIndices(event.point)
                updateSelection(row, column)
                indicesSelected(row, column)
            }
        }
    }

    private class Cell : JPanel() {
        init {
            background = UIUtil.getTextFieldBackground()
            size = Dimension(15, 15)
            preferredSize = size
            border = IdeBorderFactory.createBorder()
        }

        var isSelected = false
            set(value) {
                field = value
                background = when {
                    value -> UIUtil.getFocusedFillColor()
                    else -> UIUtil.getEditorPaneBackground()
                }
            }
    }

    companion object {
        private const val maxRows = 10
        private const val maxColumns = 10
    }
}

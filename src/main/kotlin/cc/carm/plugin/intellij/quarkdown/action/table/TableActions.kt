package cc.carm.plugin.intellij.quarkdown.action.table

import cc.carm.plugin.intellij.quarkdown.QuarkdownBundle
import cc.carm.plugin.intellij.quarkdown.QuarkdownFileType
import cc.carm.plugin.intellij.quarkdown.lang.table.QuarkdownTableModificationUtils
import cc.carm.plugin.intellij.quarkdown.lang.table.QuarkdownTableParser
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile

private fun AnActionEvent.quarkdownContext(): Pair<Editor, PsiFile>? {
    val editor = getData(CommonDataKeys.EDITOR) ?: return null
    val file = getData(CommonDataKeys.PSI_FILE) ?: return null
    if (file.fileType !is QuarkdownFileType) return null
    return editor to file
}

/** Locates the [QuarkdownTableModificationUtils.TableBlock] at [tableOffset] in [file]. */
fun findBlock(file: PsiFile, tableOffset: Int?): QuarkdownTableModificationUtils.TableBlock? {
    if (tableOffset == null) return null
    return QuarkdownTableModificationUtils.findTableBlocks(file.text)
        .firstOrNull { it.startOffset == tableOffset }
}

/** Group with update-on-BGT semantics (mirrors the Markdown plugin's TableActionsGroup). */
class TableActionsGroup : DefaultActionGroup() {
    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = true
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

// ---------------------------------------------------------------------------
// Row actions
// ---------------------------------------------------------------------------
abstract class RowBasedTableAction(
    text: String = "",
    description: String = "",
) : AnAction(text, description, null) {
    override fun actionPerformed(event: AnActionEvent) {
        val (editor, file) = event.quarkdownContext() ?: return
        val block = findBlock(file, event.getData(TableActionKeys.TABLE_OFFSET)) ?: return
        val rowIndex = event.getData(TableActionKeys.ROW_INDEX) ?: return
        performAction(editor, file, block, rowIndex)
    }

    override fun update(event: AnActionEvent) {
        val (editor, file) = event.quarkdownContext() ?: run {
            event.presentation.isEnabledAndVisible = false
            return
        }
        val block = findBlock(file, event.getData(TableActionKeys.TABLE_OFFSET))
        val rowIndex = event.getData(TableActionKeys.ROW_INDEX)
        event.presentation.isEnabledAndVisible = block != null && rowIndex != null
        if (block != null && rowIndex != null) update(event, block, rowIndex)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
    protected abstract fun performAction(
        editor: Editor,
        file: PsiFile,
        block: QuarkdownTableModificationUtils.TableBlock,
        rowIndex: Int
    )

    protected open fun update(event: AnActionEvent, block: QuarkdownTableModificationUtils.TableBlock, rowIndex: Int) =
        Unit
}

/** Inserts an empty row above/below the clicked row. */
abstract class InsertRowAction(
    private val insertAbove: Boolean,
    text: String = "",
    description: String = "",
) : RowBasedTableAction(text, description) {
    override fun performAction(
        editor: Editor,
        file: PsiFile,
        block: QuarkdownTableModificationUtils.TableBlock,
        rowIndex: Int
    ) {
        runWriteAction {
            executeCommand(editor.project) {
                // rowIndex 0 = header, 1 = separator; data rows start at 2.
                val dataRow = (rowIndex - 2).coerceIn(0, QuarkdownTableParser.parse(block.lines)?.rows?.size ?: 0)
                val insertAt = if (insertAbove) dataRow else dataRow + 1
                QuarkdownTableModificationUtils.insertRow(editor.project, editor.document, block, insertAt)
            }
        }
    }

    class InsertAbove : InsertRowAction(
        insertAbove = true,
        QuarkdownBundle.message("quarkdown.table.insert.row.above"),
        QuarkdownBundle.message("quarkdown.table.insert.row.above.description")
    )

    class InsertBelow : InsertRowAction(
        insertAbove = false,
        QuarkdownBundle.message("quarkdown.table.insert.row.below"),
        QuarkdownBundle.message("quarkdown.table.insert.row.below.description")
    )
}

/** Swaps the clicked data row with the one above/below. */
abstract class SwapRowsAction(
    private val swapWithAbove: Boolean,
    text: String = "",
    description: String = "",
) : RowBasedTableAction(text, description) {
    override fun performAction(
        editor: Editor,
        file: PsiFile,
        block: QuarkdownTableModificationUtils.TableBlock,
        rowIndex: Int
    ) {
        val table = QuarkdownTableParser.parse(block.lines) ?: return
        val dataRow = rowIndex - 2
        if (dataRow !in table.rows.indices) return
        val other = if (swapWithAbove) dataRow - 1 else dataRow + 1
        if (other !in table.rows.indices) return
        runWriteAction {
            executeCommand(editor.project) {
                QuarkdownTableModificationUtils.swapRows(editor.project, editor.document, block, dataRow, other)
            }
        }
    }

    override fun update(event: AnActionEvent, block: QuarkdownTableModificationUtils.TableBlock, rowIndex: Int) {
        val table = QuarkdownTableParser.parse(block.lines) ?: return
        val dataRow = rowIndex - 2
        val other = if (swapWithAbove) dataRow - 1 else dataRow + 1
        event.presentation.isEnabled = other in table.rows.indices
    }

    class SwapWithAbove : SwapRowsAction(
        swapWithAbove = true,
        QuarkdownBundle.message("quarkdown.table.move.row.up"),
        QuarkdownBundle.message("quarkdown.table.move.row.up.description")
    )

    class SwapWithBelow : SwapRowsAction(
        swapWithAbove = false,
        QuarkdownBundle.message("quarkdown.table.move.row.down"),
        QuarkdownBundle.message("quarkdown.table.move.row.down.description")
    )
}

/** Selects the whole clicked row. */
class SelectRowAction : RowBasedTableAction(
    QuarkdownBundle.message("quarkdown.table.select.row"),
    QuarkdownBundle.message("quarkdown.table.select.row.description")
) {
    override fun performAction(
        editor: Editor,
        file: PsiFile,
        block: QuarkdownTableModificationUtils.TableBlock,
        rowIndex: Int
    ) {
        executeCommand(editor.project) {
            QuarkdownTableModificationUtils.selectRow(editor.project, editor, block, rowIndex)
        }
    }
}

/** Removes the clicked data row. */
class RemoveCurrentRowAction : RowBasedTableAction(
    QuarkdownBundle.message("quarkdown.table.remove.row"),
    QuarkdownBundle.message("quarkdown.table.remove.row.description")
) {
    override fun performAction(
        editor: Editor,
        file: PsiFile,
        block: QuarkdownTableModificationUtils.TableBlock,
        rowIndex: Int
    ) {
        val dataRow = rowIndex - 2
        runWriteAction {
            executeCommand(editor.project) {
                QuarkdownTableModificationUtils.deleteRow(editor.project, editor.document, block, dataRow)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Column actions
// ---------------------------------------------------------------------------
abstract class ColumnBasedTableAction(
    text: String = "",
    description: String = "",
) : AnAction(text, description, null) {
    override fun actionPerformed(event: AnActionEvent) {
        val (editor, file) = event.quarkdownContext() ?: return
        val block = findBlock(file, event.getData(TableActionKeys.TABLE_OFFSET)) ?: return
        val columnIndex = event.getData(TableActionKeys.COLUMN_INDEX) ?: return
        performAction(editor, file, block, columnIndex)
    }

    override fun update(event: AnActionEvent) {
        val (editor, file) = event.quarkdownContext() ?: run {
            event.presentation.isEnabledAndVisible = false
            return
        }
        val block = findBlock(file, event.getData(TableActionKeys.TABLE_OFFSET))
        val columnIndex = event.getData(TableActionKeys.COLUMN_INDEX)
        event.presentation.isEnabledAndVisible = block != null && columnIndex != null
        if (block != null && columnIndex != null) update(event, block, columnIndex)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
    protected abstract fun performAction(
        editor: Editor,
        file: PsiFile,
        block: QuarkdownTableModificationUtils.TableBlock,
        columnIndex: Int
    )

    protected open fun update(
        event: AnActionEvent,
        block: QuarkdownTableModificationUtils.TableBlock,
        columnIndex: Int
    ) = Unit
}

/** Inserts an empty column before/after the clicked column. */
abstract class InsertTableColumnAction(
    private val insertAfter: Boolean,
    text: String = "",
    description: String = "",
) : ColumnBasedTableAction(text, description) {
    override fun performAction(
        editor: Editor,
        file: PsiFile,
        block: QuarkdownTableModificationUtils.TableBlock,
        columnIndex: Int
    ) {
        runWriteAction {
            executeCommand(editor.project) {
                QuarkdownTableModificationUtils.insertColumn(
                    editor.project,
                    editor.document,
                    block,
                    columnIndex,
                    after = insertAfter
                )
            }
        }
    }

    class InsertBefore : InsertTableColumnAction(
        insertAfter = false,
        QuarkdownBundle.message("quarkdown.table.insert.column.before"),
        QuarkdownBundle.message("quarkdown.table.insert.column.before.description")
    )

    class InsertAfter : InsertTableColumnAction(
        insertAfter = true,
        QuarkdownBundle.message("quarkdown.table.insert.column.after"),
        QuarkdownBundle.message("quarkdown.table.insert.column.after.description")
    )
}

/** Swaps the clicked column with the one on the left/right. */
abstract class SwapColumnsAction(
    private val swapWithLeft: Boolean,
    text: String = "",
    description: String = "",
) : ColumnBasedTableAction(text, description) {
    override fun performAction(
        editor: Editor,
        file: PsiFile,
        block: QuarkdownTableModificationUtils.TableBlock,
        columnIndex: Int
    ) {
        val table = QuarkdownTableParser.parse(block.lines) ?: return
        val other = if (swapWithLeft) columnIndex - 1 else columnIndex + 1
        if (other !in 0 until table.columnCount) return
        runWriteAction {
            executeCommand(editor.project) {
                QuarkdownTableModificationUtils.swapColumns(editor.project, editor.document, block, columnIndex, other)
            }
        }
    }

    override fun update(event: AnActionEvent, block: QuarkdownTableModificationUtils.TableBlock, columnIndex: Int) {
        val table = QuarkdownTableParser.parse(block.lines) ?: return
        val other = if (swapWithLeft) columnIndex - 1 else columnIndex + 1
        event.presentation.isEnabled = other in 0 until table.columnCount
    }

    class SwapWithLeft : SwapColumnsAction(
        swapWithLeft = true,
        QuarkdownBundle.message("quarkdown.table.move.column.left"),
        QuarkdownBundle.message("quarkdown.table.move.column.left.description")
    )

    class SwapWithRight : SwapColumnsAction(
        swapWithLeft = false,
        QuarkdownBundle.message("quarkdown.table.move.column.right"),
        QuarkdownBundle.message("quarkdown.table.move.column.right.description")
    )
}

/** Selects all cells of the clicked column using multiple carets. */
class SelectCurrentColumnAction : ColumnBasedTableAction(
    QuarkdownBundle.message("quarkdown.table.select.column"),
    QuarkdownBundle.message("quarkdown.table.select.column.description")
) {
    override fun performAction(
        editor: Editor,
        file: PsiFile,
        block: QuarkdownTableModificationUtils.TableBlock,
        columnIndex: Int
    ) {
        executeCommand(editor.project) {
            QuarkdownTableModificationUtils.selectColumn(editor.project, editor, block, columnIndex, withBorders = true)
        }
    }
}

/** Removes the clicked column (the last remaining column is kept). */
class RemoveCurrentColumnAction : ColumnBasedTableAction(
    QuarkdownBundle.message("quarkdown.table.remove.column"),
    QuarkdownBundle.message("quarkdown.table.remove.column.description")
) {
    override fun performAction(
        editor: Editor,
        file: PsiFile,
        block: QuarkdownTableModificationUtils.TableBlock,
        columnIndex: Int
    ) {
        runWriteAction {
            executeCommand(editor.project) {
                QuarkdownTableModificationUtils.deleteColumn(editor.project, editor.document, block, columnIndex)
            }
        }
    }
}

/** Sets the clicked column's alignment. */
abstract class SetColumnAlignmentAction(
    private val alignment: QuarkdownTableParser.Alignment,
    text: String = "",
    description: String = "",
) : ColumnBasedTableAction(text, description) {
    override fun performAction(
        editor: Editor,
        file: PsiFile,
        block: QuarkdownTableModificationUtils.TableBlock,
        columnIndex: Int
    ) {
        runWriteAction {
            executeCommand(editor.project) {
                QuarkdownTableModificationUtils.setColumnAlignment(
                    editor.project,
                    editor.document,
                    block,
                    columnIndex,
                    alignment
                )
            }
        }
    }

    class Left : SetColumnAlignmentAction(
        QuarkdownTableParser.Alignment.LEFT,
        QuarkdownBundle.message("quarkdown.table.align.column.left"),
        QuarkdownBundle.message("quarkdown.table.align.column.left.description")
    )

    class Center : SetColumnAlignmentAction(
        QuarkdownTableParser.Alignment.CENTER,
        QuarkdownBundle.message("quarkdown.table.align.column.center"),
        QuarkdownBundle.message("quarkdown.table.align.column.center.description")
    )

    class Right : SetColumnAlignmentAction(
        QuarkdownTableParser.Alignment.RIGHT,
        QuarkdownBundle.message("quarkdown.table.align.column.right"),
        QuarkdownBundle.message("quarkdown.table.align.column.right.description")
    )
}

/** Group that only enables when a valid column is selected. */
class TableColumnAlignmentActionsGroup : DefaultActionGroup() {
    override fun update(event: AnActionEvent) {
        val (editor, file) = event.quarkdownContext() ?: run {
            event.presentation.isEnabledAndVisible = false
            return
        }
        val block = findBlock(file, event.getData(TableActionKeys.TABLE_OFFSET))
        event.presentation.isEnabledAndVisible = block != null && event.getData(TableActionKeys.COLUMN_INDEX) != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

/**
 * Re-aligns the whole table (pads every cell to a common column width).
 *
 * Placed at the far left of both the row and column floating toolbars; it only needs
 * the [TableActionKeys.TABLE_OFFSET] and works regardless of the clicked row/column.
 */
class FormatTableAction : AnAction(
    QuarkdownBundle.message("quarkdown.table.format"),
    QuarkdownBundle.message("quarkdown.table.format.description"),
    null
) {
    override fun actionPerformed(event: AnActionEvent) {
        val (editor, file) = event.quarkdownContext() ?: return
        val block = findBlock(file, event.getData(TableActionKeys.TABLE_OFFSET)) ?: return
        runWriteAction {
            executeCommand(editor.project) {
                QuarkdownTableModificationUtils.formatTable(editor.project, editor.document, block)
            }
        }
    }

    override fun update(event: AnActionEvent) {
        val (editor, file) = event.quarkdownContext() ?: run {
            event.presentation.isEnabledAndVisible = false
            return
        }
        val block = findBlock(file, event.getData(TableActionKeys.TABLE_OFFSET))
        event.presentation.isEnabledAndVisible = block != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

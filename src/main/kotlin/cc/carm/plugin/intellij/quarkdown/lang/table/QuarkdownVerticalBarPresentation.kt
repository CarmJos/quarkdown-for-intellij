@file:Suppress("UnstableApiUsage")

package cc.carm.plugin.intellij.quarkdown.lang.table

import cc.carm.plugin.intellij.quarkdown.action.table.TableActionKeys
import cc.carm.plugin.intellij.quarkdown.action.table.TableActionPlaces
import cc.carm.plugin.intellij.quarkdown.lang.table.QuarkdownGraphicsUtils.clearHalfOvalOverEditor
import cc.carm.plugin.intellij.quarkdown.lang.table.QuarkdownGraphicsUtils.fillHalfOval
import cc.carm.plugin.intellij.quarkdown.lang.table.QuarkdownGraphicsUtils.useCopy
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.codeInsight.hints.presentation.BasePresentation
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.impl.ToolbarUtils
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.ui.LightweightHint
import com.intellij.util.ui.GraphicsUtil
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities

internal class QuarkdownVerticalBarPresentation(
    private val editor: Editor,
    private val block: QuarkdownTableModificationUtils.TableBlock,
    private val rowIndex: Int,
    private val hover: Boolean
) : BasePresentation() {
    private enum class RowLocation { FIRST, LAST, OTHER }

    private val rowLocation: RowLocation = when (rowIndex) {
        0 -> RowLocation.FIRST
        block.lines.lastIndex -> RowLocation.LAST
        else -> RowLocation.OTHER
    }
    override val width: Int get() = barWidth
    override val height: Int get() = editor.lineHeight

    override fun toString(): String = "QuarkdownVerticalBarPresentation"

    override fun paint(graphics: Graphics2D, attributes: TextAttributes) {
        if (editor.isDisposed) return
        graphics.useCopy { local ->
            GraphicsUtil.setupAntialiasing(local)
            GraphicsUtil.setupRoundedBorderAntialiasing(local)
            paintRow(local, rowLocation)
        }
    }

    private fun calculateBarRect(location: RowLocation): Rectangle = when (location) {
        RowLocation.OTHER -> Rectangle(0, 0, barWidth, height)
        RowLocation.FIRST -> Rectangle(0, barWidth / 2, barWidth, height - barWidth / 2)
        RowLocation.LAST -> Rectangle(0, 0, barWidth, height - barWidth / 2)
    }

    private fun paintRow(graphics: Graphics2D, location: RowLocation) {
        val rect = calculateBarRect(location)
        actuallyPaintBar(graphics, rect, hover)
        graphics.color = QuarkdownTableInlayProperties.circleColor
        when (location) {
            RowLocation.OTHER -> paintOtherCircles(graphics, rect)
            RowLocation.FIRST -> paintFirstCircles(graphics, rect)
            RowLocation.LAST -> paintLastCircles(graphics, rect)
        }
    }

    private fun paintOtherCircles(graphics: Graphics2D, rect: Rectangle) {
        repeat(2) {
            graphics.fillHalfOval(rect.x, rect.y - barWidth / 2, barWidth, barWidth, upperHalf = true)
            graphics.fillHalfOval(rect.x, rect.y + rect.height - barWidth / 2, barWidth, barWidth, upperHalf = false)
        }
    }

    private fun paintFirstCircles(graphics: Graphics2D, rect: Rectangle) {
        repeat(2) {
            graphics.fillOval(rect.x, rect.y - barWidth / 2, barWidth, barWidth)
            graphics.fillHalfOval(rect.x, rect.y + rect.height - barWidth / 2, barWidth, barWidth, upperHalf = false)
        }
    }

    private fun paintLastCircles(graphics: Graphics2D, rect: Rectangle) {
        repeat(2) {
            graphics.fillHalfOval(rect.x, rect.y - barWidth / 2, barWidth, barWidth, upperHalf = true)
            graphics.fillOval(rect.x, rect.y + rect.height - barWidth / 2, barWidth, barWidth)
        }
    }

    private fun actuallyPaintBar(graphics: Graphics2D, rect: Rectangle, hover: Boolean) {
        graphics.color =
            if (hover) QuarkdownTableInlayProperties.barHoverColor else QuarkdownTableInlayProperties.barColor
        graphics.fillRect(rect.x, rect.y, rect.width, rect.height)
        graphics.clearHalfOvalOverEditor(rect.x, rect.y - barWidth / 2, barWidth, barWidth, upper = true)
        graphics.clearHalfOvalOverEditor(rect.x, rect.y + rect.height - barWidth / 2, barWidth, barWidth, upper = false)
    }

    override fun mouseClicked(event: MouseEvent, translated: Point) {
        when {
            SwingUtilities.isLeftMouseButton(event) && event.clickCount.mod(2) == 0 -> handleMouseLeftDoubleClick()
            SwingUtilities.isLeftMouseButton(event) -> handleMouseLeftClick()
        }
    }

    private fun handleMouseLeftClick() = showToolbar()
    private fun handleMouseLeftDoubleClick() {
        executeCommand(editor.project) {
            QuarkdownTableModificationUtils.selectRow(editor.project, editor, block, rowIndex)
        }
    }

    private fun calculateToolbarPosition(componentHeight: Int): Point {
        val position = editor.offsetToXY(block.lineStarts[rowIndex])
        val editorParent = editor.contentComponent.topLevelAncestor.locationOnScreen
        val editorPosition = editor.contentComponent.locationOnScreen
        position.translate(editorPosition.x - editorParent.x, editorPosition.y - editorParent.y)
        position.translate(0, -editor.lineHeight)
        position.translate(0, -componentHeight)
        val rect = calculateBarRect(rowLocation)
        position.translate(rect.x, -rect.y - barWidth * 2 - 2)
        return position
    }

    private fun showToolbar() {
        val targetComponent = ToolbarUtils.createTargetComponent(editor) { sink ->
            TableActionKeys.putRowSnapshot(sink, block, rowIndex)
        }
        ToolbarUtils.createImmediatelyUpdatedToolbar(
            group = rowActionGroup,
            place = TableActionPlaces.TABLE_INLAY_TOOLBAR,
            targetComponent,
            horizontal = true,
            onUpdated = { toolbar -> createAndShowHint(toolbar) }
        )
    }

    private fun createAndShowHint(toolbar: ActionToolbar) {
        val hint = LightweightHint(toolbar.component)
        hint.setForceShowAsPopup(true)
        val targetPoint = calculateToolbarPosition(hint.component.preferredSize.height)
        val hintManager = HintManagerImpl.getInstanceImpl()
        hintManager.hideAllHints()
        val flags = HintManager.HIDE_BY_ESCAPE or HintManager.HIDE_BY_SCROLLING or
                HintManager.HIDE_BY_CARET_MOVE or HintManager.HIDE_BY_TEXT_CHANGE
        hintManager.showEditorHint(hint, editor, targetPoint, flags, 0, false)
    }

    companion object {
        const val barWidth = QuarkdownTableInlayProperties.barSize
        private val rowActionGroup: ActionGroup
            get() = ActionManager.getInstance().getAction("Quarkdown.TableRowActions") as ActionGroup

        fun create(
            factory: PresentationFactory,
            editor: Editor,
            block: QuarkdownTableModificationUtils.TableBlock,
            rowIndex: Int
        ): InlayPresentation {
            val base = QuarkdownVerticalBarPresentation(editor, block, rowIndex, hover = false)
            val hoverPresentation = QuarkdownVerticalBarPresentation(editor, block, rowIndex, hover = true)
            return factory.changeOnHover(
                wrapPresentation(factory, editor, base),
                { wrapPresentation(factory, editor, hoverPresentation) }
            )
        }

        private fun wrapPresentation(
            factory: PresentationFactory,
            editor: Editor,
            presentation: InlayPresentation
        ): InlayPresentation {
            return factory.inset(
                QuarkdownPresentationWithCustomCursor(editor, presentation),
                left = QuarkdownTableInlayProperties.leftRightPadding,
                right = QuarkdownTableInlayProperties.leftRightPadding
            )
        }
    }
}

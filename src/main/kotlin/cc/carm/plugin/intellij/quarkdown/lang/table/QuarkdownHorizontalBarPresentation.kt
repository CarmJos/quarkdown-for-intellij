@file:Suppress("UnstableApiUsage")

package cc.carm.plugin.intellij.quarkdown.lang.table

import cc.carm.plugin.intellij.quarkdown.action.table.TableActionKeys
import cc.carm.plugin.intellij.quarkdown.action.table.TableActionPlaces
import cc.carm.plugin.intellij.quarkdown.lang.table.QuarkdownGraphicsUtils.clearOvalOverEditor
import cc.carm.plugin.intellij.quarkdown.lang.table.QuarkdownGraphicsUtils.useCopy
import cc.carm.plugin.intellij.quarkdown.ui.QuarkdownActionToolbarUtils
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.codeInsight.hints.fireUpdateEvent
import com.intellij.codeInsight.hints.presentation.BasePresentation
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.ui.LightweightHint
import com.intellij.util.ui.GraphicsUtil
import java.awt.*
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.SwingUtilities

internal class QuarkdownHorizontalBarPresentation(
    private val editor: Editor,
    block: QuarkdownTableModificationUtils.TableBlock
) : BasePresentation() {
    private data class BoundsState(val width: Int, val height: Int, val barsModel: List<Rectangle>)

    /** Absolute start offset of the table — the anchor used to re-locate it after edits. */
    private val anchorStart: Int = block.startOffset

    /** Current table snapshot; re-resolved against the committed document on every refresh. */
    private var block: QuarkdownTableModificationUtils.TableBlock = block

    private var lastSelectedIndex: Int? = null
    private var boundsState = emptyBoundsState
    private var refreshScheduled = false
    private val project = editor.project

    init {
        val document = editor.document
        scheduleBoundsRefresh(document)
        val listenerDisposable = Disposer.newDisposable("QuarkdownHorizontalBarPresentation listener")
        EditorUtil.disposeWithEditor(editor, listenerDisposable)
        document.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                if (isInvalid || refreshScheduled) return
                // Coalesce bursts of edits (e.g. a single keystroke fires multiple events)
                // into exactly one bounds refresh per presentation.
                refreshScheduled = true
                invokeLater(ModalityState.stateForComponent(editor.contentComponent)) {
                    refreshScheduled = false
                    if (!isInvalid) scheduleBoundsRefresh(document)
                }
            }
        }, listenerDisposable)
    }

    /**
     * Recomputes the bar bounds against the committed document. Tables whose content is
     * untouched produce the same [BoundsState] and therefore skip repainting entirely —
     * that is what prevents unrelated tables from flickering while typing elsewhere.
     */
    private fun scheduleBoundsRefresh(document: Document) {
        val project = project ?: return
        PsiDocumentManager.getInstance(project).performForCommittedDocument(document) {
            invokeLater(ModalityState.stateForComponent(editor.contentComponent)) {
                if (isInvalid) return@invokeLater
                // Re-locate the table so offsets and widths always reflect the current text.
                val refreshed = QuarkdownTableModificationUtils.findTableBlocks(document.immutableCharSequence)
                    .firstOrNull { it.startOffset == anchorStart } ?: return@invokeLater
                block = refreshed
                val previous = boundsState
                val calculated = calculateCurrentBoundsState(document)
                if (calculated == previous) return@invokeLater
                boundsState = calculated
                val previousSize = Dimension(previous.width, previous.height)
                val newSize = Dimension(calculated.width, calculated.height)
                if (previousSize == newSize) {
                    fireUpdateEvent(previousSize)
                } else {
                    fireSizeChanged(previousSize, newSize)
                }
            }
        }
    }

    private val isInvalid: Boolean get() = editor.isDisposed
    private val barsModel get() = boundsState.barsModel
    override val width: Int get() = boundsState.width
    override val height: Int get() = boundsState.height

    override fun toString(): String = "QuarkdownHorizontalBarPresentation"

    override fun paint(graphics: Graphics2D, attributes: TextAttributes) {
        if (isInvalid) return
        graphics.useCopy { local ->
            GraphicsUtil.setupAntialiasing(local)
            GraphicsUtil.setupRoundedBorderAntialiasing(local)
            paintBars(local)
        }
    }

    override fun mouseClicked(event: MouseEvent, translated: Point) {
        when {
            SwingUtilities.isLeftMouseButton(event) && event.clickCount.mod(2) == 0 -> handleMouseLeftDoubleClick(
                translated
            )

            SwingUtilities.isLeftMouseButton(event) -> handleMouseLeftClick(translated)
        }
    }

    override fun mouseMoved(event: MouseEvent, translated: Point) {
        val index = barsModel.indexOfFirst { it.contains(translated) }.takeUnless { it < 0 }
        if (lastSelectedIndex != index) {
            lastSelectedIndex = index
            fireUpdateEvent(Dimension(0, 0))
        }
    }

    override fun mouseExited() {
        if (lastSelectedIndex != null) {
            lastSelectedIndex = null
            fireUpdateEvent(Dimension(0, 0))
        }
    }

    private fun calculateCurrentBoundsState(document: Document): BoundsState {
        if (isInvalid) return emptyBoundsState
        val metrics = obtainFontMetrics(editor)
        val width = calculateRowWidth(metrics, document)
        val barsModel = buildBarsModel(metrics, document)
        return BoundsState(width, QuarkdownTableInlayProperties.barSize, barsModel)
    }

    private fun calculateRowWidth(fontMetrics: FontMetrics, document: Document): Int {
        val headerLine = block.lines.firstOrNull() ?: return 0
        val range = TextRange(block.lineStarts[0], block.lineStarts[0] + headerLine.length)
        return fontMetrics.stringWidth(document.getText(range))
    }

    private fun buildBarsModel(
        fontMetrics: FontMetrics,
        document: Document
    ): List<Rectangle> {
        val positions = calculatePositions(fontMetrics, document)
        val sectors = positions.windowed(2).map { (left, right) -> left to right - left }
        return sectors.map { (offset, sectorWidth) ->
            Rectangle(offset - barHeight / 2, 0, sectorWidth + barHeight, barHeight)
        }
    }

    private fun calculatePositions(
        fontMetrics: FontMetrics,
        document: Document
    ): List<Int> {
        val headerLine = block.lines.firstOrNull() ?: return emptyList()
        val headerStart = block.lineStarts[0]
        val result = mutableListOf<Int>()
        var x = editor.offsetToXY(headerStart).x
        var prevEnd = 0
        for ((i, ch) in headerLine.withIndex()) {
            val seg = headerLine.substring(prevEnd, i + 1)
            x += fontMetrics.stringWidth(seg)
            prevEnd = i + 1
            if (ch == '|') {
                val separatorWidth = fontMetrics.charWidth('|')
                result += x - separatorWidth / 2
            }
        }
        return result
    }

    private fun calculateToolbarPosition(componentHeight: Int, columnIndex: Int): Point {
        val position = editor.offsetToXY(block.startOffset)
        val editorParent = editor.contentComponent.topLevelAncestor.locationOnScreen
        val editorPosition = editor.contentComponent.locationOnScreen
        position.translate(editorPosition.x - editorParent.x, editorPosition.y - editorParent.y)
        position.translate(
            QuarkdownTableInlayProperties.leftRightPadding * 2 + QuarkdownVerticalBarPresentation.barWidth,
            -editor.lineHeight
        )
        position.translate(0, -componentHeight)
        val rect = barsModel.getOrNull(columnIndex) ?: return position
        position.translate(rect.x, -rect.y - barHeight * 2 - 2)
        return position
    }

    private fun showToolbar(columnIndex: Int) {
        // Public-API replacement for the internal ToolbarUtils.createTargetComponent:
        // a component that provides the table snapshot data to the toolbar's actions.
        val targetComponent = QuarkdownActionToolbarUtils.createTargetComponent(editor) { sink ->
            TableActionKeys.putColumnSnapshot(sink, block, columnIndex)
        }
        // Public-API replacement for ToolbarUtils.createImmediatelyUpdatedToolbar.
        val toolbar = QuarkdownActionToolbarUtils.createToolbar(
            TableActionPlaces.TABLE_INLAY_TOOLBAR, columnActionGroup, true, targetComponent
        )
        // The toolbar must be attached to a container AND populated before its actions are
        // usable; populateImmediately waits for the (possibly asynchronous) toolbar update to
        // complete before invoking the callback (see QuarkdownActionToolbarUtils). The panel
        // below becomes the hint content and is only shown once the toolbar has buttons.
        val content = com.intellij.util.ui.components.BorderLayoutPanel().apply {
            addToCenter(toolbar.component)
        }
        QuarkdownActionToolbarUtils.populateImmediately(toolbar, editor.contentComponent) {
            if (editor.isDisposed) return@populateImmediately
            if (!it.hasVisibleActions()) return@populateImmediately
            createAndShowHint(content, columnIndex)
        }
    }

    private fun createAndShowHint(content: JComponent, columnIndex: Int) {
        val hint = LightweightHint(content)
        hint.setForceShowAsPopup(true)
        val targetPoint = calculateToolbarPosition(hint.component.preferredSize.height, columnIndex)
        val hintManager = HintManagerImpl.getInstanceImpl()
        hintManager.hideAllHints()
        val flags = HintManager.HIDE_BY_ANY_KEY or HintManager.HIDE_BY_SCROLLING or
                HintManager.HIDE_BY_CARET_MOVE or HintManager.HIDE_BY_TEXT_CHANGE
        hintManager.showEditorHint(hint, editor, targetPoint, flags, 0, false)
    }

    private fun handleMouseLeftDoubleClick(translated: Point) {
        val columnIndex = barsModel.indexOfFirst { it.contains(translated) }.takeUnless { it < 0 } ?: return
        invokeLater {
            executeCommand(editor.project) {
                QuarkdownTableModificationUtils.selectColumn(
                    editor.project,
                    editor,
                    block,
                    columnIndex,
                    withBorders = true
                )
            }
        }
    }

    private fun handleMouseLeftClick(translated: Point) {
        val columnIndex = barsModel.indexOfFirst { it.contains(translated) }.takeUnless { it < 0 } ?: return
        showToolbar(columnIndex)
    }

    private fun actuallyPaintBars(graphics: Graphics2D, rect: Rectangle, hover: Boolean) {
        graphics.color =
            if (hover) QuarkdownTableInlayProperties.barHoverColor else QuarkdownTableInlayProperties.barColor
        graphics.fillRoundRect(rect.x, 0, rect.width, barHeight, barHeight, barHeight)
        graphics.clearOvalOverEditor(rect.x, 0, barHeight, barHeight)
        graphics.clearOvalOverEditor(rect.x + rect.width - barHeight, 0, barHeight, barHeight)
    }

    private fun paintBars(graphics: Graphics2D) {
        val currentBarsModel = barsModel
        for ((index, rect) in currentBarsModel.withIndex()) {
            actuallyPaintBars(graphics, rect, hover = lastSelectedIndex == index)
        }
        repeat(2) {
            for (rect in currentBarsModel) {
                graphics.color = QuarkdownTableInlayProperties.barColor
                graphics.fillOval(rect.x, 0, barHeight, barHeight)
                graphics.fillOval(rect.x + rect.width - barHeight, 0, barHeight, barHeight)
            }
        }
    }

    companion object {
        const val barHeight = QuarkdownTableInlayProperties.barSize
        const val leftPadding =
            QuarkdownVerticalBarPresentation.barWidth + QuarkdownTableInlayProperties.leftRightPadding * 2
        private val emptyBoundsState = BoundsState(0, 0, emptyList())
        private val columnActionGroup: ActionGroup
            get() = ActionManager.getInstance().getAction("Quarkdown.TableColumnActions") as ActionGroup

        private fun obtainFontMetrics(editor: Editor): FontMetrics {
            val font = editor.colorsScheme.getFont(EditorFontType.PLAIN)
            return editor.contentComponent.getFontMetrics(font)
        }

        fun create(
            factory: PresentationFactory,
            editor: Editor,
            block: QuarkdownTableModificationUtils.TableBlock
        ): InlayPresentation {
            val presentation =
                QuarkdownPresentationWithCustomCursor(editor, QuarkdownHorizontalBarPresentation(editor, block))
            return factory.inset(
                presentation,
                left = leftPadding,
                top = QuarkdownTableInlayProperties.topDownPadding,
                down = QuarkdownTableInlayProperties.topDownPadding
            )
        }
    }
}

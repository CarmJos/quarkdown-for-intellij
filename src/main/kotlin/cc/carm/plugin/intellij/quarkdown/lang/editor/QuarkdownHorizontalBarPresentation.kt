@file:Suppress("UnstableApiUsage")
package cc.carm.plugin.intellij.quarkdown.lang.editor
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import cc.carm.plugin.intellij.quarkdown.lang.editor.QuarkdownGraphicsUtils.clearOvalOverEditor
import cc.carm.plugin.intellij.quarkdown.lang.editor.QuarkdownGraphicsUtils.useCopy
import com.intellij.codeInsight.hints.fireUpdateEvent
import com.intellij.codeInsight.hints.presentation.BasePresentation
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.impl.ToolbarUtils
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.ui.LightweightHint
import com.intellij.util.ui.GraphicsUtil
import java.awt.Dimension
import java.awt.FontMetrics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities
internal class QuarkdownHorizontalBarPresentation(
    private val editor: Editor,
    private val block: QuarkdownTableModificationUtils.TableBlock
) : BasePresentation() {
    private data class BoundsState(val width: Int, val height: Int, val barsModel: List<Rectangle>)
    private var lastSelectedIndex: Int? = null
    private var boundsState = emptyBoundsState
    init {
        val document = editor.document
        val project = editor.project
        EditorUtil.disposeWithEditor(editor) { }
        document.addDocumentListener(object : com.intellij.openapi.editor.event.DocumentListener {
            override fun documentChanged(event: com.intellij.openapi.editor.event.DocumentEvent) {
                if (isInvalid) return
                invokeLater(ModalityState.stateForComponent(editor.contentComponent)) {
                    if (!isInvalid) {
                        boundsState = calculateCurrentBoundsState(document)
                        fireUpdateEvent(Dimension(0, 0))
                    }
                }
            }
        })
        if (project != null) {
            PsiDocumentManager.getInstance(project).performForCommittedDocument(document) {
                invokeLater(ModalityState.stateForComponent(editor.contentComponent)) {
                    if (!isInvalid) {
                        boundsState = calculateCurrentBoundsState(document)
                        fireSizeChanged(Dimension(0, 0), Dimension(boundsState.width, boundsState.height))
                    }
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
            SwingUtilities.isLeftMouseButton(event) && event.clickCount.mod(2) == 0 -> handleMouseLeftDoubleClick(translated)
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
    private fun calculateCurrentBoundsState(document: com.intellij.openapi.editor.Document): BoundsState {
        if (isInvalid) return emptyBoundsState
        val metrics = obtainFontMetrics(editor)
        val width = calculateRowWidth(metrics, document)
        val barsModel = buildBarsModel(metrics, document)
        return BoundsState(width, QuarkdownTableInlayProperties.barSize, barsModel)
    }
    private fun calculateRowWidth(fontMetrics: FontMetrics, document: com.intellij.openapi.editor.Document): Int {
        val headerLine = block.lines.firstOrNull() ?: return 0
        val range = TextRange(block.lineStarts[0], block.lineStarts[0] + headerLine.length)
        return fontMetrics.stringWidth(document.getText(range))
    }
    private fun buildBarsModel(fontMetrics: FontMetrics, document: com.intellij.openapi.editor.Document): List<Rectangle> {
        val positions = calculatePositions(fontMetrics, document)
        val sectors = positions.windowed(2).map { (left, right) -> left to (right - left) }
        return sectors.map { (offset, sectorWidth) ->
            Rectangle(offset - barHeight / 2, 0, sectorWidth + barHeight, barHeight)
        }
    }
    private fun calculatePositions(fontMetrics: FontMetrics, document: com.intellij.openapi.editor.Document): List<Int> {
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
        position.translate(QuarkdownTableInlayProperties.leftRightPadding * 2 + QuarkdownVerticalBarPresentation.barWidth, -editor.lineHeight)
        position.translate(0, -componentHeight)
        val rect = barsModel.getOrNull(columnIndex) ?: return position
        position.translate(rect.x, -rect.y - barHeight * 2 - 2)
        return position
    }
    private fun showToolbar(columnIndex: Int) {
        val targetComponent = ToolbarUtils.createTargetComponent(editor) { sink ->
            QuarkdownTableActionKeys.putColumnSnapshot(sink, block, columnIndex)
        }
        ToolbarUtils.createImmediatelyUpdatedToolbar(
            group = columnActionGroup,
            place = QuarkdownTableActionPlaces.TABLE_INLAY_TOOLBAR,
            targetComponent,
            horizontal = true,
            onUpdated = { toolbar -> createAndShowHint(toolbar, columnIndex) }
        )
    }
    private fun createAndShowHint(toolbar: ActionToolbar, columnIndex: Int) {
        val hint = LightweightHint(toolbar.component)
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
                QuarkdownTableModificationUtils.selectColumn(editor.project, editor, block, columnIndex, withBorders = true)
            }
        }
    }
    private fun handleMouseLeftClick(translated: Point) {
        val columnIndex = barsModel.indexOfFirst { it.contains(translated) }.takeUnless { it < 0 } ?: return
        showToolbar(columnIndex)
    }
    private fun actuallyPaintBars(graphics: Graphics2D, rect: Rectangle, hover: Boolean) {
        graphics.color = if (hover) QuarkdownTableInlayProperties.barHoverColor else QuarkdownTableInlayProperties.barColor
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
        const val leftPadding = QuarkdownVerticalBarPresentation.barWidth + QuarkdownTableInlayProperties.leftRightPadding * 2
        private val emptyBoundsState = BoundsState(0, 0, emptyList())
        private val columnActionGroup: ActionGroup
            get() = ActionManager.getInstance().getAction("Quarkdown.TableColumnActions") as ActionGroup
        private fun obtainFontMetrics(editor: Editor): FontMetrics {
            val font = editor.colorsScheme.getFont(EditorFontType.PLAIN)
            return editor.contentComponent.getFontMetrics(font)
        }
        fun create(factory: PresentationFactory, editor: Editor, block: QuarkdownTableModificationUtils.TableBlock): InlayPresentation {
            val presentation = QuarkdownPresentationWithCustomCursor(editor, QuarkdownHorizontalBarPresentation(editor, block))
            return factory.inset(
                presentation,
                left = leftPadding,
                top = QuarkdownTableInlayProperties.topDownPadding,
                down = QuarkdownTableInlayProperties.topDownPadding
            )
        }
    }
}

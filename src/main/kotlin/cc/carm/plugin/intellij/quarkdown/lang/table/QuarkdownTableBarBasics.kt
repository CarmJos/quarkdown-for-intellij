@file:Suppress("UnstableApiUsage")

package cc.carm.plugin.intellij.quarkdown.lang.table

import com.intellij.codeInsight.hints.presentation.BasePresentation
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.codeInsight.hints.presentation.PresentationListener
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil
import java.awt.*
import java.awt.event.MouseEvent
import java.awt.geom.Arc2D

internal object QuarkdownTableInlayProperties {
    const val barSize = 6
    const val topDownPadding = 2
    const val leftRightPadding = 2
    val barColor: Color get() = UIUtil.toAlpha(JBColor.DARK_GRAY, 50)
    val barHoverColor: Color get() = UIUtil.toAlpha(JBColor.BLUE, 50)
    val circleColor: Color get() = barColor
}

internal object QuarkdownGraphicsUtils {
    private fun Graphics2D.clearShapeOverEditor(drawShape: Graphics2D.() -> Unit) {
        val originalComposite = composite
        val originalPaint = paint
        composite = AlphaComposite.Src
        color = EditorColorsManager.getInstance().globalScheme.defaultBackground
        drawShape.invoke(this)
        paint = originalPaint
        composite = originalComposite
    }

    fun Graphics2D.clearOvalOverEditor(x: Int, y: Int, width: Int, height: Int) =
        clearShapeOverEditor { fillOval(x, y, width, height) }

    fun Graphics2D.clearHalfOvalOverEditor(x: Int, y: Int, width: Int, height: Int, upper: Boolean) =
        clearShapeOverEditor { fillHalfOval(x, y, width, height, upper) }

    fun Graphics2D.fillHalfOval(x: Int, y: Int, width: Int, height: Int, upperHalf: Boolean) {
        val start = if (upperHalf) 180.0 else 0.0
        val arc = Arc2D.Double(x.toDouble(), y.toDouble(), width.toDouble(), height.toDouble(), start, 180.0, Arc2D.PIE)
        fill(arc)
    }

    fun <T> Graphics2D.useCopy(block: (Graphics2D) -> T): T {
        val local = create() as Graphics2D
        try {
            return block(local)
        } finally {
            local.dispose()
        }
    }
}

internal class QuarkdownPresentationWithCustomCursor(
    private val editor: Editor,
    private val delegate: InlayPresentation
) : BasePresentation() {

    private val delegateListener = object : PresentationListener {
        override fun contentChanged(area: Rectangle) = fireContentChanged(area)
        override fun sizeChanged(previousSize: Dimension, newSize: Dimension) =
            fireSizeChanged(previousSize, newSize)
    }

    init {
        // Forward size/content changes of the wrapped presentation to our listeners.
        delegate.addListener(delegateListener)
    }

    override val width: Int get() = delegate.width
    override val height: Int get() = delegate.height

    override fun paint(graphics: Graphics2D, attributes: TextAttributes) =
        delegate.paint(graphics, attributes)

    override fun mouseClicked(event: MouseEvent, translated: Point) =
        delegate.mouseClicked(event, translated)

    override fun mousePressed(event: MouseEvent, translated: Point) =
        delegate.mousePressed(event, translated)

    override fun mouseReleased(event: MouseEvent, translated: Point) =
        delegate.mouseReleased(event, translated)

    override fun mouseMoved(event: MouseEvent, translated: Point) {
        (editor as? EditorImpl)?.setCustomCursor(cursorRequestor, Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR))
        delegate.mouseMoved(event, translated)
    }

    override fun mouseExited() {
        (editor as? EditorImpl)?.setCustomCursor(cursorRequestor, null)
        delegate.mouseExited()
    }

    override fun translatePoint(p: Point): Point = delegate.translatePoint(p)

    override fun updateState(prevPresentation: InlayPresentation): Boolean =
        delegate.updateState(prevPresentation)

    override fun toString(): String = delegate.toString()

    companion object {
        private val cursorRequestor = Object()
    }
}

@file:Suppress("UnstableApiUsage")
package cc.carm.plugin.intellij.quarkdown.lang.editor
import com.intellij.codeInsight.hints.presentation.DynamicDelegatePresentation
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Cursor
import java.awt.Graphics2D
import java.awt.Point
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
    fun Graphics2D.clearOvalOverEditor(x: Int, y: Int, width: Int, height: Int) = clearShapeOverEditor { fillOval(x, y, width, height) }
    fun Graphics2D.clearHalfOvalOverEditor(x: Int, y: Int, width: Int, height: Int, upper: Boolean) = clearShapeOverEditor { fillHalfOval(x, y, width, height, upper) }
    fun Graphics2D.fillHalfOval(x: Int, y: Int, width: Int, height: Int, upperHalf: Boolean) {
        val start = if (upperHalf) 180.0 else 0.0
        val arc = Arc2D.Double(x.toDouble(), y.toDouble(), width.toDouble(), height.toDouble(), start, 180.0, Arc2D.PIE)
        fill(arc)
    }
    fun <T> Graphics2D.useCopy(block: (Graphics2D) -> T): T {
        val local = create() as Graphics2D
        try { return block(local) } finally { local.dispose() }
    }
}
internal class QuarkdownPresentationWithCustomCursor(
    private val editor: Editor,
    delegate: InlayPresentation
) : DynamicDelegatePresentation(delegate) {
    override fun mouseMoved(event: MouseEvent, translated: Point) {
        super.mouseMoved(event, translated)
        (editor as? EditorImpl)?.setCustomCursor(cursorRequestor, Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR))
    }
    override fun mouseExited() {
        (editor as? EditorImpl)?.setCustomCursor(cursorRequestor, null)
        super.mouseExited()
    }
    companion object { private val cursorRequestor = Object() }
}

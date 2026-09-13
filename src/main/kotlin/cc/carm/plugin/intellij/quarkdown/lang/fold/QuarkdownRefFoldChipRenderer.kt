package cc.carm.plugin.intellij.quarkdown.lang.fold

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.util.ui.JBUI
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Rectangle2D
import javax.swing.Icon

/**
 * Paints the collapsed `.ref {id}` preview chip: the icon of the referenced element kind
 * (figure / table / code / equation / section) followed by its caption, styled like a
 * standard fold placeholder (rounded [EditorColors.FOLDED_TEXT_ATTRIBUTES] background).
 *
 * The chip replaces the plain-text placeholder (`Figure …`, `Table …`) that a fold region
 * alone could show, since fold placeholders only support text.
 */
class QuarkdownRefFoldChipRenderer(
    private val editor: Editor,
    private val icon: Icon?,
    private val caption: String,
    /** `kind + caption` signature used by the synchronizer to detect stale chips. */
    val signature: String,
) : EditorCustomElementRenderer {

    override fun calcWidthInPixels(inlay: Inlay<*>): Int = chipWidth()

    override fun calcHeightInPixels(inlay: Inlay<*>): Int = editor.lineHeight

    /** Total chip width: horizontal padding + optional icon + optional gap + caption. */
    fun chipWidth(): Int = contentWidth() + H_PADDING * 2

    private fun contentWidth(): Int {
        var width = 0
        if (icon != null) width += icon.iconWidth
        if (icon != null && caption.isNotEmpty()) width += ICON_TEXT_GAP
        if (caption.isNotEmpty()) width += editor.component.getFontMetrics(font()).stringWidth(caption)
        return width
    }

    private fun font() = editor.colorsScheme.getFont(EditorFontType.PLAIN)

    override fun paint(inlay: Inlay<*>, graphics: Graphics2D, region: Rectangle2D, textAttributes: TextAttributes) {
        val scheme = editor.colorsScheme
        val folded = scheme.getAttributes(EditorColors.FOLDED_TEXT_ATTRIBUTES)
        val background = folded?.backgroundColor ?: textAttributes.backgroundColor
        val foreground = folded?.foregroundColor ?: textAttributes.foregroundColor ?: editor.component.foreground

        val x = region.x.toInt()
        val y = region.y.toInt()
        val width = calcWidthInPixels(inlay)
        val height = calcHeightInPixels(inlay)

        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        if (background != null) {
            graphics.color = background
            graphics.fillRoundRect(x, y, width - 1, height - 1, CORNER_ARC, CORNER_ARC)
        }

        var cursor = x + H_PADDING
        if (icon != null) {
            icon.paintIcon(editor.component, graphics, cursor, y + (height - icon.iconHeight) / 2)
            cursor += icon.iconWidth
            if (caption.isNotEmpty()) cursor += ICON_TEXT_GAP
        }
        if (caption.isNotEmpty() && foreground != null) {
            graphics.color = foreground
            graphics.font = font()
            val metrics = graphics.fontMetrics
            graphics.drawString(caption, cursor, y + (height - metrics.height) / 2 + metrics.ascent)
        }
    }

    private companion object {
        val H_PADDING: Int = JBUI.scale(5)
        val ICON_TEXT_GAP: Int = JBUI.scale(4)
        val CORNER_ARC: Int = JBUI.scale(8)
    }
}

package cc.carm.plugin.intellij.quarkdown.lang.marker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.awt.Point
import java.awt.event.MouseEvent
import javax.swing.JPanel

/**
 * Verifies where the multi-equation chooser of [QuarkdownEquationLineMarkerProvider] is anchored.
 *
 * The chooser used to be shown with `showUnderneathOf(event.component)`, which is wrong for a
 * gutter click: the event is delivered to the *whole* gutter (or editor) component, so "underneath"
 * it means underneath the entire editor — the chooser appeared at the bottom of the screen instead
 * of at the icon that was clicked. The anchor must therefore be the click itself.
 */
class QuarkdownEquationPopupAnchorTest {

    private val provider = QuarkdownEquationLineMarkerProvider()

    private fun clickOn(component: JPanel, x: Int, y: Int): MouseEvent = MouseEvent(
        component,
        MouseEvent.MOUSE_RELEASED,
        System.currentTimeMillis(),
        0,
        x,
        y,
        1,
        false,
        MouseEvent.BUTTON1,
    )

    @Test
    fun `the chooser is anchored at the click, not at the component it happened in`() {
        val gutter = JPanel()

        val anchor = provider.popupAnchorFor(clickOn(gutter, CLICK_X, CLICK_Y))

        assertSame("the anchor must stay relative to the clicked component", gutter, anchor.component)
        assertEquals("the anchor must be the click, not a corner of the component", Point(CLICK_X, CLICK_Y), anchor.point)
    }

    @Test
    fun `two clicks in one component anchor at their own positions`() {
        val gutter = JPanel()

        val first = provider.popupAnchorFor(clickOn(gutter, 10, 20))
        val second = provider.popupAnchorFor(clickOn(gutter, 10, 200))

        assertEquals(Point(10, 20), first.point)
        assertEquals(Point(10, 200), second.point)
    }

    private companion object {
        const val CLICK_X = 42
        const val CLICK_Y = 17
    }
}

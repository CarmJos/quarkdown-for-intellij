package cc.carm.plugin.intellij.quarkdown.action.equation

import cc.carm.plugin.intellij.quarkdown.lang.latex.QuarkdownLatexFileType
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.EditorTextField
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import javax.swing.JComponent

/**
 * Layout regression tests for [EquationDialog].
 *
 * The dialog shows the typeset preview *above* the TeX input. The embedded browser is the greedy
 * party here: JCEF's component asks for **800×600**, which in a 540 px dialog consumed everything
 * and left the input area with a *negative* height — the input field appeared to be missing.
 *
 * Two properties keep the input usable and are pinned below:
 *
 *  1. the preview is given a bounded height, so a greedy browser cannot take the dialog;
 *  2. the body is built once, because [com.intellij.openapi.ui.DialogWrapper] may ask for the
 *     centre panel more than once and rebuilding it re-adds children to the same container.
 *
 * The input is an [EditorTextField] (a real editor with line numbers and TeX coloring), so the
 * searches below look for that component rather than for a text area.
 */
class EquationDialogTest : BasePlatformTestCase() {

    /** Sizes [component] to its preferred size and lays it out, including all descendants. */
    private fun layoutTree(component: JComponent): JComponent {
        fun visit(c: Component) {
            if (c is Container) {
                c.doLayout()
                c.components.forEach { visit(it) }
            }
        }
        component.setSize(component.preferredSize)
        component.doLayout()
        visit(component)
        return component
    }

    private fun findContentArea(component: Component): EditorTextField? {
        if (component is EditorTextField) return component
        if (component is Container) {
            for (child in component.components) {
                findContentArea(child)?.let { return it }
            }
        }
        return null
    }

    fun `test the content input is present with a usable height`() {
        val dialog = EquationDialog(project, "", null)
        try {
            val panel = layoutTree(dialog.buildPanelForTest())

            val content = findContentArea(panel)
            assertNotNull("the dialog must contain the TeX content input", content)
            assertTrue("the content input must be visible", content!!.isVisible)
            assertTrue(
                "the content input must keep a usable height, height=${content.height}",
                content.height >= 200,
            )
        } finally {
            dialog.disposeForTest()
        }
    }

    fun `test the content input is an editor for the TeX file type`() {
        val dialog = EquationDialog(project, "\\frac{a}{b}", null)
        try {
            val content = findContentArea(dialog.buildPanelForTest())
            assertNotNull("the dialog must contain the TeX content editor", content)
            assertEquals(
                "the input must be highlighted as TeX",
                QuarkdownLatexFileType.INSTANCE,
                content!!.fileType,
            )
        } finally {
            dialog.disposeForTest()
        }
    }

    fun `test a greedy preview cannot squeeze the input out of view`() {
        // Emulates what JCEF actually does (its component requests 800x600) so the check works even
        // when the embedded browser is unavailable in the test JVM.
        val dialog = EquationDialog(project, "", null)
        try {
            val panel = dialog.buildPanelForTest()
            val content = findContentArea(panel)!!

            val preview = dialog.previewComponentForTest()
            preview.preferredSize = Dimension(800, 600)
            layoutTree(panel)

            assertTrue(
                "the preview must not squeeze the input, height=${content.height}",
                content.height > 0,
            )
        } finally {
            dialog.disposeForTest()
        }
    }

    fun `test disposing the dialog disposes the preview view`() {
        val dialog = EquationDialog(project, "", null)
        val view = dialog.previewViewForTest()
        assertFalse("the view must be alive while its dialog is", Disposer.isDisposed(view))

        dialog.disposeForTest()

        // The view owns the embedded browser and the loopback asset server, and registering them
        // puts the view itself into the Disposer tree: if it is not disposed through the Disposer it
        // stays a child of ROOT_DISPOSABLE and the IDE reports a memory leak when it exits.
        assertTrue(
            "the preview view must be disposed together with the dialog",
            Disposer.isDisposed(view),
        )
    }

    fun `test asking for the panel twice keeps the layout intact`() {
        val dialog = EquationDialog(project, "", null)
        try {
            val first = dialog.buildPanelForTest()
            val second = dialog.buildPanelForTest()
            assertSame("the dialog body must be built once", first, second)

            val panel = layoutTree(second)
            val content = findContentArea(panel)
            assertNotNull("the content input must survive a repeated request", content)
            assertTrue(
                "the content input must keep a positive height, height=${content!!.height}",
                content.height > 0,
            )
        } finally {
            dialog.disposeForTest()
        }
    }
}


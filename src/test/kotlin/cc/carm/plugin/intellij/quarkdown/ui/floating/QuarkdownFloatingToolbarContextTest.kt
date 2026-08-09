package cc.carm.plugin.intellij.quarkdown.ui.floating

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/**
 * Verifies the floating toolbar's "is non-prose context" heuristics:
 * the toolbar must NOT appear over function arguments, image paths or inline code,
 * but SHOULD appear over ordinary prose.
 */
class QuarkdownFloatingToolbarContextTest : BasePlatformTestCase() {

    private val isNonProse = FormattingFloatingToolbar.Companion::isNonProseContext

    fun `test function call argument is non-prose`() {
        val text = ".fullwidth {hello world}\n"
        val offset = text.indexOf("hello")
        assertTrue(isNonProse(text, offset))
    }

    fun `test named argument margin value is non-prose`() {
        val text = ".pageformat pages:{..1} margin:{0}\n"
        val offset = text.indexOf("{0}") + 1
        assertTrue("named argument value must be non-prose", isNonProse(text, offset))
    }

    fun `test resetpagenumber name is non-prose`() {
        val text = ".resetpagenumber\n"
        val offset = text.indexOf("resetpagenumber") + 1
        assertTrue("function name must be non-prose", isNonProse(text, offset))
    }

    fun `test pageformat pages value is non-prose`() {
        val text = ".pageformat pages:{..1} margin:{0}\n"
        val offset = text.indexOf("..1") + 1
        assertTrue("pages value must be non-prose", isNonProse(text, offset))
    }

    fun `test plain prose is not non-prose`() {
        val text = "This is plain prose text.\n"
        assertFalse(isNonProse(text, 5))
    }

    fun `test image path is non-prose`() {
        val text = "![logo](assets/logo.png)\n"
        val offset = text.indexOf("assets")
        assertTrue(isNonProse(text, offset))
    }

    fun `test link destination is non-prose`() {
        val text = "click [here](https://example.com)\n"
        val offset = text.indexOf("https")
        assertTrue(isNonProse(text, offset))
    }

    fun `test inline code is non-prose`() {
        val text = "use `code` here\n"
        val offset = text.indexOf("code")
        assertTrue(isNonProse(text, offset))
    }

    fun `test prose with inline link text is still prose`() {
        val text = "The [label] is part of prose\n"
        val offset = text.indexOf("label")
        assertFalse(isNonProse(text, offset))
    }
}

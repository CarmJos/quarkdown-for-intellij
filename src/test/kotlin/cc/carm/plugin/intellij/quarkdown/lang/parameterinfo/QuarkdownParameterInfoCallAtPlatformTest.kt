package cc.carm.plugin.intellij.quarkdown.lang.parameterinfo

import cc.carm.plugin.intellij.quarkdown.lang.function.QuarkdownCallParser
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Verifies `callAt` — the caret-position → function-call resolution used by Ctrl+P
 * parameter info. In particular that a freshly completed function name (e.g. the LSP
 * inserts `pageformat ` with the caret in the pending-argument position) still resolves
 * to the call so the signature popup can appear.
 */
class QuarkdownParameterInfoCallAtPlatformTest : BasePlatformTestCase() {

    private val handler = QuarkdownParameterInfoHandler()

    private fun callAt(text: String, caret: Int): QuarkdownCallParser.Call? {
        val file = myFixture.configureByText("c.qd", text)
        return handler.callAt(file, caret)
    }

    fun `test pending argument position after completed function name resolves`() {
        // `.pageformat ` — caret right after the trailing space the LSP snippet leaves
        // when completing the function name. Must resolve to the call (Ctrl+P works).
        val text = ".pageformat "
        val call = callAt(text, text.length)
        assertNotNull("caret after the completed function name must resolve to the call", call)
        assertEquals("pageformat", call!!.name)
    }

    fun `test caret right after name resolves`() {
        // `.pageformat` — caret at the end (right after the last letter of the name).
        val call = callAt(".pageformat", ".pageformat".length)
        assertNotNull("caret right after the name resolves", call)
    }

    fun `test caret inside args still resolves`() {
        val call = callAt(".multiply {6} by:{3}", ".multiply {".length)
        assertNotNull("caret inside the first arg resolves", call)
    }

    fun `test caret after a space after all args resolves`() {
        // Next-argument position: `.multiply {6} by:{3} ` — caret after the trailing space.
        val text = ".multiply {6} by:{3} "
        val call = callAt(text, text.length)
        assertNotNull("next-argument position resolves", call)
    }

    fun `test caret in prose on the next line does not resolve`() {
        val text = ".pageformat\nsome prose"
        val caret = text.indexOf("some")
        val call = callAt(text, caret)
        assertNull("caret on a following prose line must not resolve to the call", call)
    }

    fun `test caret before the function name does not resolve`() {
        val call = callAt(".pageformat {a4}", 2)
        assertNull("caret on the name itself must not resolve", call)
    }
}

package cc.carm.plugin.intellij.quarkdown.lang.marker

import cc.carm.plugin.intellij.quarkdown.QuarkdownLanguage
import cc.carm.plugin.intellij.quarkdown.action.code.CodeBlockDialog
import cc.carm.plugin.intellij.quarkdown.lang.codeblock.QuarkdownCodeBlockSyntax
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviders
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.util.ui.UIUtil
import javax.swing.text.JTextComponent

/**
 * Verifies the code block gutter marker is registered on the platform for the Quarkdown
 * language and that it is produced for both fenced and `.code` code blocks.
 */
class QuarkdownCodeBlockMarkerPlatformTest : BasePlatformTestCase() {

    fun `test code block line marker registered for quarkdown`() {
        val providers = LineMarkerProviders.getInstance()
            .allForLanguage(QuarkdownLanguage.INSTANCE)
        assertTrue(
            "QuarkdownCodeBlockLineMarkerProvider should be registered",
            providers.any { it is QuarkdownCodeBlockLineMarkerProvider }
        )
    }

    fun `test markers for fenced and code function blocks`() {
        myFixture.configureByText(
            "c.qd",
            "```python \"Fibonacci function\" {#fibonacci}\n" +
                    "def fibonacci(n):\n" +
                    "    return n\n" +
                    "```\n" +
                    "\n" +
                    ".code lang:{python} caption:{Fibonacci function} ref:{example}\n" +
                    "    def fibonacci(n):\n" +
                    "        return n\n"
        )
        val elements = PsiTreeUtil.collectElements(myFixture.file) { true }.toMutableList()
        val result = mutableListOf<LineMarkerInfo<*>>()
        QuarkdownCodeBlockLineMarkerProvider().collectSlowLineMarkers(elements, result)
        assertEquals("one marker per code block", 2, result.size)
    }

    fun `test dialog pre-fills language and builds the fence line`() {
        val info = QuarkdownCodeBlockSyntax.parseFenceLine("```python \"Fib\" {#fib}")!!
        val dialog = CodeBlockDialog(project, QuarkdownCodeBlockSyntax.Kind.FENCED)
        dialog.parseFence(info)
        assertEquals("python", dialog.getLanguageForTest())
        dialog.setLanguageForTest("go")
        assertEquals("```go \"Fib\" {#fib}", dialog.buildLine())
    }

    fun `test dialog language combo lists all languages and filters while typing`() {
        val dialog = CodeBlockDialog(project, QuarkdownCodeBlockSyntax.Kind.FENCED)
        val combo = dialog.languageComboForTest()!!
        val editor = combo.editor.editorComponent as JTextComponent

        // The initial dropdown lists every known code block language.
        assertTrue((combo.model as CollectionComboBoxModel<*>).size > 20)

        // Simulate typing a prefix: the dropdown narrows to matching languages, and the
        // caret/typed text are never disturbed (regression: IntelliJ's ComboBox would
        // re-configure the editor to the selected item and clear/select the text).
        editor.text = ""
        editor.text = "pyt"
        UIUtil.dispatchAllInvocationEvents()
        val items = (combo.model as CollectionComboBoxModel<*>).items.toList().map { it as String }
        assertTrue(items.isNotEmpty())
        assertTrue(items.all { it.startsWith("pyt", ignoreCase = true) })

        // The typed text and caret are preserved (never auto-completed or overwritten).
        assertEquals("pyt", editor.text)
        assertEquals(3, editor.caretPosition)
    }

    fun `test dialog language combo keeps prefilled text on popup reconfiguration`() {
        val dialog = CodeBlockDialog(project, QuarkdownCodeBlockSyntax.Kind.FENCED)
        val combo = dialog.languageComboForTest()!!
        val editor = combo.editor.editorComponent as JTextComponent

        dialog.setLanguageForTest("python")
        assertEquals("python", editor.text)

        // Opening the popup makes IntelliJ's ComboBox re-configure the editor to the
        // selected item; the model selection must be kept in sync so the text survives.
        combo.configureEditor(combo.editor, combo.selectedItem)
        UIUtil.dispatchAllInvocationEvents()
        assertEquals("python", editor.text)

        // Typing after that reconfiguration still works normally.
        editor.text = "pythonx"
        UIUtil.dispatchAllInvocationEvents()
        assertEquals("pythonx", editor.text)
        assertEquals(7, editor.caretPosition)
    }

    fun `test dialog language combo preserves a custom language value`() {
        val dialog = CodeBlockDialog(project, QuarkdownCodeBlockSyntax.Kind.CODE_FUNCTION)
        dialog.parseCodeFunction(
            QuarkdownCodeBlockSyntax.parseCodeFunctionLine(
                ".code lang:{python} caption:{Fib} ref:{x}"
            )!!
        )
        dialog.setLanguageForTest("mycustomlang")
        assertEquals(
            ".code lang:{mycustomlang} caption:{Fib} ref:{x}",
            dialog.buildLine()
        )
    }
}

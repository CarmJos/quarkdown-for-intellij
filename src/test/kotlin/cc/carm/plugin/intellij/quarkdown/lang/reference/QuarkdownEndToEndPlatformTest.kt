package cc.carm.plugin.intellij.quarkdown.lang.reference

import com.intellij.psi.PsiReference
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * End-to-end tests that mirror how a real user exercises the plugin in the IDE:
 * Find Usages (via the platform factory), Ctrl+Click navigation, plain-text sanity
 * and lexer tokenisation of `.ref {id}` / `{#id}`.
 */
class QuarkdownEndToEndPlatformTest : BasePlatformTestCase() {

    private fun caretAt(text: String, needle: String): Int {
        val idx = text.indexOf(needle)
        assertTrue("needle '$needle' not found in:\n$text", idx >= 0)
        return idx
    }

    fun `test lexer keeps ref id and label id as single tokens`() {
        val text = "See .ref {plc-symbol-table-output}.\n\n{#plc-symbol-table-output}"
        myFixture.configureByText("tokens.qd", text)

        val psiFile = myFixture.file
        val leaf1 = psiFile.findElementAt(text.indexOf("{plc-symbol-table-output}"))
        val leaf2 = psiFile.findElementAt(text.indexOf("{#plc-symbol-table-output}") + 1)

        System.out.println("ref leaf='${leaf1?.text}' class=${leaf1?.javaClass?.simpleName}")
        System.out.println("label leaf='${leaf2?.text}' class=${leaf2?.javaClass?.simpleName}")

        // The whole id (including braces) must be a single leaf, so it is not split
        // into `plc` / `symbol` / `table` / `output`.
        assertTrue("ref id should be one token", leaf1?.text == "{plc-symbol-table-output}")
        assertTrue("label id should be one token", leaf2?.text == "{#plc-symbol-table-output}")
    }

    fun `test ctrl click on label shows all usages via multiResolve`() {
        val text = "First .ref {multi-click}.\nSecond .ref {multi-click}.\n\n{#multi-click}"
        myFixture.configureByText("ctrlclick.qd", text)

        val labelStart = text.indexOf("{#multi-click}") + 2
        myFixture.editor.caretModel.moveToOffset(labelStart + 2)

        val ref = myFixture.file.findReferenceAt(labelStart + 2)
        assertNotNull("findReferenceAt should find a reference on the label", ref)

        val poly = ref as? com.intellij.psi.PsiPolyVariantReference
        assertNotNull("reference must be poly variant for the usage popup", poly)
        val results = poly!!.multiResolve(false)
        System.out.println("multiResolve returned ${results.size} targets")
        assertTrue("multiResolve should return both .ref usages", results.size == 2)
    }

    fun `test plain text is not navigable`() {
        val text = "Ordinary prose line with no ids.\n\nJust more text here."
        myFixture.configureByText("plain-e2e.qd", text)

        for (i in text.indices) {
            if (text[i] == '\n') continue
            val ref = myFixture.file.findReferenceAt(i)
            assertNull("plain text at offset $i ('${text[i]}') should have no reference", ref)
        }
    }

    fun `test ctrl click on ref id resolves to label declaration`() {
        val text = "See .ref {go-to-label}.\n\n# Heading {#go-to-label}"
        myFixture.configureByText("goto.qd", text)

        val refIdStart = text.indexOf("{go-to-label}") + 1
        myFixture.editor.caretModel.moveToOffset(refIdStart + 3)

        val ref = myFixture.file.findReferenceAt(refIdStart + 3)
        assertNotNull("findReferenceAt on ref id should find a reference", ref)

        val target = ref!!.resolve()
        assertNotNull("ref should resolve to the label", target)
        assertTrue(
            "resolved target should be at the {#go-to-label} declaration",
            target!!.textOffset >= text.indexOf("{#go-to-label}")
        )
    }
}

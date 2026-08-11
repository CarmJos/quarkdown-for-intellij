package cc.carm.plugin.intellij.quarkdown.lang.reference

import cc.carm.plugin.intellij.quarkdown.QuarkdownLanguage
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Regression test for the reference rework regression: navigation between `{#id}` and
 * `.ref {id}` stopped working in the real IDE for the currently-open file.
 *
 * A brand-new (unsaved) `.qd` file is backed by a [com.intellij.testFramework.LightVirtualFile],
 * which is NOT visible through [com.intellij.psi.search.FileTypeIndex]. Every resolution
 * path therefore used to silently skip the current buffer and report "no references" for
 * both directions. These tests assert that `{#id}` / `.ref {id}` navigation still works
 * entirely within an unsaved file.
 */
class QuarkdownUnsavedFileReproTest : BasePlatformTestCase() {

    private fun newUnsavedFile(name: String, text: String) {
        val psiFactory = PsiFileFactory.getInstance(project)
        val file = psiFactory.createFileFromText(name, QuarkdownLanguage.INSTANCE, text)
        myFixture.configureFromExistingVirtualFile(file.virtualFile!!)
    }

    fun `test ref resolves to label in same unsaved file`() {
        val text = "See .ref {same-file-target}.\n\n{#same-file-target}"
        newUnsavedFile("unsaved-a.qd", text)

        val refIdStart = text.indexOf("{same-file-target}") + 1
        val ref = myFixture.file.findReferenceAt(refIdStart + 3)
        assertNotNull("should find reference on .ref in unsaved file", ref)

        val target = ref!!.resolve()
        assertNotNull("ref should resolve to {#same-file-target} in the same unsaved file", target)
        assertTrue(
            "target should be at the label declaration",
            target!!.textOffset >= text.indexOf("{#same-file-target}")
        )
    }

    fun `test label multiResolve finds ref usages in same unsaved file`() {
        val text = "First .ref {usages-in-file}.\nSecond .ref {usages-in-file}.\n\n{#usages-in-file}"
        newUnsavedFile("unsaved-b.qd", text)

        val labelStart = text.indexOf("{#usages-in-file}") + 2
        val ref = myFixture.file.findReferenceAt(labelStart + 2)
        assertNotNull("should find reference on {#id} in unsaved file", ref)

        val poly = ref as com.intellij.psi.PsiPolyVariantReference
        val results = poly.multiResolve(false)
        assertEquals(
            "multiResolve should find both .ref usages in the same unsaved file",
            2, results.size
        )
    }

    fun `test goto declaration handler finds label in same unsaved file`() {
        val text = "See .ref {handler-in-file}.\n\n# Heading {#handler-in-file}"
        newUnsavedFile("unsaved-c.qd", text)

        val refIdStart = text.indexOf("{handler-in-file}") + 1
        val leaf = myFixture.file.findElementAt(refIdStart)
        assertNotNull("no leaf at ref id", leaf)

        val handlers = com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler.EP_NAME.extensionList
        val ourHandler = handlers.firstOrNull { it is QuarkdownGotoDeclarationHandler }
        assertNotNull("QuarkdownGotoDeclarationHandler must be registered", ourHandler)

        val targets = ourHandler!!.getGotoDeclarationTargets(leaf, refIdStart, myFixture.editor) ?: emptyArray()
        assertEquals("goto handler should find the label in the same unsaved file", 1, targets.size)
    }

    fun `test references search finds refs in same unsaved file`() {
        val text = "First .ref {search-in-file}.\n\n{#search-in-file}"
        newUnsavedFile("unsaved-d.qd", text)

        val labelStart = text.indexOf("{#search-in-file}") + 2
        val target = myFixture.file.findElementAt(labelStart)
        assertNotNull("no element at label", target)

        val refs = ReferencesSearch.search(target!!, GlobalSearchScope.projectScope(project)).findAll()
        assertTrue("ReferencesSearch should find the .ref in the same unsaved file", refs.isNotEmpty())
    }
}

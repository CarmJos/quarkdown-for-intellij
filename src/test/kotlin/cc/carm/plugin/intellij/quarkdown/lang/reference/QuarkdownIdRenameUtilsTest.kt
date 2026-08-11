package cc.carm.plugin.intellij.quarkdown.lang.reference

import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Verifies [QuarkdownIdRenameUtils] propagates an id rename performed through the element
 * edit dialogs to every `.ref {oldId}` usage across the project.
 */
class QuarkdownIdRenameUtilsTest : BasePlatformTestCase() {

    private fun commitAll() {
        PsiDocumentManager.getInstance(project).commitAllDocuments()
    }

    fun `test rename ref usages in the same file`() {
        val text = "See .ref {my-id} here.\nAnd .ref {my-id} there.\n\n{#my-id}"
        myFixture.configureByText("rename-util-same.qd", text)

        val count = QuarkdownIdRenameUtils.renameRefUsages(project, myFixture.file, "my-id", "new-id")
        commitAll()

        assertEquals("both usages should be renamed", 2, count)
        val newText = myFixture.editor.document.text
        assertTrue("first .ref should be renamed", newText.contains(".ref {new-id}"))
        assertTrue(
            "second .ref should be renamed",
            newText.indexOf(".ref {new-id}") >= 0 &&
                    newText.indexOf(".ref {new-id}", newText.indexOf(".ref {new-id}") + 1) > 0
        )
        // The declaration itself is written back by the dialog; the util only touches usages.
        assertTrue("declaration must stay untouched", newText.contains("{#my-id}"))
    }

    fun `test rename ref usages across files`() {
        val fileA = myFixture.addFileToProject("rename-a.qd", "First .ref {cross-id}.")
        val fileB = myFixture.addFileToProject("rename-b.qd", "Second .ref {cross-id}.\n\n{#cross-id}")
        commitAll()

        val count = QuarkdownIdRenameUtils.renameRefUsages(project, fileA, "cross-id", "cross-renamed")
        commitAll()

        assertEquals("both usages should be renamed", 2, count)
        assertTrue(fileA.text.contains(".ref {cross-renamed}"))
        assertTrue(fileB.text.contains(".ref {cross-renamed}"))
        // Declaration in file B is handled by the dialog write, not the util.
        assertTrue(fileB.text.contains("{#cross-id}"))
    }

    fun `test rename is case insensitive for old id`() {
        val text = "See .ref {My-Id}.\n\n{#my-id}"
        myFixture.configureByText("rename-util-case.qd", text)

        val count = QuarkdownIdRenameUtils.renameRefUsages(project, myFixture.file, "my-id", "new-id")
        commitAll()

        assertEquals("usage should be renamed regardless of case", 1, count)
        assertTrue(myFixture.editor.document.text.contains(".ref {new-id}"))
    }

    fun `test rename is a no-op when ids are equal`() {
        val text = "See .ref {same-id}.\n\n{#same-id}"
        myFixture.configureByText("rename-util-equal.qd", text)

        val count = QuarkdownIdRenameUtils.renameRefUsages(project, myFixture.file, "same-id", "same-id")
        commitAll()

        assertEquals(0, count)
        assertTrue(myFixture.editor.document.text.contains(".ref {same-id}"))
    }

    fun `test rename is a no-op when old id is blank`() {
        val text = "See .ref {my-id}.\n\n{#my-id}"
        myFixture.configureByText("rename-util-blank.qd", text)

        val count = QuarkdownIdRenameUtils.renameRefUsages(project, myFixture.file, "", "new-id")
        commitAll()

        assertEquals(0, count)
        assertTrue(myFixture.editor.document.text.contains(".ref {my-id}"))
    }

    fun `test rename skips non-matching ref ids`() {
        val text = "See .ref {keep-id}.\n\n{#other-id}"
        myFixture.configureByText("rename-util-nomatch.qd", text)

        val count = QuarkdownIdRenameUtils.renameRefUsages(project, myFixture.file, "other-id", "new-id")
        commitAll()

        assertEquals("no matching usage should be renamed", 0, count)
        assertTrue(myFixture.editor.document.text.contains(".ref {keep-id}"))
    }
}

package cc.carm.plugin.intellij.quarkdown.lang.reference

import com.intellij.codeInsight.navigation.actions.GotoDeclarationOrUsageHandler2
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Verifies the user-reported caret/usages behaviors:
 *  1. Ctrl+Click on `{#id}` with a SINGLE `.ref` usage must produce the platform's
 *     "Go To Declaration" outcome (GTD) so it navigates DIRECTLY — no usages window flash.
 *  2. Ctrl+Click on `{#id}` with MULTIPLE `.ref` usages must produce the platform's
 *     "Show Usages" outcome (SU), so the official native usages window opens anchored at
 *     the declaration and the caret does NOT jump to the first `.ref`.
 *  3. Ctrl+Click on a `{#id}` with NO usages must report ZERO usages (clean hint).
 */
class QuarkdownCaretBehaviorReproTest : BasePlatformTestCase() {

    private fun gotoHandler() = com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler.EP_NAME
        .extensionList.firstOrNull { it is QuarkdownGotoDeclarationHandler }!!

    fun `test single-ref label produces goto declaration outcome and returns the usage`() {
        val text = "See .ref {single-target}.\n\n# Heading {#single-target}"
        myFixture.configureByText("caret-single.qd", text)
        val labelStart = text.indexOf("{#single-target}") + 2
        val clickOffset = labelStart + 3

        val outcome = GotoDeclarationOrUsageHandler2
            .testGTDUOutcomeInNonBlockingReadAction(myFixture.editor, myFixture.file, clickOffset)
        println("single-ref GTDU outcome=$outcome")
        assertEquals("GTD", outcome.toString())

        // The handler must return the SINGLE .ref usage so the platform navigates directly.
        val leaf = myFixture.file.findElementAt(clickOffset)
        val targets = gotoHandler().getGotoDeclarationTargets(leaf, clickOffset, myFixture.editor) ?: emptyArray()
        println("single-ref handler targets=${targets.map { it.text + "@" + it.textOffset }}")
        assertEquals("handler should return the single usage", 1, targets.size)
        assertTrue(
            "target should be at the .ref usage",
            targets[0].textOffset < text.indexOf("{#single-target}")
        )
    }

    fun `test multi-ref label produces show usages outcome`() {
        val text = "First .ref {popup-target}.\nSecond .ref {popup-target}.\n\n{#popup-target}"
        myFixture.configureByText("caret-multi.qd", text)
        val labelStart = text.indexOf("{#popup-target}") + 2
        val clickOffset = labelStart + 4

        val outcome = GotoDeclarationOrUsageHandler2
            .testGTDUOutcomeInNonBlockingReadAction(myFixture.editor, myFixture.file, clickOffset)
        println("multi-ref GTDU outcome=$outcome")
        assertEquals("SU", outcome.toString())

        // The handler must return NO targets for a multi-usage declaration so the platform
        // falls back to the Symbol model and shows the native usages window.
        val leaf = myFixture.file.findElementAt(clickOffset)
        val targets = gotoHandler().getGotoDeclarationTargets(leaf, clickOffset, myFixture.editor) ?: emptyArray()
        println("multi-ref handler targets=${targets.size}")
        assertEquals("handler should return no targets for multi-usage", 0, targets.size)

        // The platform's Ctrl+Mouse data must expose the declaration as the hover/click target.
        val data = GotoDeclarationOrUsageHandler2
            .getCtrlMouseData(myFixture.editor, myFixture.file, clickOffset)
        println("multi-ref CtrlMouseData ranges=${data?.ranges} navigatable=${data?.isNavigatable}")
        assertNotNull("Ctrl+Mouse data should be available", data)

        // multiResolve still lists every .ref usage (used by rename/Find Usages).
        val ref = myFixture.file.findReferenceAt(clickOffset)
        val usages = (ref as? QuarkdownReference)?.multiResolve(false)?.mapNotNull { it.element } ?: emptyList()
        println("multi-ref usages=${usages.size}")
        assertTrue("should be >1 usages", usages.size > 1)

        // ReferencesSearch from the declaration must find the .ref usages. (The platform's
        // word-indexed searcher may additionally report the declaration itself, so we only
        // assert that both .ref usages are present.)
        val target = ref!!.resolve()
        assertNotNull("label should resolve to itself", target)
        val refs = ReferencesSearch.search(target!!, GlobalSearchScope.projectScope(project)).findAll()
        println("multi-ref ReferencesSearch=${refs.size}")
        assertTrue("ReferencesSearch should find both .ref usages", refs.size >= 2)
    }

    fun `test no-ref label reports zero usages`() {
        val text = "Just a declaration with no refs.\n\n{#lonely-target}"
        myFixture.configureByText("caret-noref.qd", text)
        val labelStart = text.indexOf("{#lonely-target}") + 2
        val clickOffset = labelStart + 3

        val ref = myFixture.file.findReferenceAt(clickOffset)
        assertNotNull("should find reference on label", ref)
        val target = ref!!.resolve()
        assertNotNull("label should resolve to itself", target)

        // The FindUsagesHandler must NOT report the declaration itself as a usage of itself,
        // so the platform shows a clean "no usages" hint instead of a flash + "only reference".
        val factory = QuarkdownFindUsagesHandlerFactory()
        assertTrue(factory.canFindUsages(target!!))
        val handler = factory.createFindUsagesHandler(target, false)
        val usages = mutableListOf<com.intellij.usageView.UsageInfo>()
        handler.processElementUsages(
            target, com.intellij.util.Processor { usages.add(it); true },
            com.intellij.find.findUsages.FindUsagesOptions(
                com.intellij.psi.search.GlobalSearchScope.projectScope(project)
            )
        )
        println("no-ref FindUsagesHandler usages=${usages.size}")
        assertTrue("a no-ref declaration must report zero usages", usages.isEmpty())
    }

    private fun underlineRanges(text: String, clickOffset: Int): List<String> {
        myFixture.configureByText("underline.qd", text)
        myFixture.editor.caretModel.moveToOffset(clickOffset)
        // The platform computes CtrlMouseData on a background coroutine thread (like
        // CtrlMouseHandler2 does), not on the EDT.
        val done = java.util.concurrent.CountDownLatch(1)
        val result = java.util.concurrent.atomic.AtomicReference<com.intellij.codeInsight.navigation.CtrlMouseData?>()
        val error = java.util.concurrent.atomic.AtomicReference<Throwable?>()
        com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread {
            try {
                result.set(
                    com.intellij.openapi.application.ApplicationManager.getApplication().runReadAction<com.intellij.codeInsight.navigation.CtrlMouseData?> {
                        com.intellij.codeInsight.navigation.actions.GotoDeclarationOrUsageHandler2
                            .getCtrlMouseData(myFixture.editor, myFixture.file, clickOffset)
                    }
                )
            } catch (t: Throwable) {
                error.set(t)
                t.printStackTrace()
            } finally {
                done.countDown()
            }
        }
        done.await(30, java.util.concurrent.TimeUnit.SECONDS)
        check(error.get() == null) { "CtrlMouseData failed: ${error.get()?.message}" }
        return result.get()?.ranges
            ?.map { myFixture.file.text.substring(it.startOffset, it.endOffset) }
            ?: emptyList()
    }

    fun `test ctrl hover underline covers only the id for single-ref label`() {
        val text = "See .ref {only-one}.\n\n# Heading {#only-one}"
        val labelStart = text.indexOf("{#only-one}") + 2
        val ranges = underlineRanges(text, labelStart + 3)
        println("single-ref underline ranges=$ranges")
        // The underline must cover ONLY the id, never the whole `{#id}` token.
        assertEquals(listOf("only-one"), ranges)
    }

    fun `test ctrl hover underline covers only the id for multi-ref label`() {
        val text = "A .ref {multi-x}.\nB .ref {multi-x}.\n\n{#multi-x}"
        val labelStart = text.indexOf("{#multi-x}") + 2
        val ranges = underlineRanges(text, labelStart + 3)
        println("multi-ref underline ranges=$ranges")
        assertEquals(listOf("multi-x"), ranges)
    }

    fun `test ctrl hover underline covers only the id for ref usage`() {
        val text = "See .ref {ref-underline}.\n\n# Heading {#ref-underline}"
        val refStart = text.indexOf("{ref-underline}") + 1
        val ranges = underlineRanges(text, refStart + 3)
        println("ref-usage underline ranges=$ranges")
        assertEquals(listOf("ref-underline"), ranges)
    }

    fun `test multi-ref label find usages handler reports only the refs`() {
        val text = "First .ref {count-target}.\nSecond .ref {count-target}.\n\n{#count-target}"
        myFixture.configureByText("caret-count.qd", text)
        val labelStart = text.indexOf("{#count-target}") + 2
        val target = myFixture.file.findElementAt(labelStart)
        assertNotNull("no element at label", target)

        val factory = QuarkdownFindUsagesHandlerFactory()
        assertTrue(factory.canFindUsages(target!!))
        val handler = factory.createFindUsagesHandler(target, false)
        val usages = mutableListOf<com.intellij.usageView.UsageInfo>()
        handler.processElementUsages(
            target, com.intellij.util.Processor { usages.add(it); true },
            com.intellij.find.findUsages.FindUsagesOptions(
                com.intellij.psi.search.GlobalSearchScope.projectScope(project)
            )
        )
        println("multi-ref FindUsagesHandler usages=${usages.size}")
        assertEquals("handler should report exactly the two .ref usages", 2, usages.size)
    }
}

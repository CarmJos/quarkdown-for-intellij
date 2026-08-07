package cc.carm.plugin.intellij.quarkdown.lang.reference

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Verifies that Ctrl+Click / Find Usages reference resolution works end-to-end
 * for `.var` usages and `.ref` usages in a real Quarkdown document.
 */
class ReferenceResolutionPlatformTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/resources"

    override fun setUp() {
        super.setUp()
        myFixture.configureByFile("reference-sample.qd")
    }

    private fun refsAt(offset: Int): Array<PsiReference> {
        val element = myFixture.file.findElementAt(offset)
        assertNotNull("no element at $offset", element)
        return ReferenceProvidersRegistry.getReferencesFromProviders(
            element!!, com.intellij.psi.PsiReferenceService.Hints.NO_HINTS
        )
    }

    @Suppress("DEPRECATION")
    private fun searchReferences(target: com.intellij.psi.PsiElement): Collection<PsiReference> {
        return com.intellij.psi.search.searches.ReferencesSearch.search(target).findAll()
    }

    fun `test var usage reference resolves`() {
        val text = myFixture.file.text
        val usageOffset = text.indexOf(".version")
        assertTrue("expected .version usage", usageOffset >= 0)

        val refs = refsAt(usageOffset + 1) // position of 'v' in .version
        assertTrue("expected a reference at .version, got ${refs.size}", refs.isNotEmpty())

        val varRef = refs.firstOrNull { it is QuarkdownReference }
        assertNotNull("expected a QuarkdownReference", varRef)

        val target = varRef!!.resolve()
        assertNotNull("var reference did not resolve", target)
        // the target should be inside the `.var {version}` declaration
        val varOffset = text.indexOf(".var")
        assertTrue(
            "resolved target should be at the .var declaration (${target!!.textOffset} vs $varOffset)",
            target.textOffset in (varOffset - 2)..(varOffset + 30)
        )
    }

    fun `test ctrl click on version usage resolves to var declaration`() {
        val text = myFixture.file.text
        val usageOffset = text.indexOf(".version")
        assertTrue("expected .version usage", usageOffset >= 0)

        // simulate Ctrl+Click on the middle of "version" (offset of 'r')
        val clickOffset = usageOffset + 3
        val elem = myFixture.file.findElementAt(clickOffset)
        System.out.println("clickOffset=$clickOffset elem='${elem?.text}' range=${elem?.textRange}")
        val allRefs = if (elem != null)
            ReferenceProvidersRegistry.getReferencesFromProviders(elem, com.intellij.psi.PsiReferenceService.Hints.NO_HINTS)
                .map { "${it.javaClass.simpleName}(${it.rangeInElement}) resolves=${it.resolve() != null}" }
                .toList()
        else emptyList()
        System.out.println("getReferencesFromProviders -> $allRefs")
        val ref = myFixture.file.findReferenceAt(clickOffset)
        System.out.println("findReferenceAt -> ${ref?.javaClass?.simpleName} range=${ref?.rangeInElement}")
        if (ref == null && elem != null) {
            val leafRef = elem.findReferenceAt(clickOffset - elem.textRange.startOffset)
            System.out.println("leaf.findReferenceAt(local) -> ${leafRef?.javaClass?.simpleName} range=${leafRef?.rangeInElement}")
            val serviceRefs = com.intellij.psi.PsiReferenceService.getService()
                .getReferences(elem, com.intellij.psi.PsiReferenceService.Hints.NO_HINTS)
                .map { "${it.javaClass.simpleName}(${it.rangeInElement})" }
                .toList()
            System.out.println("PsiReferenceService.getReferences -> $serviceRefs")
        }
        assertNotNull("findReferenceAt should find a reference on .version", ref)

        val target = ref!!.resolve()
        assertNotNull("reference should resolve", target)
        val varOffset = text.indexOf(".var")
        assertTrue(
            "target should be at .var declaration (${target!!.textOffset} vs $varOffset)",
            target.textOffset in (varOffset - 2)..(varOffset + 30)
        )
    }

    fun `test var declaration is navigable via find usages`() {
        val text = myFixture.file.text
        val varOffset = text.indexOf(".var")
        assertTrue(varOffset >= 0)
        // target = the `version` name inside `.var {version}`
        val nameOffset = text.indexOf("version", varOffset)
        val target = myFixture.file.findElementAt(nameOffset)
        assertNotNull("no element at var name", target)

        // ReferencesSearch must find the `.version` usages of the `.var {version}`.
        val refs = ReferencesSearch.search(target!!, GlobalSearchScope.projectScope(project)).findAll()
        assertTrue(
            "expected references to .var version, got ${refs.size}",
            refs.isNotEmpty()
        )
    }

    fun `test isReferenceTo for var usage`() {
        val text = myFixture.file.text
        val varOffset = text.indexOf(".var")
        val nameOffset = text.indexOf("version", varOffset)
        val target = myFixture.file.findElementAt(nameOffset)
        assertNotNull(target)

        val usageOffset = text.indexOf(".version")
        val refs = refsAt(usageOffset + 1)
        val varRef = refs.firstOrNull { it is QuarkdownReference }!!
        assertTrue(
            "isReferenceTo should be true for the .var declaration target",
            varRef.isReferenceTo(target!!)
        )
    }

    fun `test hyphenated ref id keeps the whole id as one reference`() {
        val text = "See .ref {button-start-action}.\n\n{#button-start-action}"
        myFixture.configureByText("a.qd", text)

        // find the full range of the id in `.ref {button-start-action}`
        val refIdStart = text.indexOf("{button-start-action}") + 1
        // the id spans 19 chars
        val refIdRange = refIdStart until (refIdStart + "button-start-action".length)

        // findReferenceAt on the LAST leaf ("action") must return the full-range reference
        val clickOffset = refIdRange.last - 1
        val ref = myFixture.file.findReferenceAt(clickOffset)
        assertNotNull("findReferenceAt on the tail of a hyphenated id", ref)
        // the reference range should cover the whole id (the widest match wins)
        assertEquals("button-start-action", ref!!.canonicalText)

        // the reference must resolve to the `{#button-start-action}` declaration
        val target = ref.resolve()
        assertNotNull("hyphenated ref should resolve", target)
        assertTrue(
            "target should be at the {#button-start-action} declaration",
            target!!.textOffset >= text.indexOf("{#button-start-action}")
        )
    }

    fun `test clicking the label declaration navigates back to a ref usage`() {
        val text = "See .ref {chapter-1}.\n\n# Heading {#chapter-1}"
        myFixture.configureByText("b.qd", text)

        val labelStart = text.indexOf("{#chapter-1}")
        // click on the label id (middle)
        val ref = myFixture.file.findReferenceAt(labelStart + 3)
        assertNotNull("findReferenceAt on the label should find a back-reference", ref)
        assertEquals("chapter-1", ref!!.canonicalText)

        val target = ref.resolve()
        assertNotNull("label reference should resolve to a .ref usage", target)
        assertTrue(
            "target should be at the .ref usage",
            target!!.textOffset <= text.indexOf(".ref") + 20
        )
    }

    fun `test find usages works on any part of a hyphenated label id`() {
        val text = "See .ref {button-start-action}.\n\n{#button-start-action}"
        myFixture.configureByText("c.qd", text)

        // target = the LAST leaf ("action") of the label declaration
        val labelStart = text.indexOf("{#button-start-action}") + 2
        val labelIdEnd = labelStart + "button-start-action".length
        val target = myFixture.file.findElementAt(labelIdEnd - 2)
        assertNotNull("no element at tail of label id", target)

        // reference from the .ref usage must point to this declaration
        val ref = myFixture.file.findReferenceAt(text.indexOf("{button-start-action}") + 8)
        assertNotNull(ref)
        assertTrue(
            "isReferenceTo should match any leaf of the hyphenated label id",
            ref!!.isReferenceTo(target!!)
        )
    }

    fun `test find usages lists all refs to a label`() {
        val text = "First .ref {mybutton} here.\nSecond .ref {mybutton} there.\n\n{#mybutton}"
        val file = myFixture.addFileToProject("multi.qd", text)

        // target = the label declaration id leaf
        val labelStart = text.indexOf("{#mybutton}") + 2
        val target = file.findElementAt(labelStart)
        assertNotNull("no element at label declaration", target)

        // ReferencesSearch must find BOTH `.ref {mybutton}` usages.
        val refs = ReferencesSearch.search(target!!, GlobalSearchScope.projectScope(project)).findAll()
        System.out.println("find-usages for multi-ref label -> ${refs.size}: " +
            refs.joinToString { "at ${it.element.textOffset}" })
        assertTrue("expected both .ref usages, got ${refs.size}", refs.size >= 2)
    }

    fun `test find usages on hyphenated label finds all usages`() {
        val text = "One .ref {button-start-action} here.\nTwo .ref {button-start-action} there.\n\n{#button-start-action}"
        val file = myFixture.addFileToProject("multi-hyphen.qd", text)

        // target = a leaf of the label declaration (e.g. "action")
        val labelStart = text.indexOf("{#button-start-action}") + 2
        val target = file.findElementAt(labelStart + "button-start".length)
        assertNotNull("no element at label id tail", target)

        val refs = ReferencesSearch.search(target!!, GlobalSearchScope.projectScope(project)).findAll()
        System.out.println("find-usages for hyphenated label -> ${refs.size}: " +
            refs.joinToString { "at ${it.element.textOffset}" })
        assertTrue("expected both hyphenated .ref usages, got ${refs.size}", refs.size >= 2)
    }

    fun `test references search ep has executors`() {
        val executors = com.intellij.psi.search.searches.ReferencesSearch.EP_NAME.extensionList
        assertTrue("ReferencesSearch should have at least one executor", executors.isNotEmpty())
        // Our custom searcher must be among them.
        assertTrue(
            "QuarkdownReferencesSearcher must be registered",
            executors.any { it.javaClass.name.contains("QuarkdownReferencesSearcher") }
        )
    }

    fun `test each ref usage resolves to the same label`() {
        val text = "First .ref {mybutton} here.\nSecond .ref {mybutton} there.\n\n{#mybutton}"
        myFixture.configureByText("multi2.qd", text)

        val firstRefStart = text.indexOf("mybutton") // inside first .ref
        val secondRefStart = text.indexOf("mybutton", firstRefStart + 1) // inside second .ref
        val labelStart = text.indexOf("{#mybutton}")

        val ref1 = myFixture.file.findReferenceAt(firstRefStart + 2)
        val ref2 = myFixture.file.findReferenceAt(secondRefStart + 2)
        assertNotNull("first ref not found", ref1)
        assertNotNull("second ref not found", ref2)

        val target1 = ref1!!.resolve()
        val target2 = ref2!!.resolve()
        assertNotNull("first ref did not resolve", target1)
        assertNotNull("second ref did not resolve", target2)

        // both resolve to the SAME label declaration
        assertTrue(target1!!.textOffset >= labelStart)
        assertTrue(target2!!.textOffset >= labelStart)
    }
}

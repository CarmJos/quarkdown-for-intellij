package cc.carm.plugin.intellij.quarkdown.lang.reference

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
        return ReferencesSearch.search(target).findAll()
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
            ReferenceProvidersRegistry.getReferencesFromProviders(
                elem,
                com.intellij.psi.PsiReferenceService.Hints.NO_HINTS
            )
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
        System.out.println(
            "find-usages for multi-ref label -> ${refs.size}: " +
                    refs.joinToString { "at ${it.element.textOffset}" })
        assertTrue("expected both .ref usages, got ${refs.size}", refs.size >= 2)
    }

    fun `test find usages on hyphenated label finds all usages`() {
        val text =
            "One .ref {button-start-action} here.\nTwo .ref {button-start-action} there.\n\n{#button-start-action}"
        val file = myFixture.addFileToProject("multi-hyphen.qd", text)

        // target = a leaf of the label declaration (e.g. "action")
        val labelStart = text.indexOf("{#button-start-action}") + 2
        val target = file.findElementAt(labelStart + "button-start".length)
        assertNotNull("no element at label id tail", target)

        val refs = ReferencesSearch.search(target!!, GlobalSearchScope.projectScope(project)).findAll()
        System.out.println(
            "find-usages for hyphenated label -> ${refs.size}: " +
                    refs.joinToString { "at ${it.element.textOffset}" })
        assertTrue("expected both hyphenated .ref usages, got ${refs.size}", refs.size >= 2)
    }

    // ---- Regression tests for issues reported by users ----

    fun `test plain text has no references`() {
        val text = "Just some ordinary text with no ids at all.\n\nMore plain words here."
        myFixture.configureByText("plain.qd", text)

        // No position should have a navigable reference.
        for (i in 0 until text.length) {
            if (text[i] == '\n') continue
            val ref = myFixture.file.findReferenceAt(i)
            assertNull("plain text at $i ('${text[i]}') should not have a reference", ref)
        }
    }

    fun `test hyphenated ref id is one navigable unit`() {
        val text = "See .ref {plc-symbol-table-output}.\n\n{#plc-symbol-table-output}"
        myFixture.configureByText("hyphen.qd", text)

        // Every character inside the ref id must yield a reference with the full id text.
        val idStart = text.indexOf("{plc-symbol-table-output}") + 1
        val idEnd = idStart + "plc-symbol-table-output".length
        for (i in idStart until idEnd) {
            val ref = myFixture.file.findReferenceAt(i)
            assertNotNull("no reference at offset $i", ref)
            assertEquals("plc-symbol-table-output", ref!!.canonicalText)
        }

        // Plain text between refs must not be navigable.
        val plainStart = text.indexOf("See")
        assertNull(myFixture.file.findReferenceAt(plainStart + 1))
    }

    fun `test label multiResolve returns all ref usages`() {
        val text = "First .ref {multi-label}.\nSecond .ref {multi-label}.\n\n{#multi-label}"
        myFixture.configureByText("multi-resolve.qd", text)

        // Find the reference at the label declaration {#multi-label}.
        val labelStart = text.indexOf("{#multi-label}") + 2
        val ref = myFixture.file.findReferenceAt(labelStart)
        assertNotNull("should find reference at label declaration", ref)

        // multiResolve must return BOTH .ref usages (Ctrl+Click popup support).
        val poly = ref as? com.intellij.psi.PsiPolyVariantReference
        assertNotNull("reference should be poly variant", poly)
        val results = poly!!.multiResolve(false)
        assertEquals(
            "multiResolve should return both .ref usages, got ${results.size}",
            2, results.size
        )
    }

    fun `test find usages from ref usage finds label and other refs`() {
        val text = "First .ref {shared-target}.\nSecond .ref {shared-target}.\n\n{#shared-target}"
        val file = myFixture.addFileToProject("reverse.qd", text)

        // Target = the id inside the FIRST .ref usage.
        val refIdStart = text.indexOf("{shared-target}") + 1
        val target = file.findElementAt(refIdStart)
        assertNotNull("no element at ref id", target)

        // ReferencesSearch must find the label declaration AND both .ref usages.
        val refs = ReferencesSearch.search(target!!, GlobalSearchScope.projectScope(project)).findAll()
        assertTrue("expected label + refs, got ${refs.size}", refs.size >= 3)
    }

    fun `test handleElementRename rewrites current element`() {
        val text = "See .ref {rename-me}.\n\n{#rename-me}"
        myFixture.configureByText("rename-rewrite.qd", text)

        val refStart = text.indexOf("{rename-me}") + 1
        val ref = myFixture.file.findReferenceAt(refStart)
        assertNotNull("should find reference", ref)

        ref!!.handleElementRename("renamed-id")

        val newText = myFixture.file.text
        assertTrue("first .ref should be renamed", newText.contains(".ref {renamed-id}"))
        assertFalse(
            "old id should be gone in first ref",
            newText.substring(0, newText.indexOf("\n\n")).contains("rename-me")
        )
    }

    // ---- Diagnostics: verify bare-id naming used by Find Usages / Rename ----

    fun `test leaf name returns bare id without braces`() {
        val text = "See .ref {plc-symbol-output}.\n\n{#plc-symbol-output}"
        myFixture.configureByText("bare-name.qd", text)

        // Leaf at the .ref id (FUNCTION_PARAMS -> QuarkdownIdLeafPsiElement)
        val refIdStart = text.indexOf("{plc-symbol-output}") + 1
        val refLeaf = myFixture.file.findElementAt(refIdStart)
        assertNotNull("no leaf at ref id", refLeaf)
        System.out.println(
            "refLeaf class=${refLeaf!!.javaClass.simpleName} text='${refLeaf.text}' " +
                    "name=${(refLeaf as? com.intellij.psi.PsiNamedElement)?.name}"
        )

        // Leaf at the {#id} label (ID_TAG -> QuarkdownIdLeafPsiElement)
        val labelStart = text.indexOf("{#plc-symbol-output}") + 2
        val labelLeaf = myFixture.file.findElementAt(labelStart)
        assertNotNull("no leaf at label id", labelLeaf)
        System.out.println(
            "labelLeaf class=${labelLeaf!!.javaClass.simpleName} text='${labelLeaf.text}' " +
                    "name=${(labelLeaf as? com.intellij.psi.PsiNamedElement)?.name}"
        )

        // Id leaves must be PsiNamedElement (Symbol model) with the bare id as name.
        assertTrue("ref leaf must be PsiNamedElement", refLeaf is com.intellij.psi.PsiNamedElement)
        assertTrue("label leaf must be PsiNamedElement", labelLeaf is com.intellij.psi.PsiNamedElement)
        assertEquals("plc-symbol-output", (refLeaf as com.intellij.psi.PsiNamedElement).name)
        assertEquals("plc-symbol-output", (labelLeaf as com.intellij.psi.PsiNamedElement).name)

        // Ordinary prose leaves must NOT be PsiNamedElement (no ctrl+click underline).
        val proseLeaf = myFixture.file.findElementAt(text.indexOf("See") + 1)
        assertNotNull("no prose leaf", proseLeaf)
        assertFalse(
            "plain prose leaves must not implement PsiNamedElement",
            proseLeaf is com.intellij.psi.PsiNamedElement
        )
    }

    fun `test rename target element used by rename machinery`() {
        val text = "See .ref {plc-symbol-output}.\n\n{#plc-symbol-output}"
        myFixture.configureByText("rename-target.qd", text)

        val idStart = text.indexOf("{plc-symbol-output}") + 1
        myFixture.editor.caretModel.moveToOffset(idStart + 3) // caret inside the id

        val flags = com.intellij.codeInsight.TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED or
                com.intellij.codeInsight.TargetElementUtil.ELEMENT_NAME_ACCEPTED
        val target = com.intellij.codeInsight.TargetElementUtil.findTargetElement(myFixture.editor, flags)
        System.out.println(
            "rename target=$target class=${target?.javaClass?.simpleName} " +
                    "text='${target?.text}'"
        )

        assertNotNull("TargetElementUtil should find a rename target", target)
        // The resolved target should be at the {#id} declaration.
        assertTrue(
            "target should be at the {#plc-symbol-output} declaration",
            target!!.textOffset >= text.indexOf("{#plc-symbol-output}")
        )
    }

    fun `test rename via processor renames all references`() {
        val text = "First .ref {proc-id}.\nSecond .ref {proc-id}.\n\n{#proc-id}"
        myFixture.configureByText("proc-rename.qd", text)
        val file = myFixture.file

        val labelStart = text.indexOf("{#proc-id}") + 2
        val target = file.findElementAt(labelStart)
        assertNotNull("no element at label declaration", target)

        val processor = com.intellij.refactoring.rename.RenamePsiElementProcessor.forElement(target!!)
        assertNotNull("should find rename processor", processor)
        assertTrue("processor should handle Quarkdown element", processor.canProcessElement(target))
        assertFalse("in-place rename should be disabled", processor.isInplaceRenameSupported)

        val refs = ReferencesSearch.search(target, GlobalSearchScope.projectScope(project)).findAll()
        val usages = refs.map { com.intellij.usageView.UsageInfo(it) }.toTypedArray()

        // renameElement wraps itself in a write command action (like handleElementRename).
        processor.renameElement(target, "renamed-id", usages, null)
        com.intellij.psi.PsiDocumentManager.getInstance(project).commitAllDocuments()

        val newText = file.viewProvider.document?.text ?: file.text
        assertTrue("first .ref should be renamed", newText.contains(".ref {renamed-id}"))
        assertTrue(
            "second .ref should be renamed", newText.indexOf(".ref {renamed-id}") >= 0 &&
                    newText.indexOf(".ref {renamed-id}", newText.indexOf(".ref {renamed-id}") + 1) > 0
        )
        assertTrue("label should be renamed", newText.contains("{#renamed-id}"))
        assertFalse("old id should not remain", newText.contains("proc-id"))
    }

    fun `test rename via processor works across files`() {
        val file1Text = "First .ref {cross-id}."
        val file2Text = "Second .ref {cross-id}.\n\n{#cross-id}"
        val file1 = myFixture.addFileToProject("cross-a.qd", file1Text)
        val file2 = myFixture.addFileToProject("cross-b.qd", file2Text)
        com.intellij.psi.PsiDocumentManager.getInstance(project).commitAllDocuments()

        val labelStart = file2Text.indexOf("{#cross-id}") + 2
        val target = file2.findElementAt(labelStart)
        assertNotNull("no element at label in file2", target)

        val processor = com.intellij.refactoring.rename.RenamePsiElementProcessor.forElement(target!!)
        assertNotNull("should find rename processor", processor)
        assertTrue(processor.canProcessElement(target))

        val refs = ReferencesSearch.search(target, GlobalSearchScope.projectScope(project)).findAll()
        val usages = refs.map { com.intellij.usageView.UsageInfo(it) }.toTypedArray()

        // Re-fetch the (possibly re-parsed) element before renaming.
        val freshTarget = file2.findElementAt(labelStart)
        assertNotNull("should still find label element", freshTarget)
        processor.renameElement(freshTarget!!, "cross-renamed", usages, null)
        com.intellij.psi.PsiDocumentManager.getInstance(project).commitAllDocuments()

        val newTextA = file1.viewProvider.document?.text ?: file1.text
        val newTextB = file2.viewProvider.document?.text ?: file2.text
        assertTrue("file A ref should be renamed", newTextA.contains(".ref {cross-renamed}"))
        assertTrue("file B ref should be renamed", newTextB.contains(".ref {cross-renamed}"))
        assertTrue("file B label should be renamed", newTextB.contains("{#cross-renamed}"))
    }

    fun `test references search ep has executors`() {
        val executors = ReferencesSearch.EP_NAME.extensionList
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

    // ---- Rename refactoring tests ----

    fun `test handleElementRename returns element`() {
        val text = "See .ref {chapter1}.\n\n{#chapter1}"
        myFixture.configureByText("rename-test.qd", text)

        // Find the reference at .ref {chapter1}
        val refStart = text.indexOf("{chapter1}") + 1 // start of "chapter1" in .ref
        val ref = myFixture.file.findReferenceAt(refStart)
        assertNotNull("should find reference at ref usage", ref)

        // Call handleElementRename - it should return an element without throwing
        val result = ref!!.handleElementRename("chapter2")
        assertNotNull("handleElementRename should return an element", result)
    }

    fun `test isReferenceTo enables find usages for rename`() {
        val text = "See .ref {myid}.\n\n{#myid}"
        myFixture.configureByText("find-usages-test.qd", text)

        // The label declaration
        val labelStart = text.indexOf("{#myid}") + 2
        val labelElement = myFixture.file.findElementAt(labelStart)
        assertNotNull("should find label element", labelElement)

        // The ref usage
        val refStart = text.indexOf(".ref {myid}") + 6 // inside the ref
        val ref = myFixture.file.findReferenceAt(refStart)
        assertNotNull("should find reference", ref)

        // isReferenceTo should return true, enabling Find Usages
        assertTrue("ref should be reference to label", ref!!.isReferenceTo(labelElement!!))
    }
}

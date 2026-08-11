package cc.carm.plugin.intellij.quarkdown.lang.reference

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Diagnostics for Ctrl+Click behavior using the same entry points the real IDE uses:
 * `TargetElementUtil.findTargetElement` (hover underline + navigation) and
 * `findReferenceAt` + `PsiPolyVariantReference.multiResolve` (usage popup).
 */
class QuarkdownCtrlClickDiagnosticTest : BasePlatformTestCase() {

    private fun targetAt(text: String, offset: Int, flags: Int): com.intellij.psi.PsiElement? {
        myFixture.editor.caretModel.moveToOffset(offset)
        return TargetElementUtil.findTargetElement(myFixture.editor, flags)
    }

    fun `test plain text has no ctrl click target`() {
        val text = "Just ordinary prose with no ids at all.\n\nMore plain words here."
        myFixture.configureByText("hover-plain.qd", text)

        val flags = TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED
        for (i in text.indices) {
            if (text[i] == '\n') continue
            val t = targetAt(text, i, flags)
            if (t != null) {
                System.out.println("plain offset $i '${text[i]}' target=${t.javaClass.simpleName} text='${t.text}'")
            }
            assertNull("plain text at offset $i should NOT be a ctrl+click target", t)
        }
    }

    fun `test plain text is not a name target either`() {
        val text = "Prose text here."
        myFixture.configureByText("hover-name.qd", text)

        val flags = TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED or TargetElementUtil.ELEMENT_NAME_ACCEPTED
        for (i in text.indices) {
            if (text[i] == ' ') continue
            val t = targetAt(text, i, flags)
            if (t != null) {
                System.out.println("name-offset $i '${text[i]}' target=${t.javaClass.simpleName} text='${t.text}'")
            }
            assertNull("plain text at offset $i should NOT be a name/ctrl-click target", t)
        }
    }

    fun `test ref id has single ctrl click target (the declaration)`() {
        val text = "See .ref {go-target}.\n\n{#go-target}"
        myFixture.configureByText("hover-ref.qd", text)

        val refIdStart = text.indexOf("{go-target}") + 1
        val flags = TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED
        val t = targetAt(text, refIdStart + 3, flags)
        System.out.println("ref target=$t text='${t?.text}'")
        assertNotNull("ref id should have a ctrl+click target", t)
    }

    fun `test label ctrl click is poly variant with multiple targets`() {
        val text = "First .ref {popup-id}.\nSecond .ref {popup-id}.\n\n{#popup-id}"
        myFixture.configureByText("hover-label.qd", text)

        val labelStart = text.indexOf("{#popup-id}") + 2
        myFixture.editor.caretModel.moveToOffset(labelStart + 2)

        val ref = myFixture.file.findReferenceAt(labelStart + 2)
        assertNotNull("findReferenceAt should find a reference on the label", ref)
        System.out.println("label ref=${ref!!.javaClass.simpleName}")

        val poly = ref as? PsiPolyVariantReference
        assertNotNull("label reference must be poly variant for the usage popup", poly)

        val results = poly!!.multiResolve(false)
        System.out.println("label multiResolve returned ${results.size} targets")
        for (r in results) {
            System.out.println("  target=${r.element?.javaClass?.simpleName} text='${r.element?.text}'")
        }
        assertTrue("multiResolve must return BOTH .ref usages for the popup", results.size >= 2)
    }

    fun `test ctrl click target on label via TargetElementUtil`() {
        val text = "First .ref {popup-id}.\nSecond .ref {popup-id}.\n\n{#popup-id}"
        myFixture.configureByText("hover-target.qd", text)

        val labelStart = text.indexOf("{#popup-id}") + 2
        val flags = TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED
        val t = targetAt(text, labelStart + 2, flags)
        System.out.println("label ctrl-click target=$t text='${t?.text}'")
        assertNotNull("label should have a ctrl+click target", t)
    }

    fun `test goto declaration handler returns single label for ref usage`() {
        val text = "See .ref {go-decl}.\n\n# Heading {#go-decl}"
        myFixture.configureByText("goto-handler-ref.qd", text)

        val refIdStart = text.indexOf("{go-decl}") + 1
        val leaf = myFixture.file.findElementAt(refIdStart)
        assertNotNull("no leaf at ref id", leaf)

        val handlers = com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler.EP_NAME.extensionList
        val ourHandler = handlers.firstOrNull { it is QuarkdownGotoDeclarationHandler }
        assertNotNull("QuarkdownGotoDeclarationHandler must be registered", ourHandler)

        val targets = ourHandler!!.getGotoDeclarationTargets(leaf, refIdStart, myFixture.editor) ?: emptyArray()
        assertEquals("goto handler should return the single label declaration", 1, targets.size)
        assertTrue(
            "target should be the {#go-decl} declaration",
            targets[0].textOffset >= text.indexOf("{#go-decl}")
        )
    }

    fun `test find usages handler reports all usages with leaf positions`() {
        val text = "First .ref {handler-test}.\nSecond .ref {handler-test}.\n\n{#handler-test}"
        myFixture.configureByText("handler-test.qd", text)

        val labelStart = text.indexOf("{#handler-test}") + 2
        val target = myFixture.file.findElementAt(labelStart)
        assertNotNull("no leaf at label", target)

        val factory = QuarkdownFindUsagesHandlerFactory()
        assertTrue("factory should handle the label element", factory.canFindUsages(target!!))

        val handler = factory.createFindUsagesHandler(target, false)
        assertNotNull("handler should be created", handler)
        assertTrue(
            "handler should be QuarkdownFindUsagesHandler",
            handler is QuarkdownFindUsagesHandler
        )

        val usages = mutableListOf<com.intellij.usageView.UsageInfo>()
        val options = com.intellij.find.findUsages.FindUsagesOptions(
            com.intellij.psi.search.GlobalSearchScope.projectScope(project)
        )
        val processed = handler.processElementUsages(target, com.intellij.util.Processor { usage ->
            usages.add(usage)
            true
        }, options)
        assertTrue("processElementUsages should succeed", processed)

        System.out.println("handler reported ${usages.size} usages")
        for (u in usages) {
            System.out.println("  usage at ${u.element?.textOffset} text='${u.element?.text}' range=${u.rangeInElement}")
        }
        // The declaration itself is not a usage of itself — only the two .ref usages.
        assertEquals(
            "handler should report exactly the two .ref usages",
            2, usages.size
        )
    }

    // ---- Ctrl+Click behavior ----

    fun `test symbol path outcome for label declaration`() {
        val text = "First .ref {sym-label}.\nSecond .ref {sym-label}.\n\n{#sym-label}"
        myFixture.configureByText("sym-label.qd", text)

        val labelStart = text.indexOf("{#sym-label}") + 2
        val labelLeaf = myFixture.file.findElementAt(labelStart)
        assertNotNull("no leaf at label", labelLeaf)
        assertTrue("label leaf must be PsiNamedElement", labelLeaf is com.intellij.psi.PsiNamedElement)

        // Platform outcome at the declaration when the GTD handler returns EMPTY.
        val outcome = com.intellij.codeInsight.navigation.actions.GotoDeclarationOrUsageHandler2
            .testGTDUOutcomeInNonBlockingReadAction(myFixture.editor, myFixture.file, labelStart + 2)
        System.out.println("symbol-path outcome at label=$outcome")
    }

    fun `test symbol path outcome for ref usage`() {
        val text = "See .ref {sym-ref}.\n\n{#sym-ref}"
        myFixture.configureByText("sym-ref.qd", text)

        val refIdStart = text.indexOf("{sym-ref}") + 1
        val refLeaf = myFixture.file.findElementAt(refIdStart)
        assertNotNull("no leaf at ref", refLeaf)
        // `.ref {id}` is a REFERENCE, not a declaration — it must not expose a bare name.
        assertFalse(
            "ref leaf must not be a named declaration",
            refLeaf is com.intellij.psi.PsiNamedElement && (refLeaf as com.intellij.psi.PsiNamedElement).name != null
        )

        val outcome = com.intellij.codeInsight.navigation.actions.GotoDeclarationOrUsageHandler2
            .testGTDUOutcomeInNonBlockingReadAction(myFixture.editor, myFixture.file, refIdStart + 2)
        System.out.println("symbol-path outcome at ref=$outcome")
    }

    fun `test label declaration returns no goto targets (Symbol model handles it)`() {
        val text = "First .ref {java-style}.\nSecond .ref {java-style}.\n\n{#java-style}"
        myFixture.configureByText("su-label.qd", text)

        // The id leaf inside {#java-style} must be a PsiNamedElement (QuarkdownIdLeafPsiElement).
        val labelStart = text.indexOf("{#java-style}") + 2
        val labelLeaf = myFixture.file.findElementAt(labelStart)
        assertNotNull("no leaf at label id", labelLeaf)
        assertTrue(
            "label id leaf must be PsiNamedElement",
            labelLeaf is com.intellij.psi.PsiNamedElement
        )
        assertEquals("name should be the bare id", "java-style", (labelLeaf as com.intellij.psi.PsiNamedElement).name)

        // The handler returns EMPTY for declarations so the platform falls back to the
        // Symbol model, which produces a Show Usages (SU) result — no "Choose Declaration".
        val handlers = com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler.EP_NAME.extensionList
        val ourHandler = handlers.firstOrNull { it is QuarkdownGotoDeclarationHandler }
        assertNotNull("QuarkdownGotoDeclarationHandler must be registered", ourHandler)
        val targets = ourHandler!!.getGotoDeclarationTargets(labelLeaf, labelStart, myFixture.editor) ?: emptyArray()
        assertTrue("handler should return no targets for label declaration, got ${targets.size}", targets.isEmpty())

        // The PsiNameIdentifierOwner ensures the name identifier covers only the id.
        assertTrue("label leaf must be PsiNameIdentifierOwner", labelLeaf is com.intellij.psi.PsiNameIdentifierOwner)
        val nameId = (labelLeaf as com.intellij.psi.PsiNameIdentifierOwner).nameIdentifier
        assertNotNull("name identifier must not be null", nameId)
        // The name identifier's text should be the bare id (no braces, no #).
        assertEquals("name identifier text should be the bare id", "java-style", nameId!!.text)
        // The name identifier's range should be within the leaf's range.
        val leafRange = labelLeaf.textRange
        val nameRange = nameId.textRange
        assertTrue(
            "name identifier range ${nameRange} must be within leaf range ${leafRange}",
            leafRange.contains(nameRange)
        )
        // The name identifier should NOT cover the braces or #.
        assertFalse("name identifier must not include '{'", nameId.text.contains('{'))
        assertFalse("name identifier must not include '}'", nameId.text.contains('}'))
    }

    fun `test hover on label returns no goto targets (background thread)`() {
        val text = "First .ref {hover-no-popup}.\nSecond .ref {hover-no-popup}.\n\n{#hover-no-popup}"
        myFixture.configureByText("hover-label.qd", text)

        val labelStart = text.indexOf("{#hover-no-popup}") + 2
        val labelLeaf = myFixture.file.findElementAt(labelStart)
        assertNotNull("no leaf at label", labelLeaf)

        val handlers = com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler.EP_NAME.extensionList
        val ourHandler = handlers.firstOrNull { it is QuarkdownGotoDeclarationHandler }
        assertNotNull("QuarkdownGotoDeclarationHandler must be registered", ourHandler)
        val qdHandler = ourHandler as QuarkdownGotoDeclarationHandler

        // Simulate Ctrl+hover: the handler runs on a background pooled thread (NOT the EDT).
        // It must return NO targets (the Symbol model handles declarations via SU).
        val results = java.util.concurrent.CopyOnWriteArrayList<com.intellij.psi.PsiElement>()
        val errors = java.util.concurrent.CopyOnWriteArrayList<Throwable>()
        val done = java.util.concurrent.CountDownLatch(1)
        com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val targets = qdHandler.getGotoDeclarationTargets(labelLeaf!!, labelStart, myFixture.editor)
                results.addAll(targets)
            } catch (t: Throwable) {
                errors.add(t)
                t.printStackTrace()
            } finally {
                done.countDown()
            }
        }

        assertTrue("hover computation should finish", done.await(30, java.util.concurrent.TimeUnit.SECONDS))
        assertTrue("no exceptions on hover, got ${errors.map { it.message }}", errors.isEmpty())
        assertTrue("hover should return no targets for declarations, got ${results.size}", results.isEmpty())
    }

    fun `test ref usage routes to single declaration`() {
        val text = "See .ref {goto-style}.\n\n{#goto-style}"
        myFixture.configureByText("gtd-ref.qd", text)

        val refIdStart = text.indexOf("{goto-style}") + 1
        val refLeaf = myFixture.file.findElementAt(refIdStart)
        assertNotNull("no leaf at ref id", refLeaf)

        val handlers = com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler.EP_NAME.extensionList
        val ourHandler = handlers.firstOrNull { it is QuarkdownGotoDeclarationHandler }
        assertNotNull("QuarkdownGotoDeclarationHandler must be registered", ourHandler)
        val targets = ourHandler!!.getGotoDeclarationTargets(refLeaf, refIdStart, myFixture.editor) ?: emptyArray()
        assertEquals("ref usage should return a single declaration", 1, targets.size)
        assertTrue(
            "target should be the {#goto-style} declaration",
            targets[0].textOffset >= text.indexOf("{#goto-style}")
        )
    }

    fun `test plain text has no navigation outcome`() {
        val text = "Just ordinary prose with no ids."
        myFixture.configureByText("plain-outcome.qd", text)

        val handlers = com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler.EP_NAME.extensionList
        val ourHandler = handlers.firstOrNull { it is QuarkdownGotoDeclarationHandler }
        assertNotNull("QuarkdownGotoDeclarationHandler must be registered", ourHandler)
        val targets = ourHandler!!.getGotoDeclarationTargets(myFixture.file.findElementAt(5), 5, myFixture.editor)
        assertTrue("plain text should return no targets", targets.isNullOrEmpty())
    }

    fun `test single-usage var declaration navigates directly to the usage`() {
        val text = ".var {version} {v12}\n.include {.version/file.qd}"
        myFixture.configureByText("su-var.qd", text)

        val varNameStart = text.indexOf("{version}") + 1
        val varLeaf = myFixture.file.findElementAt(varNameStart)
        assertNotNull("no leaf at var name", varLeaf)
        assertTrue("var declaration leaf must be PsiNamedElement", varLeaf is com.intellij.psi.PsiNamedElement)

        val handlers = com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler.EP_NAME.extensionList
        val ourHandler = handlers.firstOrNull { it is QuarkdownGotoDeclarationHandler }
        assertNotNull("QuarkdownGotoDeclarationHandler must be registered", ourHandler)
        // A `.var {version}` with EXACTLY ONE `.version` usage returns that usage so the
        // platform navigates directly (GTD) — no usages window, no flash.
        val targets = ourHandler!!.getGotoDeclarationTargets(varLeaf, varNameStart, myFixture.editor) ?: emptyArray()
        assertEquals("var declaration with one usage should return it", 1, targets.size)
        assertTrue(
            "target should be at the .version usage",
            targets[0].textOffset > text.indexOf(".var")
        )

        // The PsiNameIdentifierOwner ensures the name identifier covers only the name.
        assertTrue("var leaf must be PsiNameIdentifierOwner", varLeaf is com.intellij.psi.PsiNameIdentifierOwner)
        val nameId = (varLeaf as com.intellij.psi.PsiNameIdentifierOwner).nameIdentifier
        assertNotNull("name identifier must not be null", nameId)
        assertEquals("name identifier text should be the bare name", "version", nameId!!.text)
    }

    fun `test official show usages handler collects file line and preview`() {
        val text = "First .ref {col-usage}.\nSecond .ref {col-usage}.\n\n{#col-usage}"
        myFixture.configureByText("col-usage.qd", text)

        val labelStart = text.indexOf("{#col-usage}") + 2
        val labelLeaf = myFixture.file.findElementAt(labelStart)
        assertNotNull("no leaf at label", labelLeaf)

        // The official ShowUsagesAction.startFindUsages resolves this handler via the
        // FindUsagesManager. It must report both .ref usages + the declaration, each with
        // a precise position so the native Show Usages window can render file/line/preview.
        val factory = QuarkdownFindUsagesHandlerFactory()
        assertTrue("factory should handle the label element", factory.canFindUsages(labelLeaf!!))

        val handler = factory.createFindUsagesHandler(labelLeaf, false)
        val usages = mutableListOf<com.intellij.usageView.UsageInfo>()
        handler.processElementUsages(
            labelLeaf, com.intellij.util.Processor { usages.add(it); true },
            com.intellij.find.findUsages.FindUsagesOptions(
                com.intellij.psi.search.GlobalSearchScope.projectScope(
                    project
                )
            )
        )

        System.out.println("official handler collected ${usages.size} usages")
        for (u in usages) {
            System.out.println("  at ${u.element?.textOffset} text='${u.element?.text}' range=${u.rangeInElement}")
        }
        // Only the two .ref usages are reported (the declaration itself is excluded).
        assertEquals("two .ref usages", 2, usages.size)
    }

    fun `test official show usages handler works on background thread`() {
        val text = "First .ref {bg-thread}.\nSecond .ref {bg-thread}.\n\n{#bg-thread}"
        myFixture.configureByText("bg-handler.qd", text)

        val labelStart = text.indexOf("{#bg-thread}") + 2
        val labelLeaf = myFixture.file.findElementAt(labelStart)
        assertNotNull("no leaf at label", labelLeaf)

        val factory = QuarkdownFindUsagesHandlerFactory()
        val handler = factory.createFindUsagesHandler(labelLeaf!!, false)

        // The native Show Usages search runs on a pooled thread without a read action;
        // our handler must acquire read access itself and return all usages.
        val usages = java.util.concurrent.CopyOnWriteArrayList<com.intellij.usageView.UsageInfo>()
        val errors = java.util.concurrent.CopyOnWriteArrayList<Throwable>()
        val done = java.util.concurrent.CountDownLatch(1)
        com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread {
            try {
                handler.processElementUsages(
                    labelLeaf, com.intellij.util.Processor { usages.add(it); true },
                    com.intellij.find.findUsages.FindUsagesOptions(
                        com.intellij.psi.search.GlobalSearchScope.projectScope(
                            project
                        )
                    )
                )
            } catch (t: Throwable) {
                errors.add(t)
                t.printStackTrace()
            } finally {
                done.countDown()
            }
        }

        assertTrue("search should finish", done.await(30, java.util.concurrent.TimeUnit.SECONDS))
        assertTrue("no exceptions on background thread, got ${errors.map { it.message }}", errors.isEmpty())
        assertEquals("handler should report the two .ref usages", 2, usages.size)
    }

    fun `test show usages title uses References type and bare id`() {
        val text = "First .ref {title-test}.\nSecond .ref {title-test}.\n\n{#title-test}"
        myFixture.configureByText("title-test.qd", text)

        val labelStart = text.indexOf("{#title-test}") + 2
        val labelLeaf = myFixture.file.findElementAt(labelStart)
        assertNotNull("no leaf at label", labelLeaf)

        // getType: the grey part of the title must be "References" (not the id again).
        val type = com.intellij.usageView.UsageViewUtil.getType(labelLeaf!!)
        System.out.println("usage title type='$type'")
        assertEquals("References", type)

        // getLongName: the white part must be the bare id (no braces, no repetition).
        val longName = com.intellij.usageView.UsageViewUtil.getLongName(labelLeaf)
        System.out.println("usage title longName='$longName'")
        assertEquals("title-test", longName)

        // The FindUsagesProvider agrees: type = "References", descriptive name = bare id.
        val provider = QuarkdownFindUsagesProvider()
        assertEquals("References", provider.getType(labelLeaf))
        assertEquals("title-test", provider.getDescriptiveName(labelLeaf))
    }

    fun `test label ctrl click outcome is show usages`() {
        val text = "First .ref {native-label}.\nSecond .ref {native-label}.\n\n{#native-label}"
        myFixture.configureByText("native-label.qd", text)

        val labelStart = text.indexOf("{#native-label}") + 2
        myFixture.editor.caretModel.moveToOffset(labelStart + 3)

        // The label declaration resolves to itself, so the platform computes a
        // "Show Usages" outcome and opens the usages popup AT the declaration —
        // it never jumps to the first usage.
        val outcome = com.intellij.codeInsight.navigation.actions.GotoDeclarationOrUsageHandler2
            .testGTDUOutcomeInNonBlockingReadAction(myFixture.editor, myFixture.file, labelStart + 3)
        System.out.println("label ctrl+click outcome=$outcome")
        assertEquals("SU", outcome.toString())

        // Ctrl+Mouse data must expose the declaration range (hover underline).
        val data = com.intellij.codeInsight.navigation.actions.GotoDeclarationOrUsageHandler2
            .getCtrlMouseData(myFixture.editor, myFixture.file, labelStart + 3)
        System.out.println("label ctrlMouseData=${data?.ranges} navigatable=${data?.isNavigatable}")
        assertNotNull("Ctrl+Mouse data should be available over the label", data)
        assertTrue("label must be navigatable via the platform", data!!.isNavigatable)
    }

    fun `test no-ref label resolves to itself and reports no usages`() {
        val text = "No refs.\n\n{#self-only}"
        myFixture.configureByText("self-only.qd", text)

        val labelStart = text.indexOf("{#self-only}") + 2
        val ref = myFixture.file.findReferenceAt(labelStart + 2)
        assertNotNull("should find reference at label", ref)

        val target = ref!!.resolve()
        assertNotNull("label should resolve to itself", target)
        assertTrue(
            "target should be at the {#self-only} declaration",
            target!!.textOffset >= text.indexOf("{#self-only}")
        )

        // The declaration must not be reported as a usage of itself: the Find Usages handler
        // (which the Show Usages popup uses) must report ZERO usages, so a no-ref declaration
        // shows a clean "No references found" hint instead of a self-reference flash.
        val factory = QuarkdownFindUsagesHandlerFactory()
        assertTrue("factory should handle the label element", factory.canFindUsages(target))
        val handler = factory.createFindUsagesHandler(target, false)
        val usages = mutableListOf<com.intellij.usageView.UsageInfo>()
        handler.processElementUsages(
            target, com.intellij.util.Processor { usages.add(it); true },
            com.intellij.find.findUsages.FindUsagesOptions(
                com.intellij.psi.search.GlobalSearchScope.projectScope(project)
            )
        )
        assertTrue("a no-ref declaration must report zero usages, got ${usages.size}", usages.isEmpty())
    }
}

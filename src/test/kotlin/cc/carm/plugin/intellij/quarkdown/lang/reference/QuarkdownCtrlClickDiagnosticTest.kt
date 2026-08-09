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

        val factory = cc.carm.plugin.intellij.quarkdown.lang.reference.QuarkdownFindUsagesHandlerFactory()
        assertTrue("factory should handle the label element", factory.canFindUsages(target!!))

        val handler = factory.createFindUsagesHandler(target, false)
        assertNotNull("handler should be created", handler)
        assertTrue("handler should be QuarkdownFindUsagesHandler",
            handler is cc.carm.plugin.intellij.quarkdown.lang.reference.QuarkdownFindUsagesHandler)

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
            System.out.println("  usage at ${u.element?.textOffset} text='${u.element?.text}' range=${u.getRangeInElement()}")
        }
        assertTrue(
            "handler should report both .ref usages + declaration, got ${usages.size}",
            usages.size >= 3
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
        assertTrue("ref leaf must be PsiNamedElement", refLeaf is com.intellij.psi.PsiNamedElement)

        val outcome = com.intellij.codeInsight.navigation.actions.GotoDeclarationOrUsageHandler2
            .testGTDUOutcomeInNonBlockingReadAction(myFixture.editor, myFixture.file, refIdStart + 2)
        System.out.println("symbol-path outcome at ref=$outcome")
    }

    fun `test label declaration returns usages for hover underline`() {
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

        // The handler returns every usage so the platform underlines the whole id on hover.
        val handlers = com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler.EP_NAME.extensionList
        val ourHandler = handlers.firstOrNull { it is QuarkdownGotoDeclarationHandler }
        assertNotNull("QuarkdownGotoDeclarationHandler must be registered", ourHandler)
        val targets = ourHandler!!.getGotoDeclarationTargets(labelLeaf, labelStart, myFixture.editor) ?: emptyArray()
        assertEquals("handler should return both .ref usages for the underline, got ${targets.size}", 2, targets.size)

        // The FindUsagesHandler backing the Show Usages popup reports both usages + declaration.
        val factory = cc.carm.plugin.intellij.quarkdown.lang.reference.QuarkdownFindUsagesHandlerFactory()
        val handler = factory.createFindUsagesHandler(labelLeaf, false)
        val usages = mutableListOf<com.intellij.usageView.UsageInfo>()
        handler.processElementUsages(labelLeaf, com.intellij.util.Processor { usages.add(it); true },
            com.intellij.find.findUsages.FindUsagesOptions(com.intellij.psi.search.GlobalSearchScope.projectScope(project)))
        assertTrue("Show Usages popup should list both .ref usages + declaration, got ${usages.size}", usages.size >= 3)
    }

    fun `test hover on label returns usages and does not pop up (background thread)`() {
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
        // It must return the usage targets (so the platform underlines the whole id) and
        // must NOT schedule a popup (the popup is shown by the editor mouse listener).
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
        assertEquals("hover should return both .ref usages (for underline), got ${results.size}", 2, results.size)
        for (r in results) {
            // Targets are the .ref id leaves (QuarkdownIdLeafPsiElement).
            assertTrue("hover target should be a PsiElement leaf", r is com.intellij.psi.PsiElement && r.textRange.length > 0)
        }
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

    fun `test var declaration returns usages for hover underline`() {
        val text = ".var {version} {v12}\n.include {.version/file.qd}"
        myFixture.configureByText("su-var.qd", text)

        val varNameStart = text.indexOf("{version}") + 1
        val varLeaf = myFixture.file.findElementAt(varNameStart)
        assertNotNull("no leaf at var name", varLeaf)
        assertTrue("var declaration leaf must be PsiNamedElement", varLeaf is com.intellij.psi.PsiNamedElement)

        val handlers = com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler.EP_NAME.extensionList
        val ourHandler = handlers.firstOrNull { it is QuarkdownGotoDeclarationHandler }
        assertNotNull("QuarkdownGotoDeclarationHandler must be registered", ourHandler)
        // The handler returns the `.name` usages so the platform underlines the whole id.
        val targets = ourHandler!!.getGotoDeclarationTargets(varLeaf, varNameStart, myFixture.editor) ?: emptyArray()
        assertEquals("var declaration should return the .version usage(s), got ${targets.size}", 1, targets.size)
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
        val factory = cc.carm.plugin.intellij.quarkdown.lang.reference.QuarkdownFindUsagesHandlerFactory()
        assertTrue("factory should handle the label element", factory.canFindUsages(labelLeaf!!))

        val handler = factory.createFindUsagesHandler(labelLeaf, false)
        val usages = mutableListOf<com.intellij.usageView.UsageInfo>()
        handler.processElementUsages(labelLeaf, com.intellij.util.Processor { usages.add(it); true },
            com.intellij.find.findUsages.FindUsagesOptions(com.intellij.psi.search.GlobalSearchScope.projectScope(project)))

        System.out.println("official handler collected ${usages.size} usages")
        for (u in usages) {
            System.out.println("  at ${u.element?.textOffset} text='${u.element?.text}' range=${u.getRangeInElement()}")
        }
        assertEquals("declaration + 2 usages", 3, usages.size)
    }

    fun `test official show usages handler works on background thread`() {
        val text = "First .ref {bg-thread}.\nSecond .ref {bg-thread}.\n\n{#bg-thread}"
        myFixture.configureByText("bg-handler.qd", text)

        val labelStart = text.indexOf("{#bg-thread}") + 2
        val labelLeaf = myFixture.file.findElementAt(labelStart)
        assertNotNull("no leaf at label", labelLeaf)

        val factory = cc.carm.plugin.intellij.quarkdown.lang.reference.QuarkdownFindUsagesHandlerFactory()
        val handler = factory.createFindUsagesHandler(labelLeaf!!, false)

        // The native Show Usages search runs on a pooled thread without a read action;
        // our handler must acquire read access itself and return all usages.
        val usages = java.util.concurrent.CopyOnWriteArrayList<com.intellij.usageView.UsageInfo>()
        val errors = java.util.concurrent.CopyOnWriteArrayList<Throwable>()
        val done = java.util.concurrent.CountDownLatch(1)
        com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread {
            try {
                handler.processElementUsages(labelLeaf, com.intellij.util.Processor { usages.add(it); true },
                    com.intellij.find.findUsages.FindUsagesOptions(com.intellij.psi.search.GlobalSearchScope.projectScope(project)))
            } catch (t: Throwable) {
                errors.add(t)
                t.printStackTrace()
            } finally {
                done.countDown()
            }
        }

        assertTrue("search should finish", done.await(30, java.util.concurrent.TimeUnit.SECONDS))
        assertTrue("no exceptions on background thread, got ${errors.map { it.message }}", errors.isEmpty())
        assertTrue("handler should report all usages, got ${usages.size}", usages.size >= 3)
    }

    fun `test mouse listener detects declaration on ctrl click offset`() {
        val text = "First .ref {click-label}.\nSecond .ref {click-label}.\n\n{#click-label}"
        myFixture.configureByText("click-label.qd", text)

        val listener = cc.carm.plugin.intellij.quarkdown.lang.reference.QuarkdownEditorMouseListener()

        // Offset inside the {#click-label} declaration.
        val labelStart = text.indexOf("{#click-label}") + 2
        val decl = listener.declarationElementAt(myFixture.file, labelStart)
        assertNotNull("declaration element should be found at the label offset", decl)
        assertTrue("declaration element must be a PsiNamedElement", decl is com.intellij.psi.PsiNamedElement)
        assertEquals("name should be the bare id", "click-label", (decl as com.intellij.psi.PsiNamedElement).name)

        // Offset inside a .ref usage is NOT a declaration.
        val refIdStart = text.indexOf("{click-label}") + 1
        assertNull("ref usage is not a declaration", listener.declarationElementAt(myFixture.file, refIdStart))

        // Offset in plain text is not a declaration.
        assertNull("plain text is not a declaration", listener.declarationElementAt(myFixture.file, 0))
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
        val provider = cc.carm.plugin.intellij.quarkdown.lang.reference.QuarkdownFindUsagesProvider()
        assertEquals("References", provider.getType(labelLeaf))
        assertEquals("title-test", provider.getDescriptiveName(labelLeaf))
    }
}

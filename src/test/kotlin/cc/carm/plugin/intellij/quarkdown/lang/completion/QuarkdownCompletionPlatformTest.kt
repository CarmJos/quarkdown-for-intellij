package cc.carm.plugin.intellij.quarkdown.lang.completion

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Verifies the structural file-path completion contributor produces correct results.
 *
 * Function-name / parameter / enum completion is provided by the official Quarkdown
 * Language Server (see `QuarkdownLspServerIntegrationTest`); this test only covers the
 * registry-independent path completion for `.include`/`.read`/`.css`/`.code`.
 */
class QuarkdownCompletionPlatformTest : BasePlatformTestCase() {

    fun `test completion contributors registered for quarkdown`() {
        val contributors = com.intellij.codeInsight.completion.CompletionContributor
            .forLanguage(cc.carm.plugin.intellij.quarkdown.QuarkdownLanguage.INSTANCE)
        assertTrue(
            "QuarkdownCompletionContributor should be registered",
            contributors.any { it is QuarkdownCompletionContributor }
        )
    }

    /**
     * Directly drives the Quarkdown completion contributor (the headless fixture cannot
     * show a lookup) and returns the produced lookup strings.
     */
    private fun directCompletions(text: String): List<String> {
        myFixture.configureByText("c.qd", text)
        val editor = myFixture.editor
        val offset = editor.caretModel.offset
        val params = com.intellij.codeInsight.completion.CompletionParameters(
            myFixture.file, myFixture.file,
            com.intellij.codeInsight.completion.CompletionType.BASIC,
            0, offset, editor,
            object : com.intellij.codeInsight.completion.CompletionProcess {
                override fun isAutopopupCompletion(): Boolean = false
            }
        )
        val contributor = QuarkdownCompletionContributor()
        val service = com.intellij.codeInsight.completion.CompletionService.getCompletionService()
        val collected = mutableListOf<String>()
        val consumer = com.intellij.util.Consumer<com.intellij.codeInsight.completion.CompletionResult> {
            collected.add(it.lookupElement.lookupString)
        }
        val resultSet = service.createResultSet(
            params, consumer, contributor,
            com.intellij.codeInsight.completion.PrefixMatcher.ALWAYS_TRUE
        )
        service.getVariantsFromContributor(params, contributor, resultSet)
        return collected
    }

    fun `test completion confidence allows autopopup for quarkdown`() {
        // shouldSkipAutopopup must return NO for .qd files (do not skip auto-popup).
        val confidence = QuarkdownCompletionConfidence()
        myFixture.configureByText("conf.qd", ".")
        val result = confidence.shouldSkipAutopopup(
            myFixture.editor,
            myFixture.file.findElementAt(0) ?: myFixture.file,
            myFixture.file,
            1
        )
        System.out.println("shouldSkipAutopopup('.qd') = $result")
        assertTrue("Quarkdown files must allow auto-popup", result == com.intellij.util.ThreeState.NO)
    }

    // ------------------------------------------------------------------
    // File-path completion for `.include` / `.read` / `.css` / `.code`
    // ------------------------------------------------------------------

    fun `test include path completion suggests sibling directory`() {
        myFixture.addFileToProject("docs/intro.qd", "intro")
        myFixture.addFileToProject("docs/install.qd", "install")
        myFixture.addFileToProject("images/logo.png", "logo")

        val completions = directCompletions(".include {doc<caret>}")
        System.out.println("include completions for 'doc': $completions")
        assertTrue("should suggest the docs/ directory", completions.contains("docs/"))
    }

    fun `test include path completion filters inside a subdirectory`() {
        myFixture.addFileToProject("docs/intro.qd", "intro")
        myFixture.addFileToProject("docs/install.qd", "install")
        myFixture.addFileToProject("docs/manual.qd", "manual")

        val completions = directCompletions(".include {docs/ins<caret>}")
        System.out.println("include completions for 'docs/ins': $completions")
        assertTrue("should suggest install.qd", completions.contains("install.qd"))
        assertTrue("should not suggest intro.qd", !completions.contains("intro.qd"))
        assertTrue("should not suggest manual.qd", !completions.contains("manual.qd"))
    }

    fun `test include quoted path completion`() {
        myFixture.addFileToProject("docs/intro.qd", "intro")

        val completions = directCompletions(".include {\"doc<caret>\"}")
        System.out.println("quoted include completions: $completions")
        assertTrue("should suggest docs/ inside quotes", completions.contains("docs/"))
    }

    fun `test read path completion`() {
        myFixture.addFileToProject("data/file.qd", "data")

        val completions = directCompletions(".read {data/fi<caret>}")
        System.out.println("read completions: $completions")
        assertTrue("should suggest file.qd", completions.contains("file.qd"))
    }

    fun `test include path completion falls back to nearest existing directory`() {
        myFixture.addFileToProject("docs/images/logo.png", "logo")

        val completions = directCompletions(".include {docs/ima<caret>}")
        System.out.println("fallback completions for 'docs/ima': $completions")
        assertTrue("should suggest images/ from within docs/", completions.contains("images/"))
    }

    fun `test no file completion for non-path functions`() {
        myFixture.addFileToProject("docs/intro.qd", "intro")

        // `.pagemargin` and `.css` take content, not a path.
        val pagemargin = directCompletions(".pagemargin {doc<caret>}")
        assertTrue(
            "no path completion should appear for .pagemargin, got: $pagemargin",
            pagemargin.none { it == "docs/" }
        )
        val css = directCompletions(".css {doc<caret>}")
        assertTrue(
            "no path completion should appear for .css (raw CSS content), got: $css",
            css.none { it == "docs/" }
        )
    }

    // ------------------------------------------------------------------
    // Variable completion for `.var` declarations
    // ------------------------------------------------------------------

    fun `test var declaration suggests declared variable`() {
        val completions = directCompletions(".var {status} {ok}\n\nCurrent status is .sta<caret>")
        System.out.println("var completions for '.sta': $completions")
        assertTrue("should suggest the declared variable status", completions.contains("status"))
    }

    fun `test var completion offers all declared variables after bare dot`() {
        val completions = directCompletions(".var {status} {ok}\n.var {version} {1.0}\n\n.ver<caret>")
        System.out.println("var completions for '.ver': $completions")
        assertTrue("should suggest version", completions.contains("version"))
        assertTrue("should not suggest unrelated status", !completions.contains("status"))
    }

    fun `test var completion does not fire inside an argument`() {
        val completions = directCompletions(".var {status} {ok}\n\n.include {sta<caret>}")
        // Caret is inside the include value braces → file-path completion, not variables.
        System.out.println("var completions inside include: $completions")
        assertTrue("no variable completion inside an argument, got: $completions", completions.none { it == "status" })
    }
}

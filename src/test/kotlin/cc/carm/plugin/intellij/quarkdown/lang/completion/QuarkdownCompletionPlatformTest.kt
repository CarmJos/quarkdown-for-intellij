package cc.carm.plugin.intellij.quarkdown.lang.completion

import cc.carm.plugin.intellij.quarkdown.lang.function.FunctionRegistry
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

/**
 * Verifies function-name completion produces grammatically correct results.
 */
class QuarkdownCompletionPlatformTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        // Point the registry at the local scoop Quarkdown so functions load.
        val path = System.getenv("QUARKDOWN_HOME")
            ?: "C:\\Users\\Karmu\\scoop\\apps\\quarkdown\\current"
        FunctionRegistry.getInstance(project).refresh(path, force = true)
    }

    fun `test registry contains center`() {
        val names = FunctionRegistry.getInstance(project).getFunctions().map { it.name }
        assertTrue("center should be in the registry (${names.size} functions)", names.contains("center"))
    }

    fun `test registry size`() {
        val functions = FunctionRegistry.getInstance(project).getFunctions()
        System.out.println("registry loaded ${functions.size} functions")
        assertTrue("registry should load functions", functions.size > 100)
    }

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

    fun `test dot completion lists center and many functions`() {
        val collected = directCompletions(".<caret>")
        System.out.println("completions for '.': ${collected.take(30)} (total ${collected.size})")
        assertTrue("should include center", collected.contains("center"))
        assertTrue("should list many functions", collected.size > 100)
    }

    fun `test cent prefix offers center`() {
        val collected = directCompletions(".cent<caret>")
        System.out.println("completions for '.cent': ${collected.take(30)}")
        assertTrue("should include center", collected.contains("center"))
    }

    fun `test no parameter completion appears after a bare dot`() {
        val collected = directCompletions(".<caret>")
        // `.background:{}` came from PARAMETER completion. After a dot, only plain
        // function names may be offered — never `name:{}` fragments.
        assertTrue(
            "no parameter completion should appear after a dot, got: ${collected.filter { it.contains(":") || it.contains("{") }}",
            collected.none { it.contains(":") || it.contains("{") }
        )
    }

    fun `test bac prefix offers only plain function names`() {
        val collected = directCompletions(".bac<caret>")
        System.out.println("completions for '.bac': $collected")
        assertTrue(
            "results should be plain function names, got: $collected",
            collected.all { !it.contains(":") && !it.contains("{") && !it.contains("}") }
        )
    }

    fun `test dot after pageformat on a new line offers function names`() {
        // Mirrors the reported bug: a `.` typed on a fresh line below `.pageformat`
        // must NOT offer pageformat's parameters — it must offer function names.
        val collected = directCompletions(".pageformat size:{a4} margin:{1}\n.<caret>")
        System.out.println("completions after .pageformat\\n.: ${collected.take(20)}")
        assertTrue("should include center", collected.contains("center"))
        assertTrue("should list many functions", collected.size > 100)
        // No parameter completions (which appear as `name:{}` fragments) may leak here.
        assertTrue(
            "no parameter completion may leak after a new-line dot, got: ${collected.filter { it.contains(":") || it.contains("{") }}",
            collected.none { it.contains(":") || it.contains("{") }
        )
    }

    fun `test dot inside a continuation line stays in the parent call`() {
        // After `.tableofcontents \` the continuation belongs to the call, so typing
        // inside a continuation should offer that call's parameters.
        val collected = directCompletions(".tableofcontents \\\n    title:{**目 录**} maxdepth:{3} \\\n    <caret>")
        System.out.println("completions in continuation: ${collected.take(20)}")
        // The continuation is inside the call → next-argument completion.
        assertTrue("expected parameter completions in continuation, got: $collected", collected.isNotEmpty())
        assertTrue(
            "expected title/maxdepth/indexheading/breakpage params",
            collected.any { it == "indexheading" || it == "breakpage" || it == "numberheading" }
        )
    }

    fun `test completion confidence allows autopopup for quarkdown`() {
        // shouldSkipAutopopup must return NO for .qd files (do not skip auto-popup).
        val confidence = QuarkdownCompletionConfidence()
        myFixture.configureByText("conf.qd", ".")
        val result = confidence.shouldSkipAutopopup(
            myFixture.file.findElementAt(0) ?: myFixture.file,
            myFixture.file,
            1
        )
        System.out.println("shouldSkipAutopopup('.qd') = $result")
        assertTrue("Quarkdown files must allow auto-popup", result == com.intellij.util.ThreeState.NO)
    }
}

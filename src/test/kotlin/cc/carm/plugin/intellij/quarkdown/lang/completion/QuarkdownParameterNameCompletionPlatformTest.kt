package cc.carm.plugin.intellij.quarkdown.lang.completion

import cc.carm.plugin.intellij.quarkdown.lang.lsp.QuarkdownLspFunctionSignatureCache
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Verifies the parameter-name completion contributor: when the caret is inside a
 * function call's argument region, it offers the not-yet-used parameter names (as
 * `name:` items), complementing the LSP completion which only offers values inside `{}`.
 */
class QuarkdownParameterNameCompletionPlatformTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        val cache = QuarkdownLspFunctionSignatureCache.getInstance(project)
        cache.seedSignature(
            "pageformat",
            listOf("side", "pages", "size", "orientation", "width", "height", "margin")
        )
        cache.seedSignature("multiply", listOf("a", "by"))
        cache.seedSignature("docauthor", listOf("author"))
    }

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
        val contributor = QuarkdownParameterNameCompletionContributor()
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

    fun `test offers remaining parameters inside empty braces`() {
        // Caret inside the first (empty) arg braces after `.pageformat` — the position the
        // LSP snippet leaves after completing the function name. The empty `{}` is the
        // first positional arg (slot 0 = side), so the remaining params are offered.
        val completions = directCompletions(".pageformat {<caret>}")
        System.out.println("params inside empty braces: $completions")
        assertFalse("side is the current positional arg and is consumed", completions.contains("side"))
        assertTrue("should offer pages", completions.contains("pages"))
        assertTrue("should offer size", completions.contains("size"))
    }

    fun `test does not offer sibling parameters inside a named argument value`() {
        // Regression: inside `size:{<caret>}` the LSP completes *size's* allowed values;
        // the parent function's sibling parameters (alignment, margin, ...) must NOT be
        // offered there. `size` itself is already written, so nothing should be offered.
        val completions = directCompletions(".pageformat size:{<caret>}")
        System.out.println("params inside size:{}: $completions")
        assertTrue(
            "no sibling params inside a named argument's value braces, got: $completions",
            completions.isEmpty()
        )
    }

    fun `test does not offer already-written named parameters`() {
        // `size:` already written → it should no longer be suggested, but others remain.
        val completions = directCompletions(".pageformat size:{a4} {<caret>}")
        System.out.println("params after size:{a4}: $completions")
        assertFalse("size should be consumed", completions.contains("size"))
        assertFalse("side should be consumed by the positional {}", completions.contains("side"))
        assertTrue("pages should still be offered", completions.contains("pages"))
    }

    fun `test offers remaining params after a named arg`() {
        // Caret right after `size:{a4}` (next-argument position) — this is handled by the
        // LSP completion (it returns the remaining parameter names there), so our
        // contributor intentionally returns nothing to avoid duplicates.
        val completions = directCompletions(".pageformat size:{a4} <caret>")
        assertTrue("LSP handles the after-space position; contributor stays quiet: $completions", completions.isEmpty())
    }

    fun `test does not fire on the function name itself`() {
        // Caret on the function name → function-name completion, not parameter completion.
        val completions = directCompletions(".page<caret>")
        assertTrue("no parameter names on function name, got: $completions", completions.isEmpty())
    }

    fun `test unknown function offers nothing`() {
        val completions = directCompletions(".unknownfunc {<caret>}")
        assertTrue("no params for unknown function, got: $completions", completions.isEmpty())
    }

    fun `test contributors registered for quarkdown`() {
        val contributors = com.intellij.codeInsight.completion.CompletionContributor
            .forLanguage(cc.carm.plugin.intellij.quarkdown.QuarkdownLanguage.INSTANCE)
        assertTrue(
            "QuarkdownParameterNameCompletionContributor should be registered",
            contributors.any { it is QuarkdownParameterNameCompletionContributor }
        )
    }
}

package cc.carm.plugin.intellij.quarkdown.lang.inlay

import cc.carm.plugin.intellij.quarkdown.lang.function.QuarkdownCallParser
import cc.carm.plugin.intellij.quarkdown.lang.lsp.QuarkdownLspFunctionSignatureCache
import com.intellij.codeInsight.hints.InlayHintsSink
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Verifies parameter-name inlay hints are produced for positional arguments using
 * function signatures seeded from the LSP cache (the LSP fetch path is covered by
 * QuarkdownLspServerIntegrationTest).
 */
class QuarkdownParameterNameInlayProviderTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        val cache = QuarkdownLspFunctionSignatureCache.getInstance(project)
        cache.seedSignature("multiply", listOf("a", "by"))
        cache.seedSignature("docauthor", listOf("author"))
        cache.seedSignature("pageformat", listOf("side", "pages", "size", "orientation", "width", "height", "margin"))
        cache.seedSignature("row", listOf("alignment", "cross", "gap", "body"))
        cache.seedSignature("pagemargin", listOf("position", "content"))
    }

    /** Collects inlay hint offsets by driving the provider's collector directly. */
    private fun collectHintOffsets(text: String): List<Int> {
        val psiFile = myFixture.configureByText("c.qd", text)
        val collector = QuarkdownParameterNameInlayProvider.Collector(myFixture.editor)
        val sink = RecordingSink()
        collector.processFile(psiFile, sink)
        return sink.offsets
    }

    fun `test positional args get parameter name inlays`() {
        // `.multiply {6} by:{3}` → `a:` before `{6}` (offset of the `{` after `.multiply `).
        val offsets = collectHintOffsets(".multiply {6} by:{3}\n")
        val expectedBrace = ".multiply ".length // index of `{`
        assertTrue("expected an inlay at $expectedBrace, got offsets: $offsets", offsets.contains(expectedBrace))
        // Named arg `by:{3}` must NOT get an inlay.
        assertTrue("named arg must not get an inlay, got offsets: $offsets", offsets.size == 1)
    }

    fun `test docauthor positional arg gets author inlay`() {
        val offsets = collectHintOffsets(".docauthor {CarmJos}\n")
        val expectedBrace = ".docauthor ".length
        assertTrue("expected an inlay at $expectedBrace, got offsets: $offsets", offsets.contains(expectedBrace))
    }

    fun `test first positional arg maps to first parameter`() {
        // `.pageformat {a4}` → `side:` before `{a4}`.
        val offsets = collectHintOffsets(".pageformat {a4}\n")
        val expectedBrace = ".pageformat ".length
        assertTrue("expected an inlay at $expectedBrace, got offsets: $offsets", offsets.contains(expectedBrace))
    }

    fun `test named args skip positional slots`() {
        // `.multiply by:{3} {6}` — the positional `{6}` maps to `a` (named `by` does not
        // consume slot 0). Only the positional arg gets an inlay.
        val offsets = collectHintOffsets(".multiply by:{3} {6}\n")
        val expectedBrace = ".multiply by:{3} ".length // index of `{` before `6`
        assertTrue("expected an inlay at $expectedBrace, got offsets: $offsets", offsets.contains(expectedBrace))
        assertTrue("only the positional arg should get an inlay, got offsets: $offsets", offsets.size == 1)
    }

    /** Simple InlayHintsSink that records the offsets of added inline hints. */
    private class RecordingSink : InlayHintsSink {
        val offsets = mutableListOf<Int>()

        override fun addInlineElement(
            offset: Int,
            relatesToPrecedingText: Boolean,
            presentation: com.intellij.codeInsight.hints.presentation.InlayPresentation,
            update: Boolean
        ) {
            offsets.add(offset)
        }

        override fun addBlockElement(
            offset: Int,
            relatesToPrecedingText: Boolean,
            showAbove: Boolean,
            priority: Int,
            presentation: com.intellij.codeInsight.hints.presentation.InlayPresentation
        ) {
            offsets.add(offset)
        }

        override fun addInlineElement(
            offset: Int,
            presentation: com.intellij.codeInsight.hints.presentation.RootInlayPresentation<*>,
            constraints: com.intellij.codeInsight.hints.HorizontalConstraints?
        ) {
            offsets.add(offset)
        }

        override fun addBlockElement(
            logicalLine: Int,
            showAbove: Boolean,
            presentation: com.intellij.codeInsight.hints.presentation.RootInlayPresentation<*>,
            constraints: com.intellij.codeInsight.hints.BlockConstraints?
        ) {
            offsets.add(logicalLine)
        }
    }
}


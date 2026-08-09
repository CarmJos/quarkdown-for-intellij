package cc.carm.plugin.intellij.quarkdown.lang.inlay

import cc.carm.plugin.intellij.quarkdown.lang.function.FunctionMetadata
import cc.carm.plugin.intellij.quarkdown.lang.function.ParameterMetadata
import com.intellij.codeInsight.hints.BlockConstraints
import com.intellij.codeInsight.hints.HorizontalConstraints
import com.intellij.codeInsight.hints.InlayHintsProviderExtension
import com.intellij.codeInsight.hints.InlayHintsSink
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.codeInsight.hints.presentation.RootInlayPresentation
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Verifies the parameter-name inlay hints:
 *  - the provider is registered for the Quarkdown language,
 *  - the collector produces one inline hint before the opening brace of each positional
 *    argument of a known function call, and none for named arguments or unknown functions.
 */
class QuarkdownParameterNameInlayPlatformTest : BasePlatformTestCase() {

    private val functions = listOf(
        FunctionMetadata(
            name = "pagemargin",
            parameters = listOf(
                ParameterMetadata("position", "pagemarginposition", 0),
                ParameterMetadata("content", "markdowncontent", 1, isOptional = true)
            )
        ),
        FunctionMetadata(
            name = "multiply",
            parameters = listOf(
                ParameterMetadata("a", "number", 0),
                ParameterMetadata("by", "number", 1)
            )
        )
    )

    private class RecordingSink : InlayHintsSink {
        val inlineElements = mutableListOf<Int>()

        override fun addInlineElement(
            offset: Int,
            relatesToPrecedingText: Boolean,
            presentation: InlayPresentation,
            visible: Boolean
        ) {
            inlineElements += offset
        }

        override fun addBlockElement(
            offset: Int,
            relatesToPrecedingText: Boolean,
            showAbove: Boolean,
            priority: Int,
            presentation: InlayPresentation
        ) {
        }

        override fun addInlineElement(
            offset: Int,
            presentation: RootInlayPresentation<*>,
            constraints: HorizontalConstraints?
        ) {
            inlineElements += offset
        }

        override fun addBlockElement(
            logicalLine: Int,
            showAbove: Boolean,
            presentation: RootInlayPresentation<*>,
            constraints: BlockConstraints?
        ) {
        }
    }

    fun `test provider is registered`() {
        val providers = InlayHintsProviderExtension.findProviders()
        assertTrue(
            "QuarkdownParameterNameInlayProvider should be registered, got: ${providers.map { it.provider.javaClass.simpleName }}",
            providers.any { it.provider is QuarkdownParameterNameInlayProvider }
        )
    }

    fun `test hint before each positional argument`() {
        myFixture.configureByText("t.qd", ".pagemargin {bottomcenter}")
        val collector = QuarkdownParameterNameInlayProvider.CollectorForTest(myFixture.editor, functions)
        val sink = RecordingSink()
        collector.collect(myFixture.file as PsiFile, myFixture.editor, sink)

        assertTrue(
            "expected 1 hint for a single positional arg, got ${sink.inlineElements.size}",
            sink.inlineElements.size == 1
        )
        // The hint is placed right before the opening brace `{` of the argument.
        val expectedOffset = ".pagemargin ".length
        assertTrue(
            "hint should be at the opening brace, got ${sink.inlineElements[0]}",
            sink.inlineElements[0] == expectedOffset
        )
    }

    fun `test hint only for positional args, not named`() {
        myFixture.configureByText("t.qd", ".pagemargin {bottomcenter} content:{hello}")
        val collector = QuarkdownParameterNameInlayProvider.CollectorForTest(myFixture.editor, functions)
        val sink = RecordingSink()
        collector.collect(myFixture.file as PsiFile, myFixture.editor, sink)

        assertTrue(
            "only the positional argument should get a hint (named `content:` already carries its name), got ${sink.inlineElements.size}",
            sink.inlineElements.size == 1
        )
    }

    fun `test multiple positional arguments map to their params in order`() {
        myFixture.configureByText("t.qd", ".multiply {6} {3}")
        val collector = QuarkdownParameterNameInlayProvider.CollectorForTest(myFixture.editor, functions)
        val sink = RecordingSink()
        collector.collect(myFixture.file as PsiFile, myFixture.editor, sink)

        assertTrue("expected 2 hints, got ${sink.inlineElements.size}", sink.inlineElements.size == 2)
        val firstBrace = ".multiply ".length
        val secondBrace = ".multiply {6} ".length
        assertTrue(
            "hints should sit before each opening brace",
            sink.inlineElements[0] == firstBrace && sink.inlineElements[1] == secondBrace
        )
    }

    fun `test no hints for unknown functions`() {
        myFixture.configureByText("t.qd", ".notafunction {x}")
        val collector = QuarkdownParameterNameInlayProvider.CollectorForTest(myFixture.editor, functions)
        val sink = RecordingSink()
        collector.collect(myFixture.file as PsiFile, myFixture.editor, sink)

        assertTrue("no hints for unknown functions, got ${sink.inlineElements.size}", sink.inlineElements.isEmpty())
    }
}


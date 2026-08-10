package cc.carm.plugin.intellij.quarkdown.lang.table

import com.intellij.codeInsight.hints.BlockConstraints
import com.intellij.codeInsight.hints.HorizontalConstraints
import com.intellij.codeInsight.hints.InlayHintsSink
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.codeInsight.hints.presentation.RootInlayPresentation
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Directly drives the table inlay collector against a real Quarkdown document to verify
 * that bars are actually produced for table blocks.
 */
class QuarkdownInlayIntegrationTest : BasePlatformTestCase() {

    private class RecordingSink : InlayHintsSink {
        val inlineElements = mutableListOf<Int>()
        val blockElements = mutableListOf<Pair<Int, InlayPresentation>>()

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
            blockElements += offset to presentation
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
            blockElements += logicalLine to presentation
        }
    }

    fun `test collector produces bars for a table`() {
        myFixture.configureByText("t.qd", "| H1 | H2 |\n|----|----|\n| a  | b  |")
        val editor = myFixture.editor
        val collector = QuarkdownTableEditorProvider.CollectorForTest(editor)
        val sink = RecordingSink()

        val file = myFixture.file as PsiFile
        collector.collect(file, editor, sink)

        assertTrue(
            "should produce a horizontal block bar, got ${sink.blockElements.size}",
            sink.blockElements.size == 1
        )
        assertTrue(
            "should produce vertical bars for each row (3), got ${sink.inlineElements.size}",
            sink.inlineElements.size == 3
        )
    }

    fun `test no bars for non-table content`() {
        myFixture.configureByText("t.qd", "just prose here\n\n.noise {x}")
        val editor = myFixture.editor
        val collector = QuarkdownTableEditorProvider.CollectorForTest(editor)
        val sink = RecordingSink()
        collector.collect(myFixture.file as PsiFile, editor, sink)

        assertTrue(
            "no bars expected, got block=${sink.blockElements.size} inline=${sink.inlineElements.size}",
            sink.blockElements.isEmpty() && sink.inlineElements.isEmpty()
        )
    }

    fun `test collector re-produces bars after document change`() {
        myFixture.configureByText("t.qd", "| H1 | H2 |\n|----|----|\n| a  | b  |")
        val editor = myFixture.editor
        val collector = QuarkdownTableEditorProvider.CollectorForTest(editor)
        val sink = RecordingSink()

        collector.collect(myFixture.file as PsiFile, editor, sink)
        assertTrue("initial collect should produce bars", sink.blockElements.size == 1 && sink.inlineElements.size == 3)

        // Simulate the dialog's live "Format Table" write: the table gets wider.
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
            myFixture.editor.document.setText(
                "| H1       | H2       |\n|:--------:|:--------:|\n| a        | b        |"
            )
        }

        // Re-collect with the SAME collector instance (mirrors a reused inlay pass).
        val sink2 = RecordingSink()
        collector.collect(myFixture.file as PsiFile, editor, sink2)
        assertTrue(
            "bars must be re-produced after a document change, got block=${sink2.blockElements.size} inline=${sink2.inlineElements.size}",
            sink2.blockElements.size == 1 && sink2.inlineElements.size == 3
        )
    }
}

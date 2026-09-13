package cc.carm.plugin.intellij.quarkdown.lang.latex

import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.psi.tree.IElementType
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Verifies the wiring the pure tests cannot reach: that the platform really builds the TeX
 * highlighter for [QuarkdownLatexFileType] — the file type the equation editor's input field uses —
 * and that it lexes with the shared tokenizer.
 *
 * This is the extension-point half of "the input is highlighted as LaTeX": the mapping from token
 * to color is covered by `QuarkdownLatexSyntaxHighlighterTest`.
 */
class QuarkdownLatexEditorHighlighterTest : BasePlatformTestCase() {

    fun `test the TeX file type is highlighted with the shared tokenizer`() {
        val highlighter = EditorHighlighterFactory.getInstance()
            .createEditorHighlighter(project, QuarkdownLatexFileType.INSTANCE)
            ?: error("the platform must build an editor highlighter for the TeX file type")
        highlighter.setText("\\frac{a}")

        val types = mutableListOf<IElementType>()
        val iterator = highlighter.createIterator(0)
        while (!iterator.atEnd()) {
            iterator.tokenType?.let { types.add(it) }
            iterator.advance()
        }

        assertEquals(
            listOf(
                QuarkdownLatexTokenTypes.COMMAND,
                QuarkdownLatexTokenTypes.BRACE,
                QuarkdownLatexTokenTypes.TEXT,
                QuarkdownLatexTokenTypes.BRACE,
            ),
            types,
        )
    }
}

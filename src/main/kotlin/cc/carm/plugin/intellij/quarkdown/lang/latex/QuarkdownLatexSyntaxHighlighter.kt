package cc.carm.plugin.intellij.quarkdown.lang.latex

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.tree.IElementType

/**
 * Colors a bare TeX fragment with the very keys the annotator applies inside a document
 * (see [QuarkdownLatexHighlighting]), so a formula looks the same in the editor and in the equation
 * dialog, and its colors stay customizable in one place (*Editor | Color Scheme | Quarkdown*).
 *
 * Plain text is deliberately left uncolored, exactly as the annotator leaves it: it inherits the
 * editor's default foreground, which is what a formula's ordinary glyphs should look like.
 */
class QuarkdownLatexSyntaxHighlighter : SyntaxHighlighterBase() {

    override fun getHighlightingLexer(): Lexer = QuarkdownLatexLexer()

    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> =
        pack(KEYS[tokenType])

    private companion object {

        /** Every kind except `TEXT`, which keeps the editor's default color. */
        val KEYS: Map<IElementType, TextAttributesKey> = QuarkdownLatexSyntax.TokenKind.entries
            .filter { it != QuarkdownLatexSyntax.TokenKind.TEXT }
            .associate { QuarkdownLatexTokenTypes.of(it) to QuarkdownLatexHighlighting.keyFor(it) }
    }
}

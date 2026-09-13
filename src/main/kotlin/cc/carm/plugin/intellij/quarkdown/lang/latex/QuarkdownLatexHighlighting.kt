package cc.carm.plugin.intellij.quarkdown.lang.latex

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey

/**
 * Color keys for TeX/LaTeX content written inside Quarkdown equations.
 *
 * The keys are intentionally separate from `QuarkdownSyntaxHighlighter` because the equation
 * content is colored by an annotator rather than by the Quarkdown lexer: equations are plain
 * text leaves to the lexer, and teaching the lexer a second language would couple two
 * unrelated grammars. Each key falls back to a sensible platform default, so the colors work
 * out of the box and stay customisable per scheme.
 */
object QuarkdownLatexHighlighting {

    /** A control sequence such as `\frac` or `\alpha`. */
    val COMMAND = TextAttributesKey.createTextAttributesKey(
        "QUARKDOWN_LATEX_COMMAND", DefaultLanguageHighlighterColors.KEYWORD
    )

    /** A `\begin{…}` / `\end{…}` environment delimiter. */
    val ENVIRONMENT = TextAttributesKey.createTextAttributesKey(
        "QUARKDOWN_LATEX_ENVIRONMENT", DefaultLanguageHighlighterColors.CLASS_REFERENCE
    )

    /** A grouping brace. */
    val BRACE = TextAttributesKey.createTextAttributesKey(
        "QUARKDOWN_LATEX_BRACE", DefaultLanguageHighlighterColors.BRACES
    )

    /** An optional-argument bracket. */
    val BRACKET = TextAttributesKey.createTextAttributesKey(
        "QUARKDOWN_LATEX_BRACKET", DefaultLanguageHighlighterColors.BRACKETS
    )

    /** A `^` superscript marker. */
    val SUPERSCRIPT = TextAttributesKey.createTextAttributesKey(
        "QUARKDOWN_LATEX_SUPERSCRIPT", DefaultLanguageHighlighterColors.OPERATION_SIGN
    )

    /** A `_` subscript marker. */
    val SUBSCRIPT = TextAttributesKey.createTextAttributesKey(
        "QUARKDOWN_LATEX_SUBSCRIPT", DefaultLanguageHighlighterColors.OPERATION_SIGN
    )

    /** A `#1` macro parameter marker. */
    val PARAMETER = TextAttributesKey.createTextAttributesKey(
        "QUARKDOWN_LATEX_PARAMETER", DefaultLanguageHighlighterColors.PARAMETER
    )

    /** A numeric literal. */
    val NUMBER = TextAttributesKey.createTextAttributesKey(
        "QUARKDOWN_LATEX_NUMBER", DefaultLanguageHighlighterColors.NUMBER
    )

    /** A math operator or punctuation character. */
    val OPERATOR = TextAttributesKey.createTextAttributesKey(
        "QUARKDOWN_LATEX_OPERATOR", DefaultLanguageHighlighterColors.OPERATION_SIGN
    )

    /** A `%` comment running to the end of the line. */
    val COMMENT = TextAttributesKey.createTextAttributesKey(
        "QUARKDOWN_LATEX_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT
    )

    /** Plain equation text (identifiers, spaces, symbols). */
    val TEXT = TextAttributesKey.createTextAttributesKey(
        "QUARKDOWN_LATEX_TEXT", DefaultLanguageHighlighterColors.IDENTIFIER
    )

    /** Maps a token kind to the color key that should be applied to it. */
    fun keyFor(kind: QuarkdownLatexSyntax.TokenKind): TextAttributesKey = when (kind) {
        QuarkdownLatexSyntax.TokenKind.COMMAND -> COMMAND
        QuarkdownLatexSyntax.TokenKind.ENVIRONMENT -> ENVIRONMENT
        QuarkdownLatexSyntax.TokenKind.BRACE -> BRACE
        QuarkdownLatexSyntax.TokenKind.BRACKET -> BRACKET
        QuarkdownLatexSyntax.TokenKind.SUPERSCRIPT -> SUPERSCRIPT
        QuarkdownLatexSyntax.TokenKind.SUBSCRIPT -> SUBSCRIPT
        QuarkdownLatexSyntax.TokenKind.PARAMETER -> PARAMETER
        QuarkdownLatexSyntax.TokenKind.NUMBER -> NUMBER
        QuarkdownLatexSyntax.TokenKind.OPERATOR -> OPERATOR
        QuarkdownLatexSyntax.TokenKind.COMMENT -> COMMENT
        QuarkdownLatexSyntax.TokenKind.TEXT -> TEXT
    }
}

package cc.carm.plugin.intellij.quarkdown.lang.latex

import com.intellij.lexer.LexerBase
import com.intellij.psi.tree.IElementType

/**
 * Element types of the TeX content of an equation, one per [QuarkdownLatexSyntax.TokenKind].
 *
 * They exist so the platform's highlighting machinery has something to attach colors to: the lexer
 * emits them, [QuarkdownLatexSyntaxHighlighter] maps them back to the color keys of
 * [QuarkdownLatexHighlighting].
 */
object QuarkdownLatexTokenTypes {

    val COMMAND = createToken("COMMAND")
    val ENVIRONMENT = createToken("ENVIRONMENT")
    val BRACE = createToken("BRACE")
    val BRACKET = createToken("BRACKET")
    val SUPERSCRIPT = createToken("SUPERSCRIPT")
    val SUBSCRIPT = createToken("SUBSCRIPT")
    val PARAMETER = createToken("PARAMETER")
    val NUMBER = createToken("NUMBER")
    val OPERATOR = createToken("OPERATOR")
    val COMMENT = createToken("COMMENT")
    val TEXT = createToken("TEXT")

    /** The element type of [kind], so the lexer and the highlighter cannot disagree on the mapping. */
    fun of(kind: QuarkdownLatexSyntax.TokenKind): IElementType = when (kind) {
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

    private fun createToken(name: String): IElementType =
        IElementType("QUARKDOWN_LATEX_$name", QuarkdownLatexLanguage.INSTANCE)
}

/**
 * Lexer over [QuarkdownLatexSyntax] for the TeX input of the equation editor.
 *
 * It reuses the tokenizer the annotator uses inside documents, so a formula is colored by exactly
 * the same rules wherever it is shown — there is no second TeX grammar to keep in sync.
 *
 * The lexer holds no state beyond the token it currently sits on: [QuarkdownLatexSyntax.tokenize]
 * scans the whole buffer in one pass, so [getState] is always `0` and
 * [LexerBase.restore] simply re-lexes from the restored offset.
 */
class QuarkdownLatexLexer : LexerBase() {

    private var buffer: CharSequence = ""
    private var startOffset = 0
    private var endOffset = 0
    private var tokens: List<QuarkdownLatexSyntax.Token> = emptyList()
    private var index = 0

    private var tokenStart = 0
    private var tokenEnd = 0
    private var tokenType: IElementType? = null

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        this.buffer = buffer
        this.startOffset = startOffset
        this.endOffset = endOffset
        this.tokens = QuarkdownLatexSyntax.tokenize(buffer.subSequence(startOffset, endOffset))
        index = 0
        locate()
    }

    override fun getState(): Int = 0

    override fun getTokenType(): IElementType? = tokenType

    override fun getTokenStart(): Int = tokenStart

    override fun getTokenEnd(): Int = tokenEnd

    override fun advance() {
        index++
        locate()
    }

    override fun getBufferSequence(): CharSequence = buffer

    override fun getBufferEnd(): Int = endOffset

    /** Points the lexer at `tokens[index]`, or at the end of the buffer when there is none left. */
    private fun locate() {
        val token = tokens.getOrNull(index)
        if (token == null) {
            tokenType = null
            tokenStart = endOffset
            tokenEnd = endOffset
        } else {
            tokenType = QuarkdownLatexTokenTypes.of(token.kind)
            tokenStart = startOffset + token.start
            tokenEnd = startOffset + token.end
        }
    }
}

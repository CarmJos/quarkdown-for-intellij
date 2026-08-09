package cc.carm.plugin.intellij.quarkdown.lang.highlighter

import cc.carm.plugin.intellij.quarkdown.lang.lexer.QuarkdownLexer
import cc.carm.plugin.intellij.quarkdown.lang.lexer.QuarkdownTokenTypes
import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.tree.IElementType

class QuarkdownSyntaxHighlighter : SyntaxHighlighterBase() {

    override fun getHighlightingLexer(): Lexer = QuarkdownLexer()

    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> =
        pack(KEYS[tokenType])

    companion object {

        // ---- Heading ----
        val HEADING_MARKER = TextAttributesKey.createTextAttributesKey(
            "QUARKDOWN_HEADING_MARKER", DefaultLanguageHighlighterColors.KEYWORD
        )
        val HEADING_CONTENT = TextAttributesKey.createTextAttributesKey(
            "QUARKDOWN_HEADING_CONTENT", DefaultLanguageHighlighterColors.FUNCTION_DECLARATION
        )

        // ---- Inline formatting ----
        val BOLD = TextAttributesKey.createTextAttributesKey(
            "QUARKDOWN_BOLD", DefaultLanguageHighlighterColors.IDENTIFIER
        )
        val ITALIC = TextAttributesKey.createTextAttributesKey(
            "QUARKDOWN_ITALIC", DefaultLanguageHighlighterColors.IDENTIFIER
        )
        val STRIKETHROUGH = TextAttributesKey.createTextAttributesKey(
            "QUARKDOWN_STRIKETHROUGH", DefaultLanguageHighlighterColors.METADATA
        )
        val INLINE_CODE = TextAttributesKey.createTextAttributesKey(
            "QUARKDOWN_INLINE_CODE", DefaultLanguageHighlighterColors.DOC_COMMENT_TAG
        )

        // ---- Fenced code blocks ----
        val FENCED_CODE_START = TextAttributesKey.createTextAttributesKey(
            "QUARKDOWN_FENCED_CODE_START", DefaultLanguageHighlighterColors.BLOCK_COMMENT
        )
        val FENCED_CODE_LANGUAGE = TextAttributesKey.createTextAttributesKey(
            "QUARKDOWN_FENCED_CODE_LANG", DefaultLanguageHighlighterColors.DOC_COMMENT_TAG
        )
        val FENCED_CODE_CONTENT = TextAttributesKey.createTextAttributesKey(
            "QUARKDOWN_FENCED_CODE_CONTENT", DefaultLanguageHighlighterColors.BLOCK_COMMENT
        )
        val FENCED_CODE_END = TextAttributesKey.createTextAttributesKey(
            "QUARKDOWN_FENCED_CODE_END", DefaultLanguageHighlighterColors.BLOCK_COMMENT
        )

        // ---- Blockquote ----
        val BLOCKQUOTE = TextAttributesKey.createTextAttributesKey(
            "QUARKDOWN_BLOCKQUOTE", DefaultLanguageHighlighterColors.LINE_COMMENT
        )

        // ---- Lists ----
        val LIST_MARKER = TextAttributesKey.createTextAttributesKey(
            "QUARKDOWN_LIST_MARKER", DefaultLanguageHighlighterColors.METADATA
        )

        // ---- Separator ----
        val SEPARATOR = TextAttributesKey.createTextAttributesKey(
            "QUARKDOWN_SEPARATOR", DefaultLanguageHighlighterColors.METADATA
        )
        val PAGE_BREAK = TextAttributesKey.createTextAttributesKey(
            "QUARKDOWN_PAGE_BREAK", DefaultLanguageHighlighterColors.METADATA
        )

        // ---- Tables ----
        val TABLE_PIPE = TextAttributesKey.createTextAttributesKey(
            "QUARKDOWN_TABLE_PIPE", DefaultLanguageHighlighterColors.KEYWORD
        )
        val TABLE_SEPARATOR = TextAttributesKey.createTextAttributesKey(
            "QUARKDOWN_TABLE_SEPARATOR", DefaultLanguageHighlighterColors.METADATA
        )

        // ---- Images & Links ----
        val IMAGE_PREFIX = TextAttributesKey.createTextAttributesKey(
            "QUARKDOWN_IMAGE_PREFIX", DefaultLanguageHighlighterColors.KEYWORD
        )
        val IMAGE_LABEL = TextAttributesKey.createTextAttributesKey(
            "QUARKDOWN_IMAGE_LABEL", DefaultLanguageHighlighterColors.STRING
        )
        val LINK_TEXT = TextAttributesKey.createTextAttributesKey(
            "QUARKDOWN_LINK_TEXT", DefaultLanguageHighlighterColors.IDENTIFIER
        )
        val LINK_URL = TextAttributesKey.createTextAttributesKey(
            "QUARKDOWN_LINK_URL", DefaultLanguageHighlighterColors.DOC_COMMENT_TAG
        )
        val LINK_TITLE = TextAttributesKey.createTextAttributesKey(
            "QUARKDOWN_LINK_TITLE", DefaultLanguageHighlighterColors.STRING
        )
        val BRACKET = TextAttributesKey.createTextAttributesKey(
            "QUARKDOWN_BRACKET", DefaultLanguageHighlighterColors.KEYWORD
        )
        val PAREN = TextAttributesKey.createTextAttributesKey(
            "QUARKDOWN_PAREN", DefaultLanguageHighlighterColors.KEYWORD
        )
        val BRACE = TextAttributesKey.createTextAttributesKey(
            "QUARKDOWN_BRACE", DefaultLanguageHighlighterColors.KEYWORD
        )

        // ---- Function calls (.xxx { ... }) ----
        val FUNCTION_DOT = TextAttributesKey.createTextAttributesKey(
            "QUARKDOWN_FUNCTION_DOT", DefaultLanguageHighlighterColors.KEYWORD
        )
        val FUNCTION_NAME = TextAttributesKey.createTextAttributesKey(
            "QUARKDOWN_FUNCTION_NAME", DefaultLanguageHighlighterColors.FUNCTION_DECLARATION
        )
        val FUNCTION_PARAMETER_NAME = TextAttributesKey.createTextAttributesKey(
            "QUARKDOWN_FUNCTION_PARAMETER_NAME", DefaultLanguageHighlighterColors.PARAMETER
        )
        val FUNCTION_PARAMETER_COLON = TextAttributesKey.createTextAttributesKey(
            "QUARKDOWN_FUNCTION_PARAMETER_COLON", DefaultLanguageHighlighterColors.PARAMETER
        )
        val FUNCTION_BRACE = TextAttributesKey.createTextAttributesKey(
            "QUARKDOWN_FUNCTION_BRACE", DefaultLanguageHighlighterColors.KEYWORD
        )
        val FUNCTION_PARAMS = TextAttributesKey.createTextAttributesKey(
            "QUARKDOWN_FUNCTION_PARAMS", DefaultLanguageHighlighterColors.PARAMETER
        )

        // ---- Element ID tag {#id} ----
        val ID_TAG = TextAttributesKey.createTextAttributesKey(
            "QUARKDOWN_ID_TAG", DefaultLanguageHighlighterColors.METADATA
        )

        // ---- Comments ----
        val HTML_COMMENT = TextAttributesKey.createTextAttributesKey(
            "QUARKDOWN_HTML_COMMENT", DefaultLanguageHighlighterColors.BLOCK_COMMENT
        )

        // ---- Front matter ----
        val FRONT_MATTER_DELIMITER = TextAttributesKey.createTextAttributesKey(
            "QUARKDOWN_FRONT_MATTER_DELIMITER", DefaultLanguageHighlighterColors.METADATA
        )
        val FRONT_MATTER_CONTENT = TextAttributesKey.createTextAttributesKey(
            "QUARKDOWN_FRONT_MATTER_CONTENT", DefaultLanguageHighlighterColors.METADATA
        )

        // ---- Escape ----
        val ESCAPE = TextAttributesKey.createTextAttributesKey(
            "QUARKDOWN_ESCAPE", DefaultLanguageHighlighterColors.KEYWORD
        )

        // ---- Regular text ----
        @JvmField
        val TEXT = TextAttributesKey.createTextAttributesKey(
            "QUARKDOWN_TEXT", DefaultLanguageHighlighterColors.IDENTIFIER
        )

        // ---- Bad ----
        @JvmField
        val BAD_CHARACTER = TextAttributesKey.createTextAttributesKey(
            "QUARKDOWN_BAD_CHARACTER", DefaultLanguageHighlighterColors.INVALID_STRING_ESCAPE
        )

        private val KEYS: Map<IElementType, TextAttributesKey> = mapOf(
            // Comments
            QuarkdownTokenTypes.HTML_COMMENT to HTML_COMMENT,
            QuarkdownTokenTypes.HTML_COMMENT_CONTENT to HTML_COMMENT,

            // Front matter
            QuarkdownTokenTypes.FRONT_MATTER_DELIMITER to FRONT_MATTER_DELIMITER,
            QuarkdownTokenTypes.FRONT_MATTER_CONTENT to FRONT_MATTER_CONTENT,

            // Headings
            QuarkdownTokenTypes.HEADING_MARKER to HEADING_MARKER,
            QuarkdownTokenTypes.HEADING_CONTENT to HEADING_CONTENT,

            // Inline formatting
            QuarkdownTokenTypes.BOLD_MARKER to BOLD,
            QuarkdownTokenTypes.ITALIC_MARKER to ITALIC,
            QuarkdownTokenTypes.STRIKETHROUGH_MARKER to STRIKETHROUGH,
            QuarkdownTokenTypes.CODE_MARKER to INLINE_CODE,
            QuarkdownTokenTypes.CODE_CONTENT to INLINE_CODE,

            // Fenced code
            QuarkdownTokenTypes.FENCED_CODE_START to FENCED_CODE_START,
            QuarkdownTokenTypes.FENCED_CODE_LANGUAGE to FENCED_CODE_LANGUAGE,
            QuarkdownTokenTypes.FENCED_CODE_CONTENT to FENCED_CODE_CONTENT,
            QuarkdownTokenTypes.FENCED_CODE_END to FENCED_CODE_END,

            // Blockquote & lists
            QuarkdownTokenTypes.BLOCKQUOTE_MARKER to BLOCKQUOTE,
            QuarkdownTokenTypes.LIST_MARKER to LIST_MARKER,

            // Separators
            QuarkdownTokenTypes.SEPARATOR to SEPARATOR,
            QuarkdownTokenTypes.PAGE_BREAK to PAGE_BREAK,

            // Tables
            QuarkdownTokenTypes.TABLE_PIPE to TABLE_PIPE,
            QuarkdownTokenTypes.TABLE_SEPARATOR to TABLE_SEPARATOR,

            // Images & Links
            QuarkdownTokenTypes.IMAGE_PREFIX to IMAGE_PREFIX,
            QuarkdownTokenTypes.IMAGE_LABEL to IMAGE_LABEL,
            QuarkdownTokenTypes.LINK_TEXT to LINK_TEXT,
            QuarkdownTokenTypes.LINK_URL to LINK_URL,
            QuarkdownTokenTypes.LINK_TITLE to LINK_TITLE,
            QuarkdownTokenTypes.BRACKET_OPEN to BRACKET,
            QuarkdownTokenTypes.BRACKET_CLOSE to BRACKET,
            QuarkdownTokenTypes.PAREN_OPEN to PAREN,
            QuarkdownTokenTypes.PAREN_CLOSE to PAREN,
            QuarkdownTokenTypes.BRACE_OPEN to BRACE,
            QuarkdownTokenTypes.BRACE_CLOSE to BRACE,

            // Function calls
            QuarkdownTokenTypes.FUNCTION_DOT to FUNCTION_DOT,
            QuarkdownTokenTypes.FUNCTION_NAME to FUNCTION_NAME,
            QuarkdownTokenTypes.FUNCTION_PARAMETER_NAME to FUNCTION_PARAMETER_NAME,
            QuarkdownTokenTypes.FUNCTION_PARAMETER_COLON to FUNCTION_PARAMETER_COLON,
            QuarkdownTokenTypes.FUNCTION_BRACE_OPEN to FUNCTION_BRACE,
            QuarkdownTokenTypes.FUNCTION_BRACE_CLOSE to FUNCTION_BRACE,
            QuarkdownTokenTypes.FUNCTION_PARAMS to FUNCTION_PARAMS,

            // Element ID tag
            QuarkdownTokenTypes.ID_TAG to ID_TAG,

            // Escape
            QuarkdownTokenTypes.ESCAPE to ESCAPE,

            // Default
            QuarkdownTokenTypes.TEXT to TEXT,
            QuarkdownTokenTypes.BAD_CHARACTER to BAD_CHARACTER,
        )
    }
}

package cc.carm.plugin.intellij.quarkdown.lang.lexer

import com.intellij.lang.Language
import com.intellij.psi.tree.IElementType
import com.intellij.psi.TokenType

object QuarkdownTokenTypes {

    // -------------------------------------------------------------------------
    // Comment / Front Matter
    // -------------------------------------------------------------------------
    val HTML_COMMENT = createToken("HTML_COMMENT")       // <!-- or -->
    val HTML_COMMENT_CONTENT = createToken("HTML_COMMENT_CONTENT")
    val FRONT_MATTER_DELIMITER = createToken("FRONT_MATTER_DELIMITER") // ---
    val FRONT_MATTER_CONTENT = createToken("FRONT_MATTER_CONTENT")

    // -------------------------------------------------------------------------
    // Headings
    // -------------------------------------------------------------------------
    val HEADING_MARKER = createToken("HEADING_MARKER")   // #  ##  ### …
    val HEADING_CONTENT = createToken("HEADING_CONTENT")

    // -------------------------------------------------------------------------
    // Inline formatting
    // -------------------------------------------------------------------------
    val BOLD_MARKER = createToken("BOLD_MARKER")         // ** or __
    val ITALIC_MARKER = createToken("ITALIC_MARKER")     // *  or _
    val STRIKETHROUGH_MARKER = createToken("STRIKETHROUGH_MARKER") // ~~
    val CODE_MARKER = createToken("CODE_MARKER")         // `  or ```
    val CODE_CONTENT = createToken("CODE_CONTENT")

    // -------------------------------------------------------------------------
    // Fenced code blocks
    // -------------------------------------------------------------------------
    val FENCED_CODE_START = createToken("FENCED_CODE_START")       // ``` or ~~~
    val FENCED_CODE_END = createToken("FENCED_CODE_END")
    val FENCED_CODE_LANGUAGE = createToken("FENCED_CODE_LANGUAGE")
    val FENCED_CODE_CONTENT = createToken("FENCED_CODE_CONTENT")

    // -------------------------------------------------------------------------
    // Block quotes & lists
    // -------------------------------------------------------------------------
    val BLOCKQUOTE_MARKER = createToken("BLOCKQUOTE_MARKER")       // >
    val LIST_MARKER = createToken("LIST_MARKER")                  // - * + 1.

    // -------------------------------------------------------------------------
    // Separators
    // -------------------------------------------------------------------------
    val SEPARATOR = createToken("SEPARATOR")              // ---  ***  ___
    val PAGE_BREAK = createToken("PAGE_BREAK")            // <<<

    // -------------------------------------------------------------------------
    // Tables
    // -------------------------------------------------------------------------
    val TABLE_PIPE = createToken("TABLE_PIPE")            // |
    val TABLE_SEPARATOR = createToken("TABLE_SEPARATOR")  // :-:  :---  ---:

    // -------------------------------------------------------------------------
    // Images & Links
    // -------------------------------------------------------------------------
    val IMAGE_PREFIX = createToken("IMAGE_PREFIX")        // !
    val IMAGE_LABEL = createToken("IMAGE_LABEL")          // quoted label inside image syntax
    val LINK_TEXT = createToken("LINK_TEXT")              // content inside []
    val LINK_URL = createToken("LINK_URL")                // content inside ()
    val LINK_TITLE = createToken("LINK_TITLE")            // title inside ""
    val BRACKET_OPEN = createToken("BRACKET_OPEN")        // [
    val BRACKET_CLOSE = createToken("BRACKET_CLOSE")      // ]
    val PAREN_OPEN = createToken("PAREN_OPEN")            // (
    val PAREN_CLOSE = createToken("PAREN_CLOSE")          // )
    val BRACE_OPEN = createToken("BRACE_OPEN")            // {
    val BRACE_CLOSE = createToken("BRACE_CLOSE")          // }
    val LINK_DEFINITION = createToken("LINK_DEFINITION")  // [ref]: url

    // -------------------------------------------------------------------------
    // Quarkdown function calls  .name { params }
    // -------------------------------------------------------------------------
    val FUNCTION_DOT = createToken("FUNCTION_DOT")        // .
    val FUNCTION_NAME = createToken("FUNCTION_NAME")      // doctype, ref, read, …
    val FUNCTION_PARAMS = createToken("FUNCTION_PARAMS")  // content inside {}

    // -------------------------------------------------------------------------
    // Element ID tag  {#id-name}
    // -------------------------------------------------------------------------
    val ID_TAG = createToken("ID_TAG")                    // {#actual-screen-a}

    // -------------------------------------------------------------------------
    // HTML
    // -------------------------------------------------------------------------
    val HTML_TAG = createToken("HTML_TAG")

    // -------------------------------------------------------------------------
    // Escape char
    // -------------------------------------------------------------------------
    val ESCAPE = createToken("ESCAPE")                    // \x

    // -------------------------------------------------------------------------
    // Whitespace / Text
    // -------------------------------------------------------------------------
    val TEXT = createToken("TEXT")
    val NEWLINE = createToken("NEWLINE")

    // Whitespace exposed for the highlighter (may be skipped)
    val BAD_CHARACTER = TokenType.BAD_CHARACTER

    // Single inline-code backtick tokens (used by lexer)
    val INLINE_CODE = createToken("INLINE_CODE")

    // -------------------------------------------------------------------------
    // Token set helpers
    // -------------------------------------------------------------------------
    val FORMATTING_MARKERS: Set<IElementType> = setOf(
        BOLD_MARKER, ITALIC_MARKER, STRIKETHROUGH_MARKER
    )

    val LINK_MARKERS: Set<IElementType> = setOf(
        IMAGE_PREFIX, BRACKET_OPEN, BRACKET_CLOSE,
        PAREN_OPEN, PAREN_CLOSE, LINK_TEXT, LINK_URL, LINK_TITLE
    )

    private fun createToken(name: String): IElementType =
        IElementType(name, cc.carm.plugin.intellij.quarkdown.QuarkdownLanguage.INSTANCE)
}

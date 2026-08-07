package cc.carm.plugin.intellij.quarkdown.lang.lexer

import com.intellij.lexer.LexerBase
import com.intellij.psi.tree.IElementType

/**
 * A robust lexer for Quarkdown (.qd) files.
 *
 * CRITICAL: Every emitted token MUST have length > 0 (tokenEnd > tokenStart).
 * Zero-length tokens cause infinite loops and editor crashes.
 */
class QuarkdownLexer : LexerBase() {

    private var buffer: CharSequence = ""
    private var startOffset = 0
    private var endOffset = 0
    private var tokenStart = 0
    private var tokenEnd = 0
    private var tokenType: IElementType? = null

    /** True when we've emitted <!-- and are scanning for --> */
    private var stateInComment = false
    /** True while we're lexing the same line after an image prefix. */
    private var inImageSyntax = false

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        this.buffer = buffer
        this.startOffset = startOffset
        this.endOffset = endOffset
        this.tokenStart = startOffset
        this.tokenEnd = startOffset
        this.tokenType = null
        this.stateInComment = false
        this.inImageSyntax = false
        // CRITICAL: Must advance to first token so getTokenType() returns valid value.
        // The editor framework calls getTokenType() directly after start() without advance().
        advance()
    }

    override fun advance() {
        tokenStart = tokenEnd
        tokenType = null
        if (tokenStart >= endOffset) return
        tokenType = nextToken()
    }

    override fun getTokenType(): IElementType? = tokenType
    override fun getTokenStart(): Int = tokenStart
    override fun getTokenEnd(): Int = tokenEnd
    override fun getBufferSequence(): CharSequence = buffer
    override fun getBufferEnd(): Int = endOffset
    override fun getState(): Int = 0

    // --------------------------------------------------------------------
    // Helpers
    // --------------------------------------------------------------------

    private fun ch(pos: Int): Char = buffer.getOrElse(pos) { '\u0000' }

    private fun matchAt(pos: Int, s: String): Boolean {
        if (pos + s.length > endOffset) return false
        for (i in s.indices) {
            if (buffer[pos + i] != s[i]) return false
        }
        return true
    }

    /** Emit a token of exactly [len] characters starting at [tokenStart]. */
    private fun emit(type: IElementType, len: Int): IElementType {
        require(len > 0) { "Zero-length token: $type at offset $tokenStart"}
        tokenEnd = tokenStart + len
        return type
    }

    /** Return char at [pos] or null if out of bounds. */
    private fun safe(pos: Int): Char? = if (pos in 0 until endOffset) buffer[pos] else null

    private fun isEol(pos: Int): Boolean {
        val c = safe(pos) ?: return true
        return c == '\n' || c == '\r'
    }

    /** Count of space/tab characters at [pos]. */
    private fun countSpaces(pos: Int): Int {
        var i = 0
        while (pos + i < endOffset && (buffer[pos + i] == ' ' || buffer[pos + i] == '\t')) i++
        return i
    }

    /** Scan forward to next \n or \r. Returns the length from [pos]. */
    private fun scanToEol(pos: Int): Int {
        var i = 0
        while (pos + i < endOffset) {
            val c = buffer[pos + i]
            if (c == '\n' || c == '\r') return i
            i++
        }
        return i
    }

    // --------------------------------------------------------------------
    // Tokenizer
    // --------------------------------------------------------------------

    private fun nextToken(): IElementType {
        val start = tokenStart
        val c = ch(start)

        // -------- Inside HTML comment: emit content until --> --------
        if (stateInComment) {
            if (matchAt(start, "-->")) {
                stateInComment = false
                return emit(QuarkdownTokenTypes.HTML_COMMENT, 3)
            }
            // Scan until --> or end of input
            var len = 0
            while (start + len < endOffset) {
                if (matchAt(start + len, "-->")) break
                len++
            }
            if (len == 0) len = 1 // safety: ensure we always make progress
            return emit(QuarkdownTokenTypes.HTML_COMMENT_CONTENT, len)
        }

        // -------- NEWLINE --------
        if (c == '\n' || c == '\r') {
            inImageSyntax = false
            val len = if (c == '\r' && ch(start + 1) == '\n') 2 else 1
            return emit(QuarkdownTokenTypes.NEWLINE, len)
        }

        // -------- HTML comment <!‑‑ ... ‑‑> --------
        if (matchAt(start, "<!--")) {
            stateInComment = true
            return emit(QuarkdownTokenTypes.HTML_COMMENT, 4)
        }

        // -------- Front matter --- (only when it's the very first thing) --------
        if (start == startOffset && matchAt(start, "---")) {
            val restLen = scanToEol(start + 3)
            if (restLen == 0) {
                // Standalone --- on first line → front matter delimiter
                return emit(QuarkdownTokenTypes.FRONT_MATTER_DELIMITER, 3)
            }
        }

        // -------- Line-beginning patterns --------
        val atLineStart = start == startOffset || ch(start - 1) == '\n' || ch(start - 1) == '\r'

        if (atLineStart) {
            val spaces = countSpaces(start)
            val contentPos = start + spaces

        // -------- Fenced code block start / end (``` or ~~~) --------
            if (contentPos < endOffset) {
                val fc = ch(contentPos)
                if (fc == '`' || fc == '~') {
                    val fenceLen = scanFenceOpen(contentPos, fc)
                    if (fenceLen != null) {
                        val totalLen = contentPos + fenceLen - start
                        return emit(QuarkdownTokenTypes.FENCED_CODE_START, totalLen)
                    }
                }
            }

            // Separator (---, ***, ___)
            if (contentPos < endOffset && matchSeparatorOnly(contentPos)) {
                val eolLen = scanToEol(contentPos)
                val totalLen = contentPos + eolLen - start
                return if (totalLen <= 0) emit(QuarkdownTokenTypes.TEXT, 1) else emit(QuarkdownTokenTypes.SEPARATOR, totalLen)
            }

            // Page break <<<
            if (matchAt(contentPos, "<<<")) {
                return emit(QuarkdownTokenTypes.PAGE_BREAK, contentPos + 3 - start)
            }

            // Heading #
            if (contentPos < endOffset && ch(contentPos) == '#') {
                var hCount = 0
                while (contentPos + hCount < endOffset && ch(contentPos + hCount) == '#') hCount++
                if (hCount in 1..6) {
                    val after = contentPos + hCount
                    if (after >= endOffset || ch(after) == ' ' || ch(after) == '\t') {
                        return emit(QuarkdownTokenTypes.HEADING_MARKER, contentPos + hCount - start)
                    }
                }
            }

            // Blockquote >
            if (contentPos < endOffset && ch(contentPos) == '>') {
                return emit(QuarkdownTokenTypes.BLOCKQUOTE_MARKER, contentPos + 1 - start)
            }

            // List markers: - * + (unordered), 1. 1) (ordered)
            if (contentPos < endOffset) {
                val lc = ch(contentPos)
                if ((lc == '-' || lc == '*' || lc == '+') && safe(contentPos + 1)?.let { it == ' ' || it == '\t' } == true) {
                    return emit(QuarkdownTokenTypes.LIST_MARKER, contentPos + 1 - start)
                }
                if (lc.isDigit()) {
                    var d = 0
                    while (contentPos + d < endOffset && ch(contentPos + d).isDigit()) d++
                    val sep = safe(contentPos + d)
                    if ((sep == '.' || sep == ')') && safe(contentPos + d + 1)?.let { it == ' ' || it == '\t' } == true) {
                        return emit(QuarkdownTokenTypes.LIST_MARKER, contentPos + d + 1 - start)
                    }
                }
            }
        }

        // -------- Inline formatting --------
        if (c == '*' || c == '_') {
            val double = ch(start + 1) == c
            return emit(
                if (double) QuarkdownTokenTypes.BOLD_MARKER else QuarkdownTokenTypes.ITALIC_MARKER,
                if (double) 2 else 1
            )
        }
        if (c == '~' && ch(start + 1) == '~') {
            return emit(QuarkdownTokenTypes.STRIKETHROUGH_MARKER, 2)
        }

        // -------- Inline code `...` --------
        if (c == '`') return lexInlineCode(start)

        // -------- Image ! --------
        if (c == '!') {
            inImageSyntax = true
            return emit(QuarkdownTokenTypes.IMAGE_PREFIX, 1)
        }

        if (inImageSyntax && c == '"') {
            return emit(QuarkdownTokenTypes.IMAGE_LABEL, scanQuotedText(start))
        }

        // -------- Brackets / Parens / Braces --------
        when (c) {
            '[' -> return emit(QuarkdownTokenTypes.BRACKET_OPEN, 1)
            ']' -> return emit(QuarkdownTokenTypes.BRACKET_CLOSE, 1)
            '(' -> return emit(QuarkdownTokenTypes.PAREN_OPEN, 1)
            ')' -> return emit(QuarkdownTokenTypes.PAREN_CLOSE, 1)
            '{' -> {
                // Check if this is an ID tag: {#...}
                if (ch(start + 1) == '#') {
                    // Scan until closing }
                    var len = 2 // '{' + '#'
                    while (start + len < endOffset && ch(start + len) != '}') {
                        len++
                    }
                    if (start + len < endOffset && ch(start + len) == '}') {
                        len++ // include the closing }
                        return emit(QuarkdownTokenTypes.ID_TAG, len)
                    }
                }
                return emit(QuarkdownTokenTypes.BRACE_OPEN, 1)
            }
            '}' -> return emit(QuarkdownTokenTypes.BRACE_CLOSE, 1)
        }

        // -------- Table pipe --------
        if (c == '|') return emit(QuarkdownTokenTypes.TABLE_PIPE, 1)

        // -------- Dot / function call --------
        if (c == '.' && ch(start + 1).isLetter()) {
            return emit(QuarkdownTokenTypes.FUNCTION_DOT, 1)
        }

        // -------- Escape \ --------
        if (c == '\\' && safe(start + 1) != null) {
            return emit(QuarkdownTokenTypes.ESCAPE, 2)
        }

        // -------- Plain text (fallback) --------
        val textLen = scanText(start)
        if (textLen > 0) {
            return emit(QuarkdownTokenTypes.TEXT, textLen)
        }

        // -------- Consume any remaining single character as text --------
        return emit(QuarkdownTokenTypes.TEXT, 1)
    }

    // ---------------------------------------------------------------
    // Specialized scanners
    // ---------------------------------------------------------------

    /**
     * Check if the fence starting at [pos] with character [fc] is a valid
     * code fence opening (at least 3 chars). Returns the count of chars,
     * or null if not a fence.
     */
    private fun scanFenceOpen(pos: Int, fc: Char): Int? {
        var count = 0
        while (pos + count < endOffset && ch(pos + count) == fc) count++
        if (count < 3) return null
        val after = pos + count
        if (after < endOffset && !isEol(after) && ch(after) != ' ' && ch(after) != '\t') return null
        return count
    }

    /** Check if the line at [pos] is ONLY the same separator char (with spaces). */
    private fun matchSeparatorOnly(pos: Int): Boolean {
        val c = ch(pos)
        if (c != '*' && c != '_' && c != '-') return false
        var count = 0
        var i = 0
        while (pos + i < endOffset) {
            val ci = ch(pos + i)
            if (ci == c) { count++; i++; continue }
            if (ci == ' ' || ci == '\t') { i++; continue }
            if (ci == '\n' || ci == '\r') break
            return false
        }
        return count >= 3
    }

    /** Scan inline code backticks. Returns the backtick marker token length. */
    private fun lexInlineCode(start: Int): IElementType {
        var tickCount = 0
        while (start + tickCount < endOffset && ch(start + tickCount) == '`') tickCount++
        return emit(QuarkdownTokenTypes.CODE_MARKER, tickCount)
    }

    /**
     * Scan plain text, stopping at any special character.
     */
    private fun scanText(start: Int): Int {
        var i = 0
        val stop = setOf(
            '\n', '\r', '\\', '`', '*', '_', '~',
            '[', ']', '(', ')', '{', '}',
            '|', '!', '#', '>', '.',
            '-', '+', '"'
        )
        while (start + i < endOffset) {
            val c = ch(start + i)
            if (c in stop) break
            i++
        }
        return i
    }

    private fun scanQuotedText(start: Int): Int {
        var i = 1
        while (start + i < endOffset && ch(start + i) != '"') {
            if (ch(start + i) == '\\' && start + i + 1 < endOffset) i += 2 else i++
        }
        return if (start + i < endOffset) i + 1 else i
    }
}

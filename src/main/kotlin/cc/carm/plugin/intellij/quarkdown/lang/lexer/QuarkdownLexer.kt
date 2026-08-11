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

    /**
     * True between the opening fence (``` / ~~~) and the closing fence of a fenced
     * code block. While set, every line is emitted as [QuarkdownTokenTypes.FENCED_CODE_CONTENT]
     * and a line-start fence closes the block as [QuarkdownTokenTypes.FENCED_CODE_END].
     * Persisted via [getState] / [initialState] so re-lexing inside a block keeps the context.
     */
    private var inFencedCode = false

    /**
     * Lexer state for Quarkdown function calls (e.g. `.pageformat size:{a4}`).
     *
     * Both flags are persisted via [getState] / [initialState] so the platform can
     * re-lex a changed region mid-call without losing the function-call context.
     */
    private var atFunctionName = false
    private var inFunctionCall = false

    /** The lexer state as of the current token's start (returned by [getState]). */
    private var tokenStartState = 0

    /** Tokens still to emit after a multi-token construct (e.g. a function brace block). */
    private val pendingTokens = ArrayDeque<Pair<IElementType, Int>>()

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        this.buffer = buffer
        this.startOffset = startOffset
        this.endOffset = endOffset
        this.tokenStart = startOffset
        this.tokenEnd = startOffset
        this.tokenType = null
        this.stateInComment = false
        this.inImageSyntax = false
        this.pendingTokens.clear()
        this.atFunctionName = initialState and STATE_AT_FUNCTION_NAME != 0
        this.inFunctionCall = initialState and STATE_IN_FUNCTION_CALL != 0
        this.inFencedCode = initialState and STATE_IN_FENCED_CODE != 0
        // CRITICAL: Must advance to first token so getTokenType() returns valid value.
        // The editor framework calls getTokenType() directly after start() without advance().
        advance()
    }

    override fun advance() {
        tokenStart = tokenEnd
        tokenType = null
        if (tokenStart >= endOffset) return
        // Capture the state BEFORE lexing the token at tokenStart: getState() must
        // return a state that reproduces this token when the lexer is restarted here.
        tokenStartState = currentState()
        tokenType = nextToken()
    }

    override fun getTokenType(): IElementType? = tokenType
    override fun getTokenStart(): Int = tokenStart
    override fun getTokenEnd(): Int = tokenEnd
    override fun getBufferSequence(): CharSequence = buffer
    override fun getBufferEnd(): Int = endOffset
    override fun getState(): Int = tokenStartState

    private fun currentState(): Int =
        (if (atFunctionName) STATE_AT_FUNCTION_NAME else 0) or
                (if (inFunctionCall) STATE_IN_FUNCTION_CALL else 0) or
                if (inFencedCode) STATE_IN_FENCED_CODE else 0

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
        require(len > 0) { "Zero-length token: $type at offset $tokenStart" }
        tokenEnd = tokenStart + len
        return type
    }

    /** Return char at [pos] or null if out of bounds. */
    private fun safe(pos: Int): Char? = if (pos in 0 until endOffset) buffer[pos] else null

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
        // -------- Remaining tokens of a multi-token construct --------
        if (pendingTokens.isNotEmpty()) {
            val (type, len) = pendingTokens.removeFirst()
            return emit(type, len)
        }

        val start = tokenStart
        val c = ch(start)

        // -------- Inside a fenced code block (content / closing fence) --------
        if (inFencedCode) {
            lexInsideFencedCode(start, c)?.let { return it }
            // A newline falls through to the NEWLINE handling below.
        }

        // -------- Function name (right after a function-call dot) --------
        if (atFunctionName) {
            if (c.isLetter()) {
                val nameLen = scanIdentifier(start)
                atFunctionName = false
                inFunctionCall = true
                return emit(QuarkdownTokenTypes.FUNCTION_NAME, nameLen)
            }
            // No name follows the dot (e.g. `.` at end of input): drop the expectation.
            atFunctionName = false
        }

        // -------- Inside a function call's argument list --------
        if (inFunctionCall) {
            val argToken = lexFunctionArgument(start)
            if (argToken != null) return argToken
            // The argument list ended; fall through to the normal tokenizer.
        }

        // -------- Inside HTML comment: emit content until --> --------
        if (stateInComment) {
            return lexInsideHtmlComment(start)
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
        lexAtLineStart(start)?.let { return it }

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
            '{' -> return lexBraceOpen(start)
            '}' -> return emit(QuarkdownTokenTypes.BRACE_CLOSE, 1)
        }

        // -------- Table pipe --------
        if (c == '|') return emit(QuarkdownTokenTypes.TABLE_PIPE, 1)

        // -------- Dot / function call --------
        if (c == '.' && isFunctionStartDotAt(start)) {
            atFunctionName = true
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

    /**
     * Lexes the current token while inside a fenced code block. Returns `null` when
     * the character is a newline so the caller falls through to NEWLINE handling.
     */
    private fun lexInsideFencedCode(start: Int, c: Char): IElementType? {
        if (c == '\n' || c == '\r') return null
        val atLineStart = start == startOffset || ch(start - 1) == '\n' || ch(start - 1) == '\r'
        if (atLineStart) {
            val spaces = countSpaces(start)
            val contentPos = start + spaces
            if (contentPos < endOffset) {
                val fc = ch(contentPos)
                if (fc == '`' || fc == '~') {
                    val fenceLen = scanFenceOpen(contentPos, fc)
                    if (fenceLen != null) {
                        inFencedCode = false
                        return emit(QuarkdownTokenTypes.FENCED_CODE_END, contentPos + fenceLen - start)
                    }
                }
            }
        }
        // A content line: the whole line (without the trailing newline).
        val lineLen = scanToEol(start)
        return emit(QuarkdownTokenTypes.FENCED_CODE_CONTENT, if (lineLen > 0) lineLen else 1)
    }

    /** Lexes the current token while inside an HTML comment: content until `-->`. */
    private fun lexInsideHtmlComment(start: Int): IElementType {
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

    /**
     * Lexes line-beginning patterns (fence start, separator, page break, headings,
     * blockquotes, list markers). Returns `null` when the position is not a line
     * start or no pattern applies.
     */
    private fun lexAtLineStart(start: Int): IElementType? {
        val atLineStart = start == startOffset || ch(start - 1) == '\n' || ch(start - 1) == '\r'
        if (!atLineStart) return null

        val spaces = countSpaces(start)
        val contentPos = start + spaces

        // -------- Fenced code block start (``` or ~~~) --------
        if (contentPos < endOffset) {
            val fc = ch(contentPos)
            if (fc == '`' || fc == '~') {
                val fenceLen = scanFenceOpen(contentPos, fc)
                if (fenceLen != null) {
                    inFencedCode = true
                    val totalLen = contentPos + fenceLen - start
                    // Queue the language identifier (the rest of the opening line up to
                    // the first whitespace / quote / brace) so it is emitted right after
                    // the START token, e.g. "python" in ```python "caption" {#id}.
                    val langStart = contentPos + fenceLen
                    var langLen = 0
                    while (langStart + langLen < endOffset) {
                        val lc = ch(langStart + langLen)
                        if (lc == ' ' || lc == '\t' || lc == '\n' || lc == '\r' ||
                            lc == '"' || lc == '\'' || lc == '{'
                        ) break
                        langLen++
                    }
                    if (langLen > 0) {
                        pendingTokens.addLast(QuarkdownTokenTypes.FENCED_CODE_LANGUAGE to langLen)
                    }
                    return emit(QuarkdownTokenTypes.FENCED_CODE_START, totalLen)
                }
            }
        }

        // Separator (---, ***, ___)
        if (contentPos < endOffset && matchSeparatorOnly(contentPos)) {
            val eolLen = scanToEol(contentPos)
            val totalLen = contentPos + eolLen - start
            return if (totalLen <= 0) emit(QuarkdownTokenTypes.TEXT, 1) else emit(
                QuarkdownTokenTypes.SEPARATOR,
                totalLen
            )
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

        return null
    }

    /**
     * Lexes `{` — an element id tag `{#id}` is split into three tokens so the id is
     * its own leaf (like `.ref {id}`'s FUNCTION_PARAMS): `{#` ID_TAG_MARKER + `id`
     * ID_TAG + `}` BRACE_CLOSE. This keeps the Ctrl+hover underline / GTD navigation
     * on just the id, never the whole `{#id}` token.
     * (Function-call braces are handled by lexFunctionArgument while inside a call.)
     */
    private fun lexBraceOpen(start: Int): IElementType {
        if (ch(start + 1) == '#') {
            // Scan until closing }
            var len = 2 // '{' + '#'
            while (start + len < endOffset && ch(start + len) != '}') {
                len++
            }
            if (start + len < endOffset && ch(start + len) == '}') {
                val idLen = len - 2
                if (idLen > 0) {
                    pendingTokens.addLast(QuarkdownTokenTypes.ID_TAG to idLen)
                }
                pendingTokens.addLast(QuarkdownTokenTypes.BRACE_CLOSE to 1)
                return emit(QuarkdownTokenTypes.ID_TAG_MARKER, 2) // "{#"
            }
        }
        return emit(QuarkdownTokenTypes.BRACE_OPEN, 1)
    }

    // ---------------------------------------------------------------
    // Specialized scanners
    // ---------------------------------------------------------------

    /**
     * Check if the fence starting at [pos] with character [fc] is a valid
     * code fence (at least 3 chars). Returns the count of chars, or null if
     * not a fence.
     *
     * A language identifier may follow the fence directly (e.g. ` ```python `),
     * so no whitespace check is applied after the delimiter run — this matches
     * [cc.carm.plugin.intellij.quarkdown.lang.codeblock.QuarkdownCodeBlockSyntax].
     */
    private fun scanFenceOpen(pos: Int, fc: Char): Int? {
        var count = 0
        while (pos + count < endOffset && ch(pos + count) == fc) count++
        if (count < 3) return null
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
            if (ci == c) {
                count++; i++; continue
            }
            if (ci == ' ' || ci == '\t') {
                i++; continue
            }
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

    // ---------------------------------------------------------------
    // Function-call lexing
    // ---------------------------------------------------------------

    /**
     * Lexes one token inside a function call's argument list (after `.name`).
     * Returns `null` when the argument list is over so the caller falls through
     * to the normal tokenizer.
     */
    private fun lexFunctionArgument(start: Int): IElementType? {
        val c = ch(start)

        // A newline ends the call. (`\` continuations keep it alive: the `\`+newline
        // pair is consumed as a single ESCAPE token below, so the newline never
        // reaches this branch.)
        if (c == '\n' || c == '\r') {
            inFunctionCall = false
            return null
        }

        // Whitespace between arguments.
        if (c == ' ' || c == '\t') {
            return emit(QuarkdownTokenTypes.TEXT, countSpaces(start))
        }

        // Positional argument: `{ ... }`.
        if (c == '{') {
            return emitFunctionBraceBlock(start)
        }

        // `::name` chained call segment.
        if (c == ':' && ch(start + 1) == ':' && ch(start + 2).isLetter()) {
            atFunctionName = true
            return emit(QuarkdownTokenTypes.TEXT, 2)
        }

        // The colon of a named parameter (`size:` in `size:{a4}`).
        if (c == ':') {
            return emit(QuarkdownTokenTypes.FUNCTION_PARAMETER_COLON, 1)
        }

        // Named argument: `name:{ ... }`.
        if (c.isLetter()) {
            return lexNamedParameter(start)
        }

        // Line continuation / escape (keeps the call alive across `\` + newline).
        if (c == '\\' && safe(start + 1) != null) {
            return emit(QuarkdownTokenTypes.ESCAPE, 2)
        }

        // Anything else ends the argument list.
        inFunctionCall = false
        return null
    }

    /**
     * Lexes a named argument `name:{ ... }`. When the identifier is not followed by a
     * colon-and-brace, the argument list is over and `null` is returned.
     */
    private fun lexNamedParameter(start: Int): IElementType? {
        val nameLen = scanIdentifier(start)
        var afterName = start + nameLen
        while (afterName < endOffset && (ch(afterName) == ' ' || ch(afterName) == '\t')) afterName++
        if (afterName < endOffset && ch(afterName) == ':') {
            var afterColon = afterName + 1
            while (afterColon < endOffset && (ch(afterColon) == ' ' || ch(afterColon) == '\t')) afterColon++
            if (afterColon < endOffset && ch(afterColon) == '{') {
                return emit(QuarkdownTokenTypes.FUNCTION_PARAMETER_NAME, nameLen)
            }
        }
        // Not a named parameter — the argument list is over.
        inFunctionCall = false
        return null
    }

    /**
     * Emits `{` as FUNCTION_BRACE_OPEN and queues the balanced content as
     * FUNCTION_PARAMS and the closing `}` as FUNCTION_BRACE_CLOSE.
     */
    private fun emitFunctionBraceBlock(start: Int): IElementType {
        val close = findMatchingBrace(start)
        val contentLen = close - (start + 1)
        if (contentLen > 0) {
            pendingTokens.addLast(QuarkdownTokenTypes.FUNCTION_PARAMS to contentLen)
        }
        if (close < endOffset) {
            pendingTokens.addLast(QuarkdownTokenTypes.FUNCTION_BRACE_CLOSE to 1)
        }
        return emit(QuarkdownTokenTypes.FUNCTION_BRACE_OPEN, 1)
    }

    /**
     * Finds the index of the `}` that matches the brace opened at [openIdx],
     * tracking nested braces and skipping quoted strings.
     */
    private fun findMatchingBrace(openIdx: Int): Int {
        var depth = 0
        var quote: Char? = null
        var i = openIdx
        while (i < endOffset) {
            val c = ch(i)
            if (quote != null) {
                if (c == quote) quote = null
                if (c == '\\') i++ // skip escaped char
            } else when (c) {
                '"', '\'' -> quote = c
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
            i++
        }
        return endOffset
    }

    /** Length of the identifier (`[a-zA-Z0-9]*`) starting at [start]. */
    private fun scanIdentifier(start: Int): Int {
        var i = 0
        while (start + i < endOffset && ch(start + i).isLetterOrDigit()) i++
        return i
    }

    /**
     * True when [pos] is a `.` starting a Quarkdown function call: followed by a
     * letter and not preceded by a word character (so `3.14`, `foo.bar`, `..` are excluded).
     */
    private fun isFunctionStartDotAt(pos: Int): Boolean {
        if (ch(pos) != '.') return false
        val next = ch(pos + 1)
        if (!next.isLetter()) return false
        if (pos == 0) return true
        val prev = ch(pos - 1)
        return !prev.isLetterOrDigit() && prev != '_'
    }

    companion object {
        private const val STATE_AT_FUNCTION_NAME = 1
        private const val STATE_IN_FUNCTION_CALL = 2
        private const val STATE_IN_FENCED_CODE = 4
    }
}

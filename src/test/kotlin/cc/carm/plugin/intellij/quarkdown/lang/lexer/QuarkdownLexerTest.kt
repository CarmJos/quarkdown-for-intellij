package cc.carm.plugin.intellij.quarkdown.lang.lexer

import com.intellij.lexer.Lexer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies that Quarkdown function calls are tokenized into distinct tokens so the
 * highlighter can colour the method name, its braces, named parameters and their
 * values independently from plain text.
 */
class QuarkdownLexerTest {

    private fun tokenize(text: String): List<Pair<String, String>> {
        val lexer: Lexer = QuarkdownLexer()
        lexer.start(text, 0, text.length, 0)
        val result = mutableListOf<Pair<String, String>>()
        while (lexer.tokenType != null) {
            result.add(lexer.tokenType!!.toString() to text.substring(lexer.tokenStart, lexer.tokenEnd))
            lexer.advance()
        }
        return result
    }

    private fun types(text: String): List<String> = tokenize(text).map { it.first }

    @Test
    fun `function name and positional arg are split into distinct tokens`() {
        val text = ".ref {intro}"
        assertEquals(
            listOf(
                "FUNCTION_DOT",
                "FUNCTION_NAME",
                "TEXT",
                "FUNCTION_BRACE_OPEN",
                "FUNCTION_PARAMS",
                "FUNCTION_BRACE_CLOSE",
            ),
            types(text)
        )
    }

    @Test
    fun `named parameters get their own tokens`() {
        val text = ".pageformat size:{a4} margin:{2.54cm 3.18cm 2.54cm 3.18cm}"
        val tokens = tokenize(text)
        assertEquals(
            listOf(
                "FUNCTION_DOT",
                "FUNCTION_NAME",
                "TEXT",
                "FUNCTION_PARAMETER_NAME",
                "FUNCTION_PARAMETER_COLON",
                "FUNCTION_BRACE_OPEN",
                "FUNCTION_PARAMS",
                "FUNCTION_BRACE_CLOSE",
                "TEXT",
                "FUNCTION_PARAMETER_NAME",
                "FUNCTION_PARAMETER_COLON",
                "FUNCTION_BRACE_OPEN",
                "FUNCTION_PARAMS",
                "FUNCTION_BRACE_CLOSE",
            ),
            tokens.map { it.first }
        )
        assertEquals("size", tokens[3].second)
        assertEquals("a4", tokens[6].second)
        assertEquals("margin", tokens[9].second)
        assertEquals("2.54cm 3.18cm 2.54cm 3.18cm", tokens[12].second)
    }

    @Test
    fun `nested braces inside an argument stay balanced`() {
        val text = ".row { .col { x } }"
        val tokens = tokenize(text)
        assertEquals(
            listOf(
                "FUNCTION_DOT",
                "FUNCTION_NAME",
                "TEXT",
                "FUNCTION_BRACE_OPEN",
                "FUNCTION_PARAMS",
                "FUNCTION_BRACE_CLOSE",
            ),
            tokens.map { it.first }
        )
        assertEquals(" .col { x } ", tokens[4].second)
    }

    @Test
    fun `prose after a function call returns to plain text`() {
        val text = "See .ref {intro} for details."
        val tokens = tokenize(text)
        assertEquals(
            listOf(
                "TEXT",
                "FUNCTION_DOT",
                "FUNCTION_NAME",
                "TEXT",
                "FUNCTION_BRACE_OPEN",
                "FUNCTION_PARAMS",
                "FUNCTION_BRACE_CLOSE",
                "TEXT",
                "TEXT",
                "TEXT",
            ),
            tokens.map { it.first }
        )
        assertEquals("intro", tokens[5].second)
        assertEquals("for details", tokens[8].second)
    }

    @Test
    fun `restarting mid-call with saved state reproduces the same tokens`() {
        val text = ".pageformat size:{a4}"
        val lexer: Lexer = QuarkdownLexer()
        lexer.start(text, 0, text.length, 0)

        // Advance until the `size` named parameter.
        while (lexer.tokenType.toString() != "FUNCTION_PARAMETER_NAME") lexer.advance()

        val state = lexer.state
        val offset = lexer.tokenStart
        val expectedFromHere = mutableListOf<Pair<String, String>>()
        while (lexer.tokenType != null) {
            expectedFromHere.add(lexer.tokenType!!.toString() to text.substring(lexer.tokenStart, lexer.tokenEnd))
            lexer.advance()
        }

        // Re-lex from the same offset using the saved state.
        val restarted: Lexer = QuarkdownLexer()
        restarted.start(text, offset, text.length, state)
        val actualFromHere = mutableListOf<Pair<String, String>>()
        while (restarted.tokenType != null) {
            actualFromHere.add(
                restarted.tokenType!!.toString() to text.substring(
                    restarted.tokenStart,
                    restarted.tokenEnd
                )
            )
            restarted.advance()
        }

        assertEquals(expectedFromHere, actualFromHere)
    }

    @Test
    fun `plain prose braces are not treated as function braces`() {
        val text = "Here is {a} brace."
        assertEquals(
            listOf("TEXT", "BRACE_OPEN", "TEXT", "BRACE_CLOSE", "TEXT", "TEXT"),
            types(text)
        )
    }

    @Test
    fun `fenced code block emits start language content and end tokens`() {
        val text = "```kotlin\nfun main() {}\n```\n"
        val tokens = tokenize(text)
        assertEquals(
            listOf(
                "FENCED_CODE_START",
                "FENCED_CODE_LANGUAGE",
                "NEWLINE",
                "FENCED_CODE_CONTENT",
                "NEWLINE",
                "FENCED_CODE_END",
                "NEWLINE",
            ),
            tokens.map { it.first }
        )
        assertEquals("```", tokens[0].second)
        assertEquals("kotlin", tokens[1].second)
        assertEquals("fun main() {}", tokens[3].second)
        assertEquals("```", tokens[5].second)
    }

    @Test
    fun `fenced code caption and id are lexed as their own tokens`() {
        val text = "```python \"Fibonacci\" {#fib}\nprint(1)\n```"
        val tokens = tokenize(text)
        assertEquals(
            listOf(
                "FENCED_CODE_START",
                "FENCED_CODE_LANGUAGE",
                "TEXT",
                "CAPTION",
                "TEXT",
                "ID_TAG_MARKER",
                "ID_TAG",
                "BRACE_CLOSE",
                "NEWLINE",
                "FENCED_CODE_CONTENT",
                "NEWLINE",
                "FENCED_CODE_END",
            ),
            tokens.map { it.first }
        )
        assertEquals("python", tokens[1].second)
        assertEquals("Fibonacci", tokens[3].second.removeSurrounding("\""))
        assertEquals("fib", tokens[6].second)
        assertEquals("print(1)", tokens[9].second)
    }

    @Test
    fun `fenced code with only id has no caption token`() {
        val text = "```python {#fib}\nprint(1)\n```"
        val tokens = tokenize(text)
        assertEquals(
            listOf(
                "FENCED_CODE_START",
                "FENCED_CODE_LANGUAGE",
                "TEXT",
                "ID_TAG_MARKER",
                "ID_TAG",
                "BRACE_CLOSE",
                "NEWLINE",
                "FENCED_CODE_CONTENT",
                "NEWLINE",
                "FENCED_CODE_END",
            ),
            tokens.map { it.first }
        )
        assertEquals("fib", tokens[4].second)
    }

    @Test
    fun `table caption line is lexed as caption and id tag`() {
        val text = "\"Some Values\" {#table}\n"
        val tokens = tokenize(text)
        assertEquals(
            listOf(
                "CAPTION",
                "TEXT",
                "ID_TAG_MARKER",
                "ID_TAG",
                "BRACE_CLOSE",
                "NEWLINE",
            ),
            tokens.map { it.first }
        )
        assertEquals("Some Values", tokens[0].second.removeSurrounding("\""))
        assertEquals("table", tokens[3].second)
    }

    @Test
    fun `plain quoted line is not a caption`() {
        // A quoted string followed by more prose is not a table caption line.
        val text = "\"Some Values\" and more text\n"
        val tokens = tokenize(text)
        assertTrue("must not contain CAPTION", tokens.none { it.first == "CAPTION" })
    }
}

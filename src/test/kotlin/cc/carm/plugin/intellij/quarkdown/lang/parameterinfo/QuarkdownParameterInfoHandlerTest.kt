package cc.carm.plugin.intellij.quarkdown.lang.parameterinfo

import cc.carm.plugin.intellij.quarkdown.lang.function.QuarkdownCallParser
import cc.carm.plugin.intellij.quarkdown.lang.lsp.QuarkdownFunctionSignature
import org.junit.Assert.assertEquals
import org.junit.Test

class QuarkdownParameterInfoHandlerTest {

    private val handler = QuarkdownParameterInfoHandler()

    private fun sig(name: String, vararg params: String) =
        QuarkdownFunctionSignature(name, ".$name " + params.joinToString(" ") { "$it:{?}" }, params.toList())

    private fun parse(text: String, caret: Int): QuarkdownCallParser.Call {
        val start = QuarkdownCallParser.findCallStart(text, caret)
        return QuarkdownCallParser.parseCall(text, start)!!
    }

    @Test
    fun `caret inside first positional arg maps to first parameter`() {
        // `.multiply {6} by:{3}` — caret inside `{6}` → parameter 0 (a).
        val text = ".multiply {6} by:{3}"
        val caret = text.indexOf("6")
        val call = parse(text, caret)
        assertEquals(0, handler.parameterIndexAtCaret(call, sig("multiply", "a", "by"), caret))
    }

    @Test
    fun `caret before first arg maps to first parameter`() {
        val text = ".multiply {6} by:{3}"
        val caret = ".multiply ".length
        val call = parse(text, caret)
        assertEquals(0, handler.parameterIndexAtCaret(call, sig("multiply", "a", "by"), caret))
    }

    @Test
    fun `caret inside second positional arg maps to second parameter`() {
        val text = ".multiply {6} {8}"
        val caret = text.indexOf("8")
        val call = parse(text, caret)
        assertEquals(1, handler.parameterIndexAtCaret(call, sig("multiply", "a", "by"), caret))
    }

    @Test
    fun `caret inside named arg maps by name`() {
        val text = ".multiply {6} by:{3}"
        val caret = text.indexOf("3")
        val call = parse(text, caret)
        assertEquals(1, handler.parameterIndexAtCaret(call, sig("multiply", "a", "by"), caret))
    }

    @Test
    fun `caret inside named arg maps to named parameter index when out of order`() {
        val text = ".multiply by:{3} {6}"
        val caret = text.indexOf("3")
        val call = parse(text, caret)
        // named `by` → parameter 1 (even though written first).
        assertEquals(1, handler.parameterIndexAtCaret(call, sig("multiply", "a", "by"), caret))
    }

    @Test
    fun `caret after all args maps to next parameter`() {
        val text = ".multiply {6} by:{3} "
        val caret = text.length
        val call = parse(text, caret)
        // After both args (positional 0 + named by) → parameter index 2 (beyond list → coerce to size).
        assertEquals(2, handler.parameterIndexAtCaret(call, sig("multiply", "a", "by"), caret))
    }

    @Test
    fun `chained call reserves slot zero`() {
        val text = "Number::multiply {6} by:{3}"
        val caret = text.indexOf("6")
        val call = parse(text, caret)
        // Chained: slot 0 is the chained value, so the first explicit positional `{6}` maps to `by` (index 1).
        assertEquals(1, handler.parameterIndexAtCaret(call, sig("multiply", "a", "by"), caret))
    }
}

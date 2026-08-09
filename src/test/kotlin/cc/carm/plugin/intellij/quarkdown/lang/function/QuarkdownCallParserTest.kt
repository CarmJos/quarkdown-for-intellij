package cc.carm.plugin.intellij.quarkdown.lang.function

import org.junit.Assert.*
import org.junit.Test

class QuarkdownCallParserTest {

    @Test
    fun `parses positional arguments`() {
        val text = ".multiply {6} {3}"
        val call = QuarkdownCallParser.parseCall(text, 0)!!
        assertEquals("multiply", call.name)
        assertEquals(2, call.args.size)
        assertEquals(null, call.args[0].paramName)
        assertEquals("6", call.args[0].raw)
        assertEquals("3", call.args[1].raw)
    }

    @Test
    fun `parses named arguments`() {
        val text = ".pagemargin position:{bottomcenter} content:{hello}"
        val call = QuarkdownCallParser.parseCall(text, 0)!!
        assertEquals("pagemargin", call.name)
        assertEquals(2, call.args.size)
        assertTrue(call.args[0].isNamed)
        assertEquals("position", call.args[0].paramName)
        assertEquals("bottomcenter", call.args[0].raw)
        assertEquals("content", call.args[1].paramName)
    }

    @Test
    fun `parses mixed positional and named arguments`() {
        val text = ".multiply {6} by:{3}"
        val call = QuarkdownCallParser.parseCall(text, 0)!!
        assertEquals(2, call.args.size)
        assertFalse(call.args[0].isNamed)
        assertTrue(call.args[1].isNamed)
        assertEquals("by", call.args[1].paramName)
    }

    @Test
    fun `handles nested function calls in arguments`() {
        val text = ".multiply {.pow {3} to:{2}} by:{.pi}"
        val call = QuarkdownCallParser.parseCall(text, 0)!!
        assertEquals(2, call.args.size)
        assertEquals(".pow {3} to:{2}", call.args[0].raw)
        assertEquals(".pi", call.args[1].raw)
    }

    @Test
    fun `handles unterminated braces`() {
        val text = ".pagemargin {bottom"
        val call = QuarkdownCallParser.parseCall(text, 0)!!
        assertEquals(1, call.args.size)
        assertEquals("bottom", call.args[0].raw)
    }

    @Test
    fun `does not treat number dots as function calls`() {
        val text = "3.14 is not a call"
        assertNull(QuarkdownCallParser.parseCall(text, 1))
    }

    @Test
    fun `findCallStart returns nearest call before offset`() {
        val text = ".sum {10} {5}::multiply {2}"
        // cursor inside the multiply value → the `::` chain segment is the nearest start
        val start = QuarkdownCallParser.findCallStart(text, text.length - 1)
        assertEquals(text.indexOf("::"), start)
    }

    @Test
    fun `finds all call starts`() {
        val text = ".doctype {paged}\n\n.sum {1} {2}"
        val starts = QuarkdownCallParser.findAllCallStarts(text)
        assertEquals(listOf(0, 18), starts)
    }

    @Test
    fun `bare colon-colon in prose is not a call start for the annotator`() {
        val text = "Use std::vector and Foo::Bar in prose."
        val starts = QuarkdownCallParser.findAllCallStarts(text)
        assertEquals(emptyList<Int>(), starts)
    }

    @Test
    fun `collects var declarations`() {
        val text = ".var {version} {version-12}\n.include {.version/03.02-hardware.qd}"
        val vars = QuarkdownCallParser.findVarDeclarations(text)
        assertEquals(setOf("version"), vars.keys)
    }

    @Test
    fun `dot on a fresh line does not associate with the previous call`() {
        val text = ".pageformat size:{a4} margin:{1}\n."
        // caret right after the second-line dot
        val start = QuarkdownCallParser.findCallStart(text, text.length)
        // The dot on line 2 is a NEW call, not the previous .pageformat.
        assertEquals(text.indexOf("\n.") + 1, start)
    }

    @Test
    fun `dot on a fresh line after var does not associate with var`() {
        val text = ".var {version} {version-12}\n."
        val start = QuarkdownCallParser.findCallStart(text, text.length)
        assertEquals(text.indexOf("\n.") + 1, start)
    }

    @Test
    fun `dot inside a continuation line associates with the parent call`() {
        val text =
            ".tableofcontents \\\n    title:{**Contents**} maxdepth:{3} \\\n    indexheading:{false} numberheading:{false}"
        // caret inside the last continuation line (at the `indexheading` value)
        val caret = text.indexOf("indexheading") + 3
        val start = QuarkdownCallParser.findCallStart(text, caret)
        // Must find `.tableofcontents`, not any `.` in the continuation content.
        assertEquals(0, start)
    }

    @Test
    fun `isContinuationLine detects backslash-ended lines`() {
        // newline index = length of content before \n
        val t1 = ".tableofcontents \\"
        assertTrue(QuarkdownCallParser.isContinuationLine(t1 + "\n", t1.length))
        val t2 = ".pageformat size:{a4}"
        assertFalse(QuarkdownCallParser.isContinuationLine(t2 + "\n", t2.length))
        // trailing spaces after the backslash still count
        val t3 = ".foo {x} \\  "
        assertTrue(QuarkdownCallParser.isContinuationLine(t3 + "\n", t3.length))
    }

    @Test
    fun `dot inside a same-line call still finds that call`() {
        val text = ".pageformat size:{a4} margin:{1}"
        val caret = text.indexOf("{a4}") + 1 // inside the a4 braces
        val start = QuarkdownCallParser.findCallStart(text, caret)
        assertEquals(0, start)
    }

    @Test
    fun `dot after completed call on same line is a new call`() {
        val text = ".foo {x} .bar"
        val caret = text.indexOf(".bar") + 1
        val start = QuarkdownCallParser.findCallStart(text, caret)
        // `.bar` is its own call start
        assertEquals(text.indexOf(".bar"), start)
    }

    @Test
    fun `collects var declarations with named args`() {
        val text = ".var name:{version} value:{version-12}"
        val vars = QuarkdownCallParser.findVarDeclarations(text)
        assertEquals(setOf("version"), vars.keys)
    }

    @Test
    fun `detects body argument`() {
        val text = ".pagemargin {bottomcenter}\n    .currentpage"
        val call = QuarkdownCallParser.parseCall(text, 0)!!
        assertTrue(call.hasBodyArgument)
    }

    @Test
    fun `no body for unindented following content`() {
        val text = ".pagemargin {bottomcenter}\n.currentpage"
        val call = QuarkdownCallParser.parseCall(text, 0)!!
        assertFalse(call.hasBodyArgument)
    }

    @Test
    fun `no body when the call ends the document`() {
        val text = ".pagemargin {bottomcenter}"
        val call = QuarkdownCallParser.parseCall(text, 0)!!
        assertFalse(call.hasBodyArgument)
    }

    @Test
    fun `does not mark non-chained call as chained`() {
        val text = ".pagemargin position:{bottomcenter}"
        val call = QuarkdownCallParser.parseCall(text, 0)!!
        assertFalse(call.isChained)
        assertNull(call.chainRoot)
    }

    @Test
    fun `parses chained call`() {
        val text = ".pow {3} {2}::subtract {1}"
        // find the subtract call (starts at the `::`)
        val start = QuarkdownCallParser.findCallStart(text, text.length - 1)
        assertEquals(text.indexOf("::"), start)
        val call = QuarkdownCallParser.parseCall(text, start)!!
        assertEquals("subtract", call.name)
        assertTrue(call.isChained)
        assertEquals("pow", call.chainRoot)
    }

    @Test
    fun `dot after brace is a function start (tight calls)`() {
        val text = "H{.text {2} script:{sub}}"
        val dot = QuarkdownCallParser.findCallStart(text, 6)
        assertNotNull(dot)
        assertEquals(2, dot)
    }
}

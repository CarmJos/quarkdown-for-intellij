package cc.carm.plugin.intellij.quarkdown.lang.completion

import org.junit.Assert.*
import org.junit.Test

class FunctionCallTokenizerTest {

    @Test
    fun `detects function name being typed`() {
        val ctx = FunctionCallTokenizer.parseContext(".page", 5)
        assertTrue(ctx.hasCall)
        assertTrue(ctx.inFunctionName)
        assertEquals("page", ctx.namePrefix)
        assertEquals("page", ctx.functionName)
    }

    @Test
    fun `bare dot at start of file triggers function-name completion`() {
        val ctx = FunctionCallTokenizer.parseContext(".", 1)
        assertTrue(ctx.hasCall)
        assertTrue(ctx.inFunctionName)
        assertEquals("", ctx.namePrefix)
    }

    @Test
    fun `dot after newline triggers function-name completion`() {
        val ctx = FunctionCallTokenizer.parseContext(".pageformat {a4}\n.", 20)
        assertTrue(ctx.hasCall)
        assertTrue(ctx.inFunctionName)
        assertEquals("", ctx.namePrefix)
    }

    @Test
    fun `typing cent produces a name-prefix context for center`() {
        val ctx = FunctionCallTokenizer.parseContext(".cent", 5)
        assertTrue(ctx.hasCall)
        assertTrue(ctx.inFunctionName)
        assertEquals("cent", ctx.namePrefix)
        // "center" must be a completion candidate for this prefix.
        assertTrue("center".startsWith(ctx.namePrefix))
    }

    @Test
    fun `completed function name is not in name-completion mode`() {
        val ctx = FunctionCallTokenizer.parseContext(".center ", 8)
        assertTrue(ctx.hasCall)
        assertTrue(!ctx.inFunctionName)
        assertEquals("center", ctx.functionName)
        assertEquals(null, ctx.currentArg)
    }

    @Test
    fun `after a complete function name no arg is current`() {
        val text = ".pagemargin "
        val ctx = FunctionCallTokenizer.parseContext(text, text.length)
        assertTrue(ctx.hasCall)
        assertFalse(ctx.inFunctionName)
        assertEquals("pagemargin", ctx.functionName)
        assertEquals(null, ctx.currentArg)
        assertFalse(ctx.afterNamedColon)
        assertTrue(ctx.writtenArgs.isEmpty())
    }

    @Test
    fun `caret inside positional value braces`() {
        val text = ".pagemargin {bottom"
        val ctx = FunctionCallTokenizer.parseContext(text, text.length)
        assertTrue(ctx.hasCall)
        assertNotNull(ctx.currentArg)
        assertEquals(false, ctx.currentArg!!.isNamed)
        assertEquals("bottom", ctx.valuePrefix)
    }

    @Test
    fun `caret right after a named argument colon`() {
        val text = ".pagemargin position:"
        val ctx = FunctionCallTokenizer.parseContext(text, text.length)
        assertTrue(ctx.hasCall)
        assertTrue(ctx.afterNamedColon)
        assertEquals("position", ctx.pendingNamedParam)
    }

    @Test
    fun `caret inside named value braces`() {
        val text = ".pagemargin position:{bottom"
        val ctx = FunctionCallTokenizer.parseContext(text, text.length)
        assertTrue(ctx.hasCall)
        assertNotNull(ctx.currentArg)
        assertTrue(ctx.currentArg!!.isNamed)
        assertEquals("position", ctx.currentArg.paramName)
        assertEquals("bottom", ctx.valuePrefix)
    }

    @Test
    fun `written arguments are reported after a complete call`() {
        val text = ".pagemargin position:{bottomcenter} "
        val ctx = FunctionCallTokenizer.parseContext(text, text.length)
        assertTrue(ctx.hasCall)
        assertEquals(1, ctx.writtenArgs.size)
        assertEquals("position", ctx.writtenArgs[0].paramName)
        assertEquals(null, ctx.currentArg)
    }

    @Test
    fun `caret right after closing brace is not inside the value`() {
        val text = ".pagemargin position:{bottomcenter}"
        val ctx = FunctionCallTokenizer.parseContext(text, text.length)
        assertTrue(ctx.hasCall)
        assertEquals(null, ctx.currentArg)
        assertEquals(1, ctx.writtenArgs.size)
    }

    @Test
    fun `no false positive for plain prose`() {
        val ctx = FunctionCallTokenizer.parseContext("some text and no call", 15)
        assertFalse(ctx.hasCall)
    }

    @Test
    fun `detects chained call segment`() {
        val text = ".sum {10} {5}::multiply {2}"
        // caret before the closing brace of the last value
        val ctx = FunctionCallTokenizer.parseContext(text, text.lastIndexOf("}"))
        assertTrue(ctx.hasCall)
        assertEquals("multiply", ctx.functionName)
        assertNotNull(ctx.currentArg)
        assertEquals("2", ctx.valuePrefix)
    }

    @Test
    fun `detects function name being typed in a chain`() {
        val text = ".myvar::uppercase"
        val ctx = FunctionCallTokenizer.parseContext(text, text.length)
        assertTrue(ctx.hasCall)
        assertEquals("uppercase", ctx.functionName)
    }

    @Test
    fun `number with dot is not a call`() {
        val ctx = FunctionCallTokenizer.parseContext("value is 3.14 here", 14)
        assertFalse(ctx.hasCall)
    }
}

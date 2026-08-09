package cc.carm.plugin.intellij.quarkdown.lang.codeblock

import org.junit.Assert.*
import org.junit.Test

class QuarkdownCodeBlockSyntaxTest {

    // ------------------------------------------------------------------
    // Fenced code block parsing
    // ------------------------------------------------------------------

    @Test
    fun `parses fenced line with language caption and id`() {
        val info = QuarkdownCodeBlockSyntax.parseFenceLine("```python \"Fibonacci function\" {#fibonacci}")!!
        assertEquals("", info.indent)
        assertEquals("```", info.fence)
        assertEquals("python", info.language)
        assertEquals("Fibonacci function", info.caption)
        assertEquals("fibonacci", info.id)
    }

    @Test
    fun `parses fenced line with id only`() {
        val info = QuarkdownCodeBlockSyntax.parseFenceLine("```kotlin {#main}")!!
        assertEquals("kotlin", info.language)
        assertEquals("", info.caption)
        assertEquals("main", info.id)
    }

    @Test
    fun `parses bare fence`() {
        val info = QuarkdownCodeBlockSyntax.parseFenceLine("```")!!
        assertEquals("", info.language)
        assertEquals("", info.caption)
        assertEquals("", info.id)
    }

    @Test
    fun `parses indented tilde fence with caption`() {
        val info = QuarkdownCodeBlockSyntax.parseFenceLine("    ~~~javascript 'hello world'")!!
        assertEquals("    ", info.indent)
        assertEquals("~~~", info.fence)
        assertEquals("javascript", info.language)
        assertEquals("hello world", info.caption)
    }

    @Test
    fun `rejects non-fence lines`() {
        assertNull(QuarkdownCodeBlockSyntax.parseFenceLine("`inline code`"))
        assertNull(QuarkdownCodeBlockSyntax.parseFenceLine("| table | row |"))
        assertNull(QuarkdownCodeBlockSyntax.parseFenceLine("## heading"))
        assertNull(QuarkdownCodeBlockSyntax.parseFenceLine("`` two backticks"))
    }

    // ------------------------------------------------------------------
    // Fenced code block building
    // ------------------------------------------------------------------

    @Test
    fun `builds fenced line from parsed info`() {
        val original = "```python \"Fibonacci function\" {#fibonacci}"
        val built = QuarkdownCodeBlockSyntax.buildFenceLine(original, "kotlin", "Hello World", "main")
        assertEquals("```kotlin \"Hello World\" {#main}", built)
    }

    @Test
    fun `builds fenced line omitting empty values`() {
        val original = "```python \"Fibonacci function\" {#fibonacci}"
        val built = QuarkdownCodeBlockSyntax.buildFenceLine(original, "", "", "")
        assertEquals("```", built)
    }

    @Test
    fun `builds fenced line preserving indent and tilde fence`() {
        val original = "    ~~~javascript 'hello world'"
        val built = QuarkdownCodeBlockSyntax.buildFenceLine(original, "python", "changed", "")
        assertEquals("    ~~~python \"changed\"", built)
    }

    // ------------------------------------------------------------------
    // `.code` function block parsing
    // ------------------------------------------------------------------

    @Test
    fun `parses code function line with lang caption and ref`() {
        val info =
            QuarkdownCodeBlockSyntax.parseCodeFunctionLine(".code lang:{python} caption:{Fibonacci function} ref:{example}")!!
        assertEquals("", info.indent)
        assertEquals("python", info.language)
        assertEquals("Fibonacci function", info.caption)
        assertEquals("example", info.id)
    }

    @Test
    fun `parses code function line with only lang`() {
        val info = QuarkdownCodeBlockSyntax.parseCodeFunctionLine(".code lang:{javascript}")!!
        assertEquals("javascript", info.language)
        assertEquals("", info.caption)
        assertEquals("", info.id)
    }

    @Test
    fun `parses indented code function line with extra args`() {
        val info = QuarkdownCodeBlockSyntax.parseCodeFunctionLine("    .code lang:{python} linenumbers:{no}")!!
        assertEquals("    ", info.indent)
        assertEquals("python", info.language)
    }

    @Test
    fun `rejects non code function lines`() {
        assertNull(QuarkdownCodeBlockSyntax.parseCodeFunctionLine(".codespan {hello}"))
        assertNull(QuarkdownCodeBlockSyntax.parseCodeFunctionLine("text .code lang:{python}"))
        assertNull(QuarkdownCodeBlockSyntax.parseCodeFunctionLine(".notcode lang:{python}"))
        assertNull(QuarkdownCodeBlockSyntax.parseCodeFunctionLine("```python"))
    }

    // ------------------------------------------------------------------
    // `.code` function block building
    // ------------------------------------------------------------------

    @Test
    fun `builds code function line updating lang caption and ref`() {
        val original = ".code lang:{python} caption:{Fibonacci function} ref:{example}"
        val built = QuarkdownCodeBlockSyntax.buildCodeFunctionLine(original, "kotlin", "Hello", "main")
        assertEquals(".code lang:{kotlin} caption:{Hello} ref:{main}", built)
    }

    @Test
    fun `builds code function line removing empty values`() {
        val original = ".code lang:{python} caption:{Fibonacci function} ref:{example}"
        val built = QuarkdownCodeBlockSyntax.buildCodeFunctionLine(original, "", "", "")
        assertEquals(".code", built)
    }

    @Test
    fun `builds code function line appending missing args`() {
        val original = ".code lang:{python}"
        val built = QuarkdownCodeBlockSyntax.buildCodeFunctionLine(original, "python", "Hello", "main")
        assertEquals(".code lang:{python} caption:{Hello} ref:{main}", built)
    }

    @Test
    fun `builds code function line preserving other args and position`() {
        val original = ".code {assets/point.ts} lang:{python} linenumbers:{no} focus:{5..8}"
        val built = QuarkdownCodeBlockSyntax.buildCodeFunctionLine(original, "kotlin", "Caption", "my-id")
        assertEquals(
            ".code {assets/point.ts} lang:{kotlin} linenumbers:{no} focus:{5..8} caption:{Caption} ref:{my-id}",
            built
        )
    }

    @Test
    fun `builds code function line preserving indent`() {
        val original = "    .code lang:{python}"
        val built = QuarkdownCodeBlockSyntax.buildCodeFunctionLine(original, "python", "", "id")
        assertEquals("    .code lang:{python} ref:{id}", built)
    }

    // ------------------------------------------------------------------
    // Fence open-offset detection
    // ------------------------------------------------------------------

    @Test
    fun `finds opening fences only`() {
        val text = "```python\nprint(1)\n```\n\n```java\nSystem.out\n```\n"
        val offsets = QuarkdownCodeBlockSyntax.findFenceOpenOffsets(text)
        assertEquals(2, offsets.size)
        assertEquals(setOf(0, 24), offsets)
    }

    @Test
    fun `does not treat closing fence as opening`() {
        val text = "```python\nprint(1)\n```\n"
        val offsets = QuarkdownCodeBlockSyntax.findFenceOpenOffsets(text)
        assertEquals(setOf(0), offsets)
    }

    @Test
    fun `finds opening fences ignoring inline fences`() {
        val text = "text `inline` more\n```python\nx\n```\n"
        val offsets = QuarkdownCodeBlockSyntax.findFenceOpenOffsets(text)
        assertEquals(setOf(19), offsets)
    }

    @Test
    fun `matches tilde fences`() {
        val text = "~~~python\nx\n~~~\n"
        val offsets = QuarkdownCodeBlockSyntax.findFenceOpenOffsets(text)
        assertEquals(setOf(0), offsets)
    }

    @Test
    fun `no offsets when no code blocks`() {
        assertTrue(QuarkdownCodeBlockSyntax.findFenceOpenOffsets("plain text\nmore text\n").isEmpty())
        assertFalse(QuarkdownCodeBlockSyntax.findFenceOpenOffsets("plain text\nmore text\n").isNotEmpty())
    }
}

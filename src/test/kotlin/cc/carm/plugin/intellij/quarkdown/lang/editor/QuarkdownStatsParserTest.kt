package cc.carm.plugin.intellij.quarkdown.lang.editor

import cc.carm.plugin.intellij.quarkdown.lang.editor.QuarkdownStatsParser.QuarkdownStats
import org.junit.Assert.assertEquals
import org.junit.Test

class QuarkdownStatsParserTest {

    private fun stats(text: String): QuarkdownStats = QuarkdownStatsParser.computeStats(text)

    @Test
    fun `empty document has zero counts`() {
        assertEquals(QuarkdownStats(0, 0), stats(""))
    }

    @Test
    fun `plain text paragraph`() {
        assertEquals(QuarkdownStats(5, 1), stats("The quick brown fox jumps."))
    }

    @Test
    fun `two paragraphs separated by blank line`() {
        assertEquals(QuarkdownStats(4, 2), stats("One two\n\nThree four"))
    }

    @Test
    fun `heading counts as paragraph and words`() {
        assertEquals(QuarkdownStats(3, 1), stats("# Hello world here"))
    }

    @Test
    fun `list items form one paragraph`() {
        assertEquals(QuarkdownStats(3, 1), stats("- apple\n- banana\n- cherry"))
    }

    @Test
    fun `function call line is excluded entirely`() {
        val text = ".var {version} {version-12}\n\nHello world"
        assertEquals(QuarkdownStats(2, 1), stats(text))
    }

    @Test
    fun `read and include calls are excluded`() {
        val text = ".include {docs/intro.qd}\n.read {data.csv}\n\nActual prose here."
        assertEquals(QuarkdownStats(3, 1), stats(text))
    }

    @Test
    fun `function call separates paragraphs`() {
        val text = "First paragraph\n.include {x.qd}\nSecond paragraph"
        assertEquals(QuarkdownStats(4, 2), stats(text))
    }

    @Test
    fun `function with indented body is excluded`() {
        val text = """
            .center {
                This is centered content
                Second centered line
            }
            Outer paragraph text
        """.trimIndent()
        assertEquals(QuarkdownStats(3, 1), stats(text))
    }

    @Test
    fun `chained call is excluded`() {
        val text = ".sum {10} {5}::multiply {2}\n\nReal content words"
        assertEquals(QuarkdownStats(3, 1), stats(text))
    }

    @Test
    fun `inline formatting stripped from words`() {
        assertEquals(QuarkdownStats(4, 1), stats("**bold** and *italic* text"))
    }

    @Test
    fun `link text kept but url excluded`() {
        assertEquals(QuarkdownStats(2, 1), stats("[click here](https://example.com)"))
    }

    @Test
    fun `image alt text excluded`() {
        assertEquals(QuarkdownStats(0, 0), stats("![logo](assets/logo.png)"))
    }

    @Test
    fun `table rows count words and stay one paragraph`() {
        val text = "| Name | Value |\n| --- | --- |\n| Alpha | 1 |\n| Beta | 2 |"
        assertEquals(QuarkdownStats(6, 1), stats(text))
    }

    @Test
    fun `fenced code block excluded from words`() {
        val text = "Before\n```kotlin\nfun main() = println(\"hi\")\n```\nAfter"
        assertEquals(QuarkdownStats(2, 2), stats(text))
    }

    @Test
    fun `blockquote text counts`() {
        assertEquals(QuarkdownStats(3, 1), stats("> quoted wisdom here"))
    }

    @Test
    fun `separator line ends a paragraph`() {
        val text = "Top section\n---\nBottom section"
        assertEquals(QuarkdownStats(4, 2), stats(text))
    }

    @Test
    fun `html comment line is ignored`() {
        val text = "Real text\n<!-- a comment -->\nMore text"
        assertEquals(QuarkdownStats(4, 2), stats(text))
    }

    @Test
    fun `cjk text counts as words`() {
        assertEquals(QuarkdownStats(2, 1), stats("你好世界 测试"))
    }

    @Test
    fun `function call in the middle of a line keeps surrounding text`() {
        assertEquals(QuarkdownStats(2, 1), stats("before .center {x} after"))
    }
}

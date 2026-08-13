package cc.carm.plugin.intellij.quarkdown.statusbar

import cc.carm.plugin.intellij.quarkdown.statusbar.QuarkdownStatsParser.Stats
import org.junit.Assert.assertEquals
import org.junit.Test

class QuarkdownStatsParserTest {

    private fun stats(text: String): Stats = QuarkdownStatsParser.computeStats(text)

    @Test
    fun `empty document has zero counts`() {
        assertEquals(Stats(0, 0), stats(""))
    }

    @Test
    fun `plain text paragraph`() {
        assertEquals(Stats(5, 1), stats("The quick brown fox jumps."))
    }

    @Test
    fun `two paragraphs separated by blank line`() {
        assertEquals(Stats(4, 2), stats("One two\n\nThree four"))
    }

    @Test
    fun `heading counts as paragraph and words`() {
        assertEquals(Stats(3, 1), stats("# Hello world here"))
    }

    @Test
    fun `list items form one paragraph`() {
        assertEquals(Stats(3, 1), stats("- apple\n- banana\n- cherry"))
    }

    @Test
    fun `function call line is excluded entirely`() {
        val text = ".var {version} {version-12}\n\nHello world"
        assertEquals(Stats(2, 1), stats(text))
    }

    @Test
    fun `read and include calls are excluded`() {
        val text = ".include {docs/intro.qd}\n.read {data.csv}\n\nActual prose here."
        assertEquals(Stats(3, 1), stats(text))
    }

    @Test
    fun `function call separates paragraphs`() {
        val text = "First paragraph\n.include {x.qd}\nSecond paragraph"
        assertEquals(Stats(4, 2), stats(text))
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
        assertEquals(Stats(3, 1), stats(text))
    }

    @Test
    fun `chained call is excluded`() {
        val text = ".sum {10} {5}::multiply {2}\n\nReal content words"
        assertEquals(Stats(3, 1), stats(text))
    }

    @Test
    fun `inline formatting stripped from words`() {
        assertEquals(Stats(4, 1), stats("**bold** and *italic* text"))
    }

    @Test
    fun `link text kept but url excluded`() {
        assertEquals(Stats(2, 1), stats("[click here](https://example.com)"))
    }

    @Test
    fun `image alt text excluded`() {
        assertEquals(Stats(0, 0), stats("![logo](assets/logo.png)"))
    }

    @Test
    fun `table rows count words and stay one paragraph`() {
        val text = "| Name | Value |\n| --- | --- |\n| Alpha | 1 |\n| Beta | 2 |"
        assertEquals(Stats(6, 1), stats(text))
    }

    @Test
    fun `fenced code block excluded from words`() {
        val text = "Before\n```kotlin\nfun main() = println(\"hi\")\n```\nAfter"
        assertEquals(Stats(2, 2), stats(text))
    }

    @Test
    fun `blockquote text counts`() {
        assertEquals(Stats(3, 1), stats("> quoted wisdom here"))
    }

    @Test
    fun `separator line ends a paragraph`() {
        val text = "Top section\n---\nBottom section"
        assertEquals(Stats(4, 2), stats(text))
    }

    @Test
    fun `html comment line is ignored`() {
        val text = "Real text\n<!-- a comment -->\nMore text"
        assertEquals(Stats(4, 2), stats(text))
    }

    @Test
    fun `cjk text counts each character as a word`() {
        // Each CJK ideograph counts individually: 你好世界 = 4, 测试 = 2.
        assertEquals(Stats(6, 1, 6), stats("你好世界 测试"))
    }

    @Test
    fun `mixed cjk and latin text counts separately`() {
        // "Hello" = 1 word + 5 latin letters, "你好" = 2 CJK chars/words, "world" = 1.
        assertEquals(Stats(4, 1, 2), stats("Hello 你好 world"))
    }

    @Test
    fun `function call in the middle of a line keeps surrounding text`() {
        assertEquals(Stats(2, 1), stats("before .center {x} after"))
    }
}

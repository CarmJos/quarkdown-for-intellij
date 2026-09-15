package cc.carm.plugin.intellij.quarkdown.lang.table

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Platform-level tests for [QuarkdownTableLineMarkerProvider]: the table gutter icon must
 * appear on the first line of every table the plugin can edit, whatever the separator row
 * looks like.
 */
class QuarkdownTableLineMarkerProviderTest : BasePlatformTestCase() {

    /** The 1-based numbers of the lines that get a table gutter marker in [text]. */
    private fun markerLines(text: String): List<Int> {
        myFixture.configureByText("test.qd", text)
        val provider = QuarkdownTableLineMarkerProvider()
        val lines = mutableListOf<Int>()
        myFixture.file.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (provider.getLineMarkerInfo(element) != null) {
                    val offset = element.textRange.startOffset
                    lines += text.substring(0, offset).count { it == '\n' } + 1
                }
                super.visitElement(element)
            }
        })
        return lines
    }

    fun `test a table with a single-dash separator cell gets a gutter marker`() {
        val text = "Intro\n" +
                "| 2025.3.20 | > | 2025.6.21 | > |\n" +
                "| :-------: | ---: | :-------: | -: |\n" +
                "| 1:30-1:40 | 4.20 | 0:20-0:30 | 8.72 |\n" +
                "\"caption\" {#tab-1}\n"
        assertEquals("the table must be recognized: $text", listOf(2), markerLines(text))
    }

    fun `test every table of a document gets its own gutter marker`() {
        val text = "## Section\n" +
                "\n" +
                "| A | B |\n" +
                "|---|---|\n" +
                "| a | b |\n" +
                "\n" +
                "Prose.\n" +
                "\n" +
                "| C | D |\n" +
                "| - | -: |\n" +
                "| c | d |\n"
        assertEquals(listOf(3, 9), markerLines(text))
    }

    fun `test a table separator without dashes is not a table`() {
        val text = "| a | b |\n" +
                "| c | d |\n"
        assertEquals(emptyList<Int>(), markerLines(text))
    }
}

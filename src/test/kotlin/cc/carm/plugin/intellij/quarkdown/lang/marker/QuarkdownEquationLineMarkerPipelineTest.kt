package cc.carm.plugin.intellij.quarkdown.lang.marker

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Checks that the equation gutter icon is produced for every LaTeX form, through the real
 * marker pipeline. A missing icon is what makes an equation look "unrecognized" in the IDE.
 */
class QuarkdownEquationLineMarkerPipelineTest : BasePlatformTestCase() {

    private fun markers(text: String): List<Int> {
        myFixture.configureByText("test.qd", text)
        val provider = QuarkdownEquationLineMarkerProvider()
        val elements = myFixture.file.collectElements()
        val result = mutableListOf<LineMarkerInfo<*>>()
        provider.collectSlowLineMarkers(elements, result)
        println("MARKERS for [${text.replace("\n", "\\n")}]: " + result.map { "${it.startOffset} icon=${it.icon}" })
        return result.map { it.startOffset }
    }

    private fun com.intellij.psi.PsiElement.collectElements(): MutableList<com.intellij.psi.PsiElement> {
        val all = mutableListOf<com.intellij.psi.PsiElement>()
        fun walk(element: com.intellij.psi.PsiElement) {
            all.add(element)
            var child = element.firstChild
            while (child != null) {
                walk(child)
                child = child.nextSibling
            }
        }
        walk(this)
        return all
    }

    fun `test inline equation gets a gutter icon`() {
        val offsets = markers("Let \$ x = 1 \$ be it.\n")
        assertEquals("expected one gutter icon", 1, offsets.size)
    }

    fun `test fenced equation gets a gutter icon on its opening line`() {
        val text = "\$\$\$\nx = 1\n\$\$\$\n"
        val offsets = markers(text)
        assertEquals("expected one gutter icon", 1, offsets.size)
        assertEquals("the icon belongs on the opening fence line", 0, offsets.first())
    }

    fun `test math call gets a gutter icon`() {
        val offsets = markers(".math {2 + 2}\n")
        assertEquals("expected one gutter icon", 1, offsets.size)
    }

    fun `test math block body gets a gutter icon`() {
        val text = ".math\n    a^2 + b^2 = c^2\n"
        val offsets = markers(text)
        assertEquals("expected one gutter icon", 1, offsets.size)
    }

    fun `test texmacro gets one gutter icon for name and body`() {
        val text = ".texmacro {\\gradient} {\\nabla}\n"
        val offsets = markers(text)
        assertEquals("one icon per line, not per region", 1, offsets.size)
    }

    fun `test two equations on one line share a single gutter icon`() {
        val text = "a \$ x \$ b \$ y \$ c\n"
        val offsets = markers(text)
        assertEquals("one icon for the line", 1, offsets.size)
    }
}


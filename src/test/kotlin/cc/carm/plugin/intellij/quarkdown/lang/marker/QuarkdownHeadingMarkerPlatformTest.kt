package cc.carm.plugin.intellij.quarkdown.lang.marker

import cc.carm.plugin.intellij.quarkdown.QuarkdownLanguage
import cc.carm.plugin.intellij.quarkdown.action.heading.HeadingDialog
import cc.carm.plugin.intellij.quarkdown.lang.heading.QuarkdownHeadingSyntax
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviders
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Verifies the heading gutter marker is registered on the platform for the Quarkdown
 * language and that it is produced for every heading level.
 */
class QuarkdownHeadingMarkerPlatformTest : BasePlatformTestCase() {

    fun `test heading line marker registered for quarkdown`() {
        val providers = LineMarkerProviders.getInstance()
            .allForLanguage(QuarkdownLanguage.INSTANCE)
        assertTrue(
            "QuarkdownHeadingLineMarkerProvider should be registered",
            providers.any { it is QuarkdownHeadingLineMarkerProvider }
        )
    }

    fun `test one marker per heading line`() {
        myFixture.configureByText(
            "c.qd",
            "# Title\n" +
                    "## Section {#sec}\n" +
                    "### Sub\n" +
                    "plain text\n"
        )
        val provider = QuarkdownHeadingLineMarkerProvider()
        val markers = collectMarkers(provider)
        assertEquals("one marker per heading", 3, markers.size)
    }

    fun `test no marker for non-heading lines`() {
        myFixture.configureByText(
            "c.qd",
            "plain text\n" +
                    "#NoSpace\n" +
                    "```\ncode\n```\n"
        )
        val provider = QuarkdownHeadingLineMarkerProvider()
        val markers = collectMarkers(provider)
        assertEquals("no marker for non-headings", 0, markers.size)
    }

    fun `test dialog pre-fills level content and id`() {
        val info = QuarkdownHeadingSyntax.parseHeadingLine("## Section {#sec}")!!
        val dialog = HeadingDialog(project)
        dialog.parseHeading(info)
        assertEquals(2, dialog.getLevelForTest())
        assertEquals("Section", dialog.getContentForTest())
        assertEquals("sec", dialog.getIdForTest())
    }

    fun `test dialog builds the heading line`() {
        val info = QuarkdownHeadingSyntax.parseHeadingLine("# Title")!!
        val dialog = HeadingDialog(project)
        dialog.parseHeading(info)
        dialog.setLevelForTest(3)
        dialog.setContentForTest("Changed")
        dialog.setIdForTest("new-id")
        assertEquals("### Changed {#new-id}", dialog.buildLine())
    }

    fun `test extract button fills id from content`() {
        val info = QuarkdownHeadingSyntax.parseHeadingLine("# Hello, World!")!!
        val dialog = HeadingDialog(project)
        dialog.parseHeading(info)
        assertEquals("", dialog.getIdForTest())
        dialog.clickExtractForTest()
        assertEquals("hello-world", dialog.getIdForTest())
        assertEquals("# Hello, World! {#hello-world}", dialog.buildLine())
    }

    private fun collectMarkers(provider: QuarkdownHeadingLineMarkerProvider): List<LineMarkerInfo<*>> {
        val result = mutableListOf<LineMarkerInfo<*>>()
        val elements = PsiTreeUtil.collectElements(myFixture.file) { true }.toMutableList()
        for (element in elements) {
            provider.getLineMarkerInfo(element)?.let { result.add(it) }
        }
        return result
    }
}


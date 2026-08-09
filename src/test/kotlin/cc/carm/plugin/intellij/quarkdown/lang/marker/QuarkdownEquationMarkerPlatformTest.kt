package cc.carm.plugin.intellij.quarkdown.lang.marker

import cc.carm.plugin.intellij.quarkdown.QuarkdownLanguage
import cc.carm.plugin.intellij.quarkdown.action.equation.EquationDialog
import cc.carm.plugin.intellij.quarkdown.lang.equation.QuarkdownEquationSyntax
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviders
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Verifies the equation gutter marker is registered on the platform for the Quarkdown
 * language and that it is produced for both inline `$...$` and fenced `$$$` equations.
 */
class QuarkdownEquationMarkerPlatformTest : BasePlatformTestCase() {

    fun `test equation line marker registered for quarkdown`() {
        val providers = LineMarkerProviders.getInstance()
            .allForLanguage(QuarkdownLanguage.INSTANCE)
        assertTrue(
            "QuarkdownEquationLineMarkerProvider should be registered",
            providers.any { it is QuarkdownEquationLineMarkerProvider }
        )
    }

    fun `test markers for inline and fenced equations`() {
        myFixture.configureByText(
            "c.qd",
            "$ E = mc^2 $ {#energy}\n" +
                    "\n" +
                    "$$$\n" +
                    "E = mc^2\n" +
                    "$$$\n"
        )
        val elements = PsiTreeUtil.collectElements(myFixture.file) { true }.toMutableList()
        val result = mutableListOf<LineMarkerInfo<*>>()
        QuarkdownEquationLineMarkerProvider().collectSlowLineMarkers(elements, result)
        assertEquals("one marker per equation", 2, result.size)
    }

    fun `test no marker for prose inline math`() {
        myFixture.configureByText(
            "c.qd",
            "Einstein's formula \$E=mc^2\$ is famous.\n"
        )
        val elements = PsiTreeUtil.collectElements(myFixture.file) { true }.toMutableList()
        val result = mutableListOf<LineMarkerInfo<*>>()
        QuarkdownEquationLineMarkerProvider().collectSlowLineMarkers(elements, result)
        assertEquals("prose inline math is not an equation line", 0, result.size)
    }

    fun `test only the opening fence gets a marker`() {
        myFixture.configureByText(
            "c.qd",
            "$$$\nE = mc^2\n$$$\n"
        )
        val elements = PsiTreeUtil.collectElements(myFixture.file) { true }.toMutableList()
        val result = mutableListOf<LineMarkerInfo<*>>()
        QuarkdownEquationLineMarkerProvider().collectSlowLineMarkers(elements, result)
        assertEquals("only the opening fence gets a marker", 1, result.size)
    }

    fun `test dialog pre-fills id and builds the inline line`() {
        val info = QuarkdownEquationSyntax.parseInlineEquationLine("$ E = mc^2 $ {#energy}")!!
        val dialog = EquationDialog(project, QuarkdownEquationSyntax.Kind.INLINE)
        dialog.parseInline(info)
        assertEquals("energy", dialog.getIdForTest())
        dialog.setIdForTest("mass")
        assertEquals("$ E = mc^2 $ {#mass}", dialog.buildLine())
    }

    fun `test dialog removes id from fenced line`() {
        val info = QuarkdownEquationSyntax.parseFenceEquationLine("$$$ {#energy}")!!
        val dialog = EquationDialog(project, QuarkdownEquationSyntax.Kind.FENCED)
        dialog.parseFence(info)
        assertEquals("energy", dialog.getIdForTest())
        dialog.setIdForTest("")
        assertEquals("$$$", dialog.buildLine())
    }
}

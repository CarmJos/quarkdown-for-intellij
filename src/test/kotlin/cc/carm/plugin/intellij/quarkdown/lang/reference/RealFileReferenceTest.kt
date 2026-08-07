package cc.carm.plugin.intellij.quarkdown.lang.reference

import cc.carm.plugin.intellij.quarkdown.lang.function.QuarkdownCallParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Verifies the var/ref anchor computation against a real-world `.qd` document
 * (mirrors `D:\IOT-Practical\doc\_main_12.qd`).
 */
class RealFileReferenceTest {

    private val sampleText: String =
        File(System.getProperty("user.dir"), "src/test/resources/reference-sample.qd").readText()

    @Test
    fun `findVarDeclarations detects version`() {
        val vars = QuarkdownCallParser.findVarDeclarations(sampleText)
        assertTrue("expected 'version' in $vars", vars.containsKey("version"))
    }

    @Test
    fun `var usage anchors point at version usages`() {
        val anchors = QuarkdownReferenceParser.computeAnchors(sampleText)
        val varAnchors = anchors.filter { it.referenceType == "var" }
        assertTrue("expected >=1 var anchor, got ${varAnchors.size}", varAnchors.size >= 1)
        assertEquals("version", varAnchors.first().referenceText)
        // each var anchor should point at a `.version` usage after the declaration
        for (a in varAnchors) {
            val at = sampleText.substring(a.start, a.end)
            assertEquals("version", at)
        }
    }

    @Test
    fun `version usage text is present`() {
        assertTrue(sampleText.contains(".include {.version/03.02-hardware.qd}"))
    }
}

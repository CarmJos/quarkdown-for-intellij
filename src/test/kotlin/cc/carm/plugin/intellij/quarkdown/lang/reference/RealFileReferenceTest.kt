package cc.carm.plugin.intellij.quarkdown.lang.reference

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Verifies the var/ref anchor computation against the bundled sample `.qd` document
 * (`src/test/resources/reference-sample.qd`).
 */
class RealFileReferenceTest {

    private val sampleText: String =
        File(System.getProperty("user.dir"), "src/test/resources/reference-sample.qd").readText()

    @Test
    fun `ref usage anchors point at every ref id`() {
        val anchors = QuarkdownReferenceParser.computeAnchors(sampleText)
        val refAnchors = anchors.filter { it.referenceType == "ref" }
        // The sample declares three .ref usages: {first} {first} {table}.
        assertEquals("first", refAnchors.first().referenceText)
        assertTrue("expected >=3 ref anchors, got ${refAnchors.size}", refAnchors.size >= 3)
        // Each ref anchor should point at the id text inside a `.ref { ... }`.
        for (a in refAnchors) {
            val at = sampleText.substring(a.start, a.end)
            assertEquals(a.referenceText, at)
        }
    }

    @Test
    fun `label anchors point at declared ids`() {
        val anchors = QuarkdownReferenceParser.computeAnchors(sampleText)
        val labelAnchors = anchors.filter { it.referenceType == "label" }
        assertTrue("expected >=1 label anchor, got ${labelAnchors.size}", labelAnchors.size >= 1)
        for (a in labelAnchors) {
            val at = sampleText.substring(a.start, a.end)
            assertEquals(a.referenceText, at)
        }
    }

    @Test
    fun `ref usage text is present`() {
        assertTrue(sampleText.contains(".ref {first}"))
    }
}

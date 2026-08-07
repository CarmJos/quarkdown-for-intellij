package cc.carm.plugin.intellij.quarkdown.lang.reference

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuarkdownReferenceParserTest {

    @Test
    fun `detects var usage anchors`() {
        val text = ".var {version} {version-12}\n.include {.version/03.02-hardware.qd}"
        val anchors = QuarkdownReferenceParser.computeAnchors(text)
        val varAnchors = anchors.filter { it.referenceType == "var" }
        assertEquals(1, varAnchors.size)
        assertEquals("version", varAnchors[0].referenceText)
        // anchor should point at the `.version` usage (not the declaration)
        assertTrue(text.substring(varAnchors[0].start, varAnchors[0].end) == "version")
        assertTrue(varAnchors[0].start > text.indexOf(".var"))
    }

    @Test
    fun `detects ref id anchors`() {
        val text = "See .ref {chapter-1} for details."
        val anchors = QuarkdownReferenceParser.computeAnchors(text)
        val refAnchors = anchors.filter { it.referenceType == "ref" }
        assertEquals(1, refAnchors.size)
        assertEquals("chapter-1", refAnchors[0].referenceText)
    }

    @Test
    fun `detects label declaration anchors`() {
        val text = "# Heading {#chapter-1}\n\nSee .ref {chapter-1}"
        val anchors = QuarkdownReferenceParser.computeAnchors(text)
        val labelAnchors = anchors.filter { it.referenceType == "label" }
        assertEquals(1, labelAnchors.size)
        assertEquals("chapter-1", labelAnchors[0].referenceText)
        // the label anchor points at the declaration position
        assertTrue(text.substring(labelAnchors[0].start, labelAnchors[0].end) == "chapter-1")
        assertTrue(labelAnchors[0].start < text.indexOf(".ref"))
    }

    @Test
    fun `standalone label declaration is detected`() {
        val text = "{#mylabel}\n\nSee .ref {mylabel}"
        val anchors = QuarkdownReferenceParser.computeAnchors(text)
        assertEquals(1, anchors.count { it.referenceType == "label" })
        assertEquals(1, anchors.count { it.referenceType == "ref" })
    }

    @Test
    fun `detects include path anchors`() {
        val text = ".include {\"docs/intro.qd\"}"
        val anchors = QuarkdownReferenceParser.computeAnchors(text)
        val fileAnchors = anchors.filter { it.referenceType == "include" }
        assertEquals(1, fileAnchors.size)
        assertEquals("docs/intro.qd", fileAnchors[0].referenceText)
    }

    @Test
    fun `detects include path anchors with unquoted path`() {
        val text = ".include {docs/intro.qd}"
        val anchors = QuarkdownReferenceParser.computeAnchors(text)
        val fileAnchors = anchors.filter { it.referenceType == "include" }
        assertEquals(1, fileAnchors.size)
        assertEquals("docs/intro.qd", fileAnchors[0].referenceText)
    }

    @Test
    fun `detects read path anchors with unquoted path`() {
        val text = ".read {data/file.qd}"
        val anchors = QuarkdownReferenceParser.computeAnchors(text)
        val fileAnchors = anchors.filter { it.referenceType == "read" }
        assertEquals(1, fileAnchors.size)
        assertEquals("data/file.qd", fileAnchors[0].referenceText)
    }

    @Test
    fun `detects image path segments`() {
        val text = "!(50%)[logo](assets/logo.png)"
        val anchors = QuarkdownReferenceParser.computeAnchors(text)
        val imageAnchors = anchors.filter { it.referenceType == "image" }
        assertEquals(1, imageAnchors.size)
        assertEquals("assets/logo.png", imageAnchors[0].referenceText)
    }

    @Test
    fun `no var anchor for undeclared names`() {
        val text = ".include {.version/03.02-hardware.qd}"
        val anchors = QuarkdownReferenceParser.computeAnchors(text)
        assertEquals(0, anchors.count { it.referenceType == "var" })
    }

    @Test
    fun `overlap check works for click inside anchor`() {
        val text = "See .ref {chapter-1} for details."
        val anchor = QuarkdownReferenceParser.computeAnchors(text).first { it.referenceType == "ref" }
        // clicking in the middle of "chapter-1"
        assertTrue(anchor.overlaps(anchor.start + 4, anchor.start + 6))
        // clicking outside the anchor (before/after) does not overlap
        assertTrue(!anchor.overlaps(0, anchor.start))
        assertTrue(!anchor.overlaps(anchor.end, text.length))
    }
}

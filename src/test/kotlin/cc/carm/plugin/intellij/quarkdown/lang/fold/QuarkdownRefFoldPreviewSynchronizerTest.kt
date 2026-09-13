package cc.carm.plugin.intellij.quarkdown.lang.fold

import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage

/**
 * Verifies [QuarkdownRefFoldPreviewSynchronizer] keeps the icon-chip inlay of a collapsed
 * `.ref` fold in sync with the folding model (added while collapsed, removed while expanded).
 */
class QuarkdownRefFoldPreviewSynchronizerTest : BasePlatformTestCase() {

    private val documentText =
        "As shown in .ref {data}.\n\n| A | B |\n|---|---|\n| 1 | 2 |\n\"Beverage preferences\" {#data}\n"

    private fun refRange(): IntRange {
        val start = myFixture.editor.document.text.indexOf(".ref {data}")
        return start until start + ".ref {data}".length
    }

    private fun chips() = myFixture.editor.inlayModel.getInlineElementsInRange(
        0, myFixture.editor.document.textLength, QuarkdownRefFoldChipRenderer::class.java
    )

    private fun addRefFold(placeholder: String): FoldRegion {
        val range = refRange()
        var region: FoldRegion? = null
        myFixture.editor.foldingModel.runBatchFoldingOperation {
            region = myFixture.editor.foldingModel.addFoldRegion(range.first, range.last + 1, placeholder)
            region!!.isExpanded = false
        }
        return region!!
    }

    private fun installed(): QuarkdownRefFoldPreviewSynchronizer {
        val synchronizer = QuarkdownRefFoldPreviewSynchronizer.install(myFixture.editor)!!
        synchronizer.sync()
        return synchronizer
    }

    fun `test collapsed ref fold shows an icon chip inlay`() {
        myFixture.configureByText("test.qd", documentText)
        addRefFold(RESOLVED_REF_PLACEHOLDER)
        installed()

        val inlays = chips()
        assertEquals("collapsed ref fold should get exactly one chip", 1, inlays.size)
        assertEquals(refRange().first, inlays[0].offset)
        assertTrue("chip should carry the table caption", inlays[0].renderer.signature.contains("Beverage preferences"))
        // The chip must be laid out on the reference's own line (not swallowed by the folded region).
        val referenceLine = myFixture.editor.offsetToVisualPosition(refRange().first).line
        assertEquals(referenceLine, inlays[0].visualPosition.line)
    }

    fun `test expanding the fold removes the chip and collapsing restores it`() {
        myFixture.configureByText("test.qd", documentText)
        val region = addRefFold(RESOLVED_REF_PLACEHOLDER)
        installed()
        assertEquals(1, chips().size)

        setExpanded(region, true)
        assertEquals("expanded fold should not show a chip", 0, chips().size)

        setExpanded(region, false)
        assertEquals("re-collapsed fold should show the chip again", 1, chips().size)
    }

    private fun setExpanded(region: FoldRegion, expanded: Boolean) {
        myFixture.editor.foldingModel.runBatchFoldingOperation { region.isExpanded = expanded }
    }

    fun `test folds with a text placeholder get no chip`() {
        myFixture.configureByText("test.qd", documentText)
        addRefFold("Reference(data)")
        installed()
        assertEquals("text-placeholder folds keep their plain placeholder", 0, chips().size)
    }

    fun `test chip renderer reports a size and paints without errors`() {
        myFixture.configureByText("test.qd", documentText)
        addRefFold(RESOLVED_REF_PLACEHOLDER)
        installed()

        val inlay = chips().single()
        val renderer = inlay.renderer
        val width = renderer.calcWidthInPixels(inlay)
        val height = renderer.calcHeightInPixels(inlay)
        assertTrue("chip must have a non-empty width", width > 0)
        assertTrue("chip must be one line tall", height > 0)

        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        renderer.paint(
            inlay, graphics,
            Rectangle2D.Double(0.0, 0.0, width.toDouble(), height.toDouble()),
            TextAttributes()
        )
        graphics.dispose()
    }

    private companion object {
        /**
         * A *resolved* `.ref` fold carries no text placeholder: the icon chip inlay supplies
         * the visible preview (icon + caption), so the fold region itself must stay empty.
         */
        const val RESOLVED_REF_PLACEHOLDER = ""
    }
}
